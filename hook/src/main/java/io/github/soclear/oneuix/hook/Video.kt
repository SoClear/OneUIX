package io.github.soclear.oneuix.hook

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.getIntField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setIntField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import kotlinx.serialization.Serializable
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.getHookConfig
import io.github.soclear.oneuix.hook.util.longVersionCode
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Modifier

object Video {
    private const val PLAY_SPEED_POPUP = "${Package.VIDEO}.player.popup.PlaySpeedPopup"
    private const val SPEED_3X: Int = 12
    private const val SPEED_4X: Int = 16
    private const val MESSAGE_DELAY: Long = 3000L
    private const val MESSAGE_CODE: Int = 1

    private class SpeedButton(
        context: Context,
        private val baseText: String,
        val speedValue: Int
    ) {
        val view: TextView = TextView(context)

        init {
            setupView()
        }

        private fun setupView() {
            view.setTextColor(Color.WHITE)
            view.gravity = Gravity.CENTER
            updateText(false)
        }

        fun updateText(selected: Boolean) {
            val text = baseText + (if (selected) "x" else "")
            view.text = text
        }

        fun setSelected(selected: Boolean, background: Drawable?) {
            updateText(selected)
            view.background = if (selected) background else null
        }
    }


    private var bgSelected: Drawable? = null
    private var playSpeedSetter: Any? = null
    private var playSpeedStore: Any? = null

    fun showMorePlaybackSpeeds(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.VIDEO) {
            return
        }
        afterAttach {
            val hookConfig = getHookConfig { getHookConfigFromDexKit() }
            if (hookConfig != null) {
                try {
                    hook(hookConfig)
                } catch (t: Throwable) {
                    XposedBridge.log(t)
                }
            }
        }
    }

    private fun Context.hook(hookConfig: VideoHookConfig) {
        var speed3Button: SpeedButton? = null
        var speed4Button: SpeedButton? = null

        findAndHookMethod(
            PLAY_SPEED_POPUP,
            classLoader,
            "inflateView",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val playSpeedLayout = getObjectField(
                        param.thisObject,
                        "mAnimationLayout"
                    ) as ViewGroup
                    val speedButtonLayout = playSpeedLayout.getChildAt(1) as LinearLayout
                    speedButtonLayout.setPadding(0, 0, 0, 0)
                    initializeSpeedControls(
                        classLoader,
                        hookConfig.playbackSvcUtilField,
                        hookConfig.playerInfoField
                    )
                    initializeBackground(playSpeedLayout.context)

                    speed3Button = SpeedButton(playSpeedLayout.context, "3.0", SPEED_3X)
                    speed4Button = SpeedButton(playSpeedLayout.context, "4.0", SPEED_4X)

                    setupButtonClickListener(
                        speed3Button,
                        speed4Button,
                        param.thisObject,
                        hookConfig.playSpeedField,
                        hookConfig.setPlaySpeedMethod,
                    )
                    setupButtonClickListener(
                        speed4Button,
                        speed3Button,
                        param.thisObject,
                        hookConfig.playSpeedField,
                        hookConfig.setPlaySpeedMethod,
                    )

                    val currentSpeed = getIntField(playSpeedStore, "t")
                    updateButtonStates(currentSpeed, speed3Button, speed4Button)

                    speedButtonLayout.addView(speed3Button.view)
                    speedButtonLayout.addView(speed4Button.view)

                }
            }
        )

        findAndHookMethod(
            PLAY_SPEED_POPUP,
            classLoader,
            "onClick",
            View::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam?) {
                    speed3Button?.setSelected(false, null)
                    speed4Button?.setSelected(false, null)
                }
            }
        )
    }

    private fun initializeSpeedControls(
        classLoader: ClassLoader,
        playbackSvcUtilField: String,
        playerInfoField: String
    ) {
        if (playSpeedSetter == null) {
            playSpeedSetter = DexField(playbackSvcUtilField).getFieldInstance(classLoader).get(null)
        }
        if (playSpeedStore == null) {
            playSpeedStore = DexField(playerInfoField).getFieldInstance(classLoader).get(null)
        }
    }

    private fun initializeBackground(context: Context) {
        if (bgSelected == null) {
            @SuppressLint("DiscouragedApi")
            val bgSelectedId = context.resources.getIdentifier(
                "play_speed_select",
                "drawable",
                Package.VIDEO
            )
            bgSelected = context.getDrawable(bgSelectedId)
        }
    }

    private fun setupButtonClickListener(
        activeButton: SpeedButton?,
        inactiveButton: SpeedButton?,
        popupObject: Any,
        playSpeedField: String,
        setPlaySpeedMethod: String
    ) {
        activeButton?.view?.setOnClickListener {
            activeButton.setSelected(true, bgSelected)
            inactiveButton?.setSelected(false, null)

            // 更新存储的播放速度
            setIntField(playSpeedStore, DexField(playSpeedField).name, activeButton.speedValue)
            // 设置实际的播放速度
            callMethod(playSpeedSetter, DexMethod(setPlaySpeedMethod).name, activeButton.speedValue)
            callMethod(popupObject, "updateCurrentPlaySpeed", activeButton.speedValue)
            callMethod(popupObject, "removeMessage", MESSAGE_CODE)
            callMethod(popupObject, "sendMessage", MESSAGE_CODE, MESSAGE_DELAY)
        }
    }

    private fun updateButtonStates(
        currentSpeed: Int,
        speed3Button: SpeedButton?,
        speed4Button: SpeedButton?
    ) {
        speed3Button?.setSelected(currentSpeed == SPEED_3X, bgSelected)
        speed4Button?.setSelected(currentSpeed == SPEED_4X, bgSelected)
    }

    @Serializable
    data class VideoHookConfig(
        override val versionCode: Long,
        val playbackSvcUtilField: String,
        val setPlaySpeedMethod: String,
        val playerInfoField: String,
        val playSpeedField: String,
    ) : HookConfig

    private fun Context.getHookConfigFromDexKit(): VideoHookConfig? {
        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            val excludes = listOf("android", "androidx", "com", "kotlin", "kotlinx", "srib")
            val playbackSvcUtilField = bridge.findClass {
                excludePackages(excludes)
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.ABSTRACT
                    superClass = "java.lang.Object"
                    fields {
                        count = 1
                        add {
                            modifiers = Modifier.PUBLIC or Modifier.STATIC or Modifier.FINAL
                            type {
                                modifiers = Modifier.PUBLIC or Modifier.FINAL
                                superClass = "java.lang.Object"
                                interfaceCount(1)
                                usingStrings("PlaybackSvcUtil")
                            }
                        }
                    }
                    methodCount(1)
                }
            }.singleOrNull()?.fields?.singleOrNull() ?: return null

            val setPlaySpeedMethod = playbackSvcUtilField.type.findMethod {
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    returnType = "void"
                    paramTypes("int")
                    usingStrings("setPlaySpeed() - service is not ready.")
                }
            }.singleOrNull() ?: return null

            val playerInfoClass = bridge.findClass {
                excludePackages(excludes)
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    superClass = "java.lang.Object"
                    usingStrings("set recommended PlaySpeed :  ")
                }
            }.singleOrNull() ?: return null

            val playerInfoField = playerInfoClass.findField {
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.STATIC or Modifier.FINAL
                    type = playerInfoClass.name
                }
            }.singleOrNull() ?: return null


            val resetPlaySpeedMethod = playerInfoClass.findMethod {
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    returnType = "void"
                    paramCount = 0
                    usingStrings("resetPlaySpeed E")
                }
            }.singleOrNull() ?: return null

            val playSpeedField =
                resetPlaySpeedMethod.usingFields.singleOrNull()?.field ?: return null

            return VideoHookConfig(
                versionCode = longVersionCode,
                playbackSvcUtilField = playbackSvcUtilField.toDexField().serialize(),
                setPlaySpeedMethod = setPlaySpeedMethod.toDexMethod().serialize(),
                playerInfoField = playerInfoField.toDexField().serialize(),
                playSpeedField = playSpeedField.toDexField().serialize(),
            )
        }
    }
}
