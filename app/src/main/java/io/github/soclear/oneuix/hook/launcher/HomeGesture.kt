package io.github.soclear.oneuix.hook.launcher

import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClassIfExists
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.hook.util.InteractionHookLog
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

object HomeGesture {
    private const val HOME_VIEW =
        "com.honeyspace.ui.honeypots.homescreen.presentation.HomeView"
    private const val SEARCH_LAUNCH_FROM = "com.honeyspace.common.search.SearchLaunchFrom"
    private val states = WeakHashMap<View, GestureState>()
    private val finderGateHooked = AtomicBoolean(false)
    private val statusBarGateHooked = AtomicBoolean(false)

    fun enable(
        lpparam: LoadPackageParam,
        enableMiddleSwipeDown: Boolean,
        keepOriginalSwipeUpSearch: Boolean,
        requestedThresholdDp: Float,
    ) {
        try {
            InteractionHookLog.environment("HomeGesture", lpparam)
            val homeViewClass = findClassIfExists(HOME_VIEW, lpparam.classLoader) ?: run {
                InteractionHookLog.info("HomeGesture", "HomeView not found; launcher behavior retained")
                return
            }
            val launchFromClass = findClassIfExists(SEARCH_LAUNCH_FROM, lpparam.classLoader) ?: run {
                InteractionHookLog.info("HomeGesture", "SearchLaunchFrom not found; launcher behavior retained")
                return
            }
            // SEARCH_FROM_GESTURE is only metadata in this One UI 8.5 build: passing it to
            // startSearch() returns without navigating. SEARCH_FROM_KEY uses the same native
            // search controller and standard Finder screen, and performs the required transition.
            val nativeLaunchSource = launchFromClass.enumConstants?.firstOrNull {
                (it as? Enum<*>)?.name == "SEARCH_FROM_KEY"
            } ?: run {
                InteractionHookLog.info("HomeGesture", "SEARCH_FROM_KEY not found; launcher behavior retained")
                return
            }
            val thresholdDp = requestedThresholdDp.coerceIn(64f, 160f)

            findAndHookMethod(
                homeViewClass,
                "dispatchTouchEvent",
                MotionEvent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as? View ?: return
                        val event = param.args[0] as? MotionEvent ?: return
                        try {
                            if (!keepOriginalSwipeUpSearch) {
                                disableNativeFinderGate(view)
                            }
                            if (!enableMiddleSwipeDown) return
                            disableLauncherStatusBarGate(view)
                            handleGesture(view, event, thresholdDp, nativeLaunchSource)?.let {
                                param.result = it
                            }
                        } catch (error: Throwable) {
                            synchronized(states) { states.remove(view) }
                            InteractionHookLog.failure("HomeGesture", error)
                        }
                    }
                }
            )
            InteractionHookLog.info(
                "HomeGesture",
                "HomeView hook installed middleDown=$enableMiddleSwipeDown keepSwipeUp=$keepOriginalSwipeUpSearch threshold=${thresholdDp}dp"
            )
        } catch (error: Throwable) {
            InteractionHookLog.failure("HomeGesture", error)
        }
    }

    private fun handleGesture(
        view: View,
        event: MotionEvent,
        thresholdDp: Float,
        nativeLaunchSource: Any,
    ): Boolean? {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val candidate = event.pointerCount == 1 &&
                    view.isShown && view.height > 0 &&
                    event.y > view.height * 0.25f &&
                    isSafeHomeState(view) &&
                    isVacantCell(view, event)
                synchronized(states) {
                    states[view] = GestureState(event.x, event.y, candidate)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> synchronized(states) {
                states[view]?.candidate = false
            }

            MotionEvent.ACTION_MOVE -> {
                val state = synchronized(states) { states[view] } ?: return null
                if (state.consuming) return true
                if (!state.candidate || event.pointerCount != 1 || !isSafeHomeState(view)) {
                    state.candidate = false
                    return null
                }
                val deltaX = event.x - state.downX
                val deltaY = event.y - state.downY
                val thresholdPx = thresholdDp * view.resources.displayMetrics.density
                if (deltaY >= thresholdPx && abs(deltaY) > abs(deltaX) * 1.2f) {
                    val controller = callMethod(view, "getSearchScreenController")
                    callMethod(controller, "startSearch", nativeLaunchSource, true)
                    focusFinderInput(view)
                    state.consuming = true
                    InteractionHookLog.info(
                        "HomeGesture",
                        "middle swipe recognized deltaY=${deltaY.toInt()}px -> native Finder via SEARCH_FROM_KEY"
                    )
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val state = synchronized(states) { states.remove(view) }
                if (state?.consuming == true) return true
            }
        }
        return null
    }

    private fun focusFinderInput(view: View, attempt: Int = 0) {
        val delays = longArrayOf(120L, 220L, 400L)
        if (attempt !in delays.indices) return
        view.postDelayed({
            try {
                val inputId = view.resources.getIdentifier(
                    "search_src_text",
                    "id",
                    "com.sec.android.app.launcher",
                )
                val input = if (inputId != 0) {
                    view.rootView.findViewById<View>(inputId) as? EditText
                } else {
                    null
                }
                if (input == null || !input.isShown) {
                    focusFinderInput(view, attempt + 1)
                    return@postDelayed
                }
                input.isFocusableInTouchMode = true
                input.requestFocus()
                input.setSelection(input.text?.length ?: 0)
                val inputMethodManager =
                    view.context.getSystemService(InputMethodManager::class.java)
                val requested = inputMethodManager.showSoftInput(
                    input,
                    0,
                )
                if (requested || attempt == delays.lastIndex) {
                    InteractionHookLog.info(
                        "HomeGesture",
                        "Finder input focused keyboardRequested=$requested attempt=${attempt + 1}",
                    )
                } else {
                    focusFinderInput(view, attempt + 1)
                }
            } catch (error: Throwable) {
                if (attempt == delays.lastIndex) {
                    InteractionHookLog.failure("HomeGestureKeyboard", error)
                } else {
                    focusFinderInput(view, attempt + 1)
                }
            }
        }, delays[attempt])
    }

    private fun isSafeHomeState(view: View): Boolean = try {
        if ((callMethod(view, "isDragAnimRunning") as? Boolean) == true) return false
        val quickOption = callMethod(view, "getQuickOptionController")
        if ((callMethod(quickOption, "isDragging") as? Boolean) == true) return false
        if ((callMethod(quickOption, "isShowQuickOption") as? Boolean) == true) return false
        val honeyScreen = getObjectField(view, "h") ?: return false
        val current = callMethod(honeyScreen, "getCurrentHoneyState")
        val changing = callMethod(honeyScreen, "getCurrentChangeState")
        current?.javaClass?.name?.endsWith("HomeScreen\$Normal") == true &&
            changing?.javaClass?.name?.endsWith("HomeScreen\$Normal") == true
    } catch (_: Throwable) {
        false
    }

    private fun isVacantCell(view: View, event: MotionEvent): Boolean = try {
        val callback = getObjectField(view, "i")
        (callMethod(callback, "invoke", PointF(event.rawX, event.rawY)) as? Boolean) == true
    } catch (_: Throwable) {
        false
    }

    private fun disableNativeFinderGate(view: View) {
        disableControllerGate(
            view = view,
            controllerName = "FinderTouchController",
            hooked = finderGateHooked,
            successMessage = "original Finder swipe-up gate disabled",
            failureMessage = "FinderTouchController gate ambiguous; swipe-up retained",
        )
    }

    private fun disableLauncherStatusBarGate(view: View) {
        disableControllerGate(
            view = view,
            controllerName = "StatusBarTouchController",
            hooked = statusBarGateHooked,
            successMessage =
                "launcher anywhere-swipe notification gate disabled; top status bar retained by SystemUI",
            failureMessage =
                "StatusBarTouchController gate ambiguous; launcher notification gesture retained",
        )
    }

    private fun disableControllerGate(
        view: View,
        controllerName: String,
        hooked: AtomicBoolean,
        successMessage: String,
        failureMessage: String,
    ) {
        if (hooked.get()) return
        val controllers = getObjectField(view, "e") as? Iterable<*> ?: return
        val controller = controllers.firstOrNull { candidate ->
            runCatching { callMethod(candidate, "getName") as? String }.getOrNull() ==
                controllerName
        } ?: return
        val gate = controller.javaClass.declaredMethods.singleOrNull {
            it.name == "a" && it.parameterCount == 0 && it.returnType == Boolean::class.javaPrimitiveType
        } ?: run {
            InteractionHookLog.info("HomeGesture", failureMessage)
            return
        }
        if (hooked.compareAndSet(false, true)) {
            XposedBridge.hookMethod(gate, XC_MethodReplacement.returnConstant(false))
            InteractionHookLog.info("HomeGesture", successMessage)
        }
    }

    private data class GestureState(
        val downX: Float,
        val downY: Float,
        var candidate: Boolean,
        var consuming: Boolean = false,
    )
}
