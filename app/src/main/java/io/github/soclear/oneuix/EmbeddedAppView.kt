package io.github.soclear.oneuix

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.TextView
import java.util.UUID

/** Real app output/input; IME belongs to the virtual display, not this host. */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class EmbeddedAppView(context: Context, private val onExit: () -> Unit, private val onFullscreen: (Boolean) -> Unit) :
    FrameLayout(context), SurfaceHolder.Callback {
    private val surfaceView = SurfaceView(context)
    private val refreshRate = context.getSystemService(android.hardware.display.DisplayManager::class.java)
        .getDisplay(0).supportedModes.maxOf { it.refreshRate }
    private val status = TextView(context).apply {
        gravity = Gravity.CENTER; setTextColor(Color.LTGRAY); setBackgroundColor(Color.BLACK)
        setPadding(32, 32, 32, 32); setText(R.string.mediaPage_summary)
    }
    // Reuse pointer storage; preserve batched historical samples for scrolling/fling velocity.
    private val pointers = Array(32) { MotionEvent.PointerProperties() }
    private val coordinates = Array(32) { MotionEvent.PointerCoords() }
    private var component: ComponentName? = null
    private var target = ""
    private var surface: Surface? = null
    private var server: IBinder? = null
    private var process: Process? = null
    private var nonce = ""
    private var active = false

    init {
        addView(surfaceView, LayoutParams(-1, -1)); addView(status, LayoutParams(-1, -1))
        surfaceView.holder.addCallback(this)
        surfaceView.setOnTouchListener { _, event ->
            if (status.visibility == VISIBLE) return@setOnTouchListener true
            val history = event.historySize
            for (i in 0 until event.pointerCount) {
                event.getPointerProperties(i, pointers[i])
                if (history == 0) event.getPointerCoords(i, coordinates[i])
                else event.getHistoricalPointerCoords(i, 0, coordinates[i])
            }
            // A fresh event uses local coordinates as raw coordinates on the other display.
            val copy = MotionEvent.obtain(event.downTime, if (history == 0) event.eventTime else event.getHistoricalEventTime(0),
                event.action, event.pointerCount, pointers, coordinates, event.metaState, event.buttonState,
                event.xPrecision, event.yPrecision, event.deviceId, event.edgeFlags, event.source, event.flags)
            for (sample in 1..history) {
                for (i in 0 until event.pointerCount) {
                    if (sample == history) event.getPointerCoords(i, coordinates[i])
                    else event.getHistoricalPointerCoords(i, sample, coordinates[i])
                }
                copy.addBatch(if (sample == history) event.eventTime else event.getHistoricalEventTime(sample), coordinates, event.metaState)
            }
            send(3) { writeTypedObject(copy, 0) }
            copy.recycle()
            true
        }
    }

    fun setApp(packageName: String) {
        if (target == packageName) return
        closeSession()
        target = packageName
        component = context.packageManager.getLaunchIntentForPackage(packageName)?.component
        status.visibility = VISIBLE
        status.setText(if (packageName.isEmpty()) R.string.mediaPage_summary else R.string.mediaPage_starting)
        if (active) start()
    }

    fun setActive(value: Boolean) {
        active = value
        if (value) start() else updateSurface(null)
    }

    fun connect(intent: Intent): Boolean {
        val request = nonce
        if (request.isEmpty() || intent.getStringExtra("nonce") != request || server != null) return false
        val remote = intent.extras?.getBinder("server") ?: return false
        server = remote
        remote.linkToDeath({ post { if (server === remote) { server = null; showError("Root helper disconnected") } } }, 0)
        send(1) {
            writeStrongBinder(object : Binder() {
                override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                    if (getCallingUid() != 0) throw SecurityException("Only the root helper may send events")
                    data.enforceInterface(PROTOCOL)
                    val message = data.readString()
                    post {
                        if (nonce == request) when (code) {
                            1 -> { status.visibility = GONE; if (active) onFullscreen(message?.toIntOrNull()?.rem(2) == 1) }
                            2 -> showError(message.orEmpty())
                            3 -> onExit()
                        }
                    }
                    return true
                }
            })
        }
        if (active) updateSurface(surface)
        return true
    }

    private val backCallback = android.window.OnBackInvokedCallback { back() }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback)
    }
    override fun onDetachedFromWindow() {
        findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(backCallback)
        super.onDetachedFromWindow()
    }
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode != android.view.KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        if (event.action == android.view.KeyEvent.ACTION_UP) back()
        return true
    }
    private fun back() {
        if (status.visibility == VISIBLE) onExit() else send(4) {}
    }

    private fun start() {
        if (surface == null || target.isEmpty()) return
        if (component == null) { showError(context.getString(R.string.mediaPage_unavailable)); return }
        if (server != null) { updateSurface(surface); return }
        if (nonce.isNotEmpty()) return
        status.visibility = VISIBLE; status.setText(R.string.mediaPage_starting)
        val request = UUID.randomUUID().toString().also { nonce = it }
        val apk = context.applicationInfo.sourceDir.replace("'", "'\\''")
        val command = "CLASSPATH='$apk' exec /system/bin/app_process /system/bin " +
            "io.github.soclear.oneuix.EmbeddedAppServer ${context.packageName} ${context.applicationInfo.uid} $request"
        Thread({
            try {
                val child = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
                post { if (nonce == request) process = child else child.destroy() }
                child.inputStream.bufferedReader().useLines { lines -> lines.forEach { Log.i(TAG, it) } }
                val exit = child.waitFor()
                post { if (nonce == request) { nonce = ""; server = null; showError(context.getString(R.string.mediaPage_rootFailed, exit)) } }
            } catch (error: Exception) {
                post { if (nonce == request) { nonce = ""; showError(error.toString()) } }
            }
        }, TAG).start()
    }

    private fun updateSurface(value: Surface?) {
        send(2) {
            writeTypedObject(value, 0)
            writeInt(surfaceView.width); writeInt(surfaceView.height); writeInt(resources.displayMetrics.densityDpi)
            writeFloat(refreshRate)
            writeTypedObject(component, 0)
        }
    }

    private fun send(code: Int, body: Parcel.() -> Unit) {
        val remote = server ?: return
        val parcel = Parcel.obtain()
        try {
            parcel.writeInterfaceToken(PROTOCOL); parcel.body()
            remote.transact(code, parcel, null, IBinder.FLAG_ONEWAY)
        } catch (error: Exception) { showError(error.toString()) }
        finally { parcel.recycle() }
    }

    private fun showError(message: String) {
        status.text = message; status.visibility = VISIBLE
        Log.e(TAG, message)
    }

    private fun closeSession() {
        nonce = ""
        val connected = server != null
        send(5) {}; server = null
        // Connected helpers clean up their tasks before exiting; don't race that with SIGTERM.
        if (!connected) process?.destroy()
        process = null
    }

    fun dispose() { active = false; closeSession() }
    override fun surfaceCreated(holder: SurfaceHolder) {
        surface = holder.surface
        holder.surface.setFrameRate(refreshRate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
        if (active) start()
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { if (active) updateSurface(holder.surface) }
    override fun surfaceDestroyed(holder: SurfaceHolder) { surface = null; updateSurface(null) }

    companion object {
        private const val PROTOCOL = "io.github.soclear.oneuix.EmbeddedApp"
        private const val TAG = "OneUIX.EmbeddedApp"
    }
}
