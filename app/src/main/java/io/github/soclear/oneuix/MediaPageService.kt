package io.github.soclear.oneuix

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.ui.dataStore
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Samsung overlay panel. App content owns all gestures; there is no module toolbar. */
class MediaPageService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val scope = MainScope()
    private val windows by lazy { getSystemService(WindowManager::class.java) }
    private var page: EmbeddedAppView? = null
    private var callback: IBinder? = null
    private var layout: WindowManager.LayoutParams? = null
    private var animation: ValueAnimator? = null
    private var position = 0f
    private var settings = Preference.Other()
    private val selectedApp get() = if (settings.embedAppInMediaPage) settings.mediaPagePackage else ""
    private var immersive = false
    private val death = IBinder.DeathRecipient { main.post { detach() } }
    private val width get() = windows.currentWindowMetrics.bounds.width().toFloat()

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            dataStore.data.collect { preference ->
                settings = preference.other
                page?.setApp(selectedApp)
                if (!settings.embedAppInMediaPage) settle(false)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "$packageName.EMBEDDED_ROOT_READY") {
            runCatching { page?.connect(intent) }.onFailure { Log.e(TAG, "Root connection failed", it) }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) { reply?.writeString(PROTOCOL); return true }
            if (packageManager.getPackagesForUid(getCallingUid())?.contains("com.sec.android.app.launcher") != true)
                throw SecurityException("Only One UI Home may attach a media page")
            data.enforceInterface(PROTOCOL)
            when (code) {
                1 -> {
                    val token = data.readStrongBinder(); val listener = data.readStrongBinder()
                    main.post { attach(token, listener) }
                    reply?.writeNoException()
                }
                2 -> {
                    data.readTypedObject(MotionEvent.CREATOR)?.recycle()
                    main.post { animation?.cancel(); page?.setActive(true); notify(2, 1) }
                }
                3 -> {
                    data.readTypedObject(MotionEvent.CREATOR)?.recycle()
                    val pixels = data.readInt()
                    main.post { move(pixels.toFloat()) }
                }
                4 -> {
                    val event = data.readTypedObject(MotionEvent.CREATOR)
                    val cancelled = event?.actionMasked == MotionEvent.ACTION_CANCEL
                    event?.recycle()
                    main.post { settle(!cancelled && position > width / 4) }
                }
                5 -> {
                    data.readTypedObject(Configuration.CREATOR)
                    main.post { if (position > 0) move(width) }
                }
                6 -> {
                    val state = data.readInt()
                    main.post { if (state >= 4) settle(false, false); if (state == 5) detach() }
                }
                7, 8 -> {
                    val mode = data.readInt()
                    main.post { settle(code == 7, mode != 2) }
                }
                13 -> main.post { detach() }
                else -> return super.onTransact(code, data, reply, flags)
            }
            return true
        }
    }

    private fun attach(token: IBinder?, listener: IBinder?) {
        detach()
        if (token == null || listener == null) return
        callback = listener
        try {
            listener.linkToDeath(death, 0)
            page = EmbeddedAppView(this, { settle(false) }, ::fullscreen).apply {
                setApp(selectedApp)
                visibility = View.INVISIBLE
            }
            layout = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.OPAQUE).apply {
                this.token = token
                gravity = Gravity.TOP or Gravity.START
                title = "OneUIX media page"
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            windows.addView(page, layout)
            notify(2, 0)
        } catch (error: Exception) { Log.e(TAG, "Cannot attach media page", error); detach() }
    }

    private fun fullscreen(value: Boolean) {
        if (immersive == value) return
        immersive = value
        notify(3, if (value) 1 else 0)
        val params = layout ?: return
        params.flags = if (value) params.flags or WindowManager.LayoutParams.FLAG_FULLSCREEN
            else params.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN.inv()
        params.setFitInsetsTypes(if (value) 0 else WindowInsets.Type.systemBars())
        windows.updateViewLayout(page, params)
        page?.windowInsetsController?.apply {
            systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (value) hide(WindowInsets.Type.systemBars()) else show(WindowInsets.Type.systemBars())
        }
    }

    private fun notify(code: Int, value: Int) {
        val parcel = Parcel.obtain()
        try {
            parcel.writeInterfaceToken(CALLBACK); parcel.writeInt(value)
            callback?.transact(code, parcel, null, IBinder.FLAG_ONEWAY)
        } catch (_: android.os.RemoteException) { main.post { detach() } }
        finally { parcel.recycle() }
    }

    private fun move(pixels: Float) {
        position = pixels.coerceIn(0f, width)
        page?.apply { visibility = if (position == 0f) View.INVISIBLE else View.VISIBLE; translationX = position - width }
        notify(1, position.toInt())
    }

    private fun settle(open: Boolean, animate: Boolean = true) {
        animation?.cancel()
        val params = layout ?: return
        notify(2, if (open) 1 else 2)
        fun finish() {
            move(if (open) width else 0f)
            val inactive = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            params.flags = if (open) params.flags and inactive.inv() else params.flags or inactive
            windows.updateViewLayout(page, params)
            page?.setActive(open)
            if (!open) fullscreen(false)
            notify(2, if (open) 3 else 0)
        }
        if (!animate) { finish(); return }
        animation = ValueAnimator.ofFloat(position, if (open) width else 0f).apply {
            duration = 200
            addUpdateListener { move(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) { if (!cancelled) finish() }
            })
            start()
        }
    }

    private fun detach() {
        animation?.cancel(); animation = null
        notify(1, 0); notify(2, 0)
        runCatching { callback?.unlinkToDeath(death, 0) }
        callback = null
        page?.dispose()
        page?.let { if (it.isAttachedToWindow) windows.removeViewImmediate(it) }
        page = null; layout = null; position = 0f; immersive = false
    }

    override fun onUnbind(intent: Intent): Boolean { detach(); return false }
    override fun onDestroy() { detach(); scope.cancel(); super.onDestroy() }

    companion object {
        private const val TAG = "OneUIX.MediaPage"
        private const val PROTOCOL = "com.samsung.android.app.spage.service.IPageOverlay"
        private const val CALLBACK = "com.samsung.android.app.spage.service.IPageOverlayCallback"
    }
}
