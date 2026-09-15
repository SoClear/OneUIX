package io.github.soclear.oneuix.hook.systemui

import android.annotation.SuppressLint
import android.util.Log
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object StatusBar102 {
    private const val TAG = "SystemUI.StatusBar"

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun setStatusBarClockFormat(format: String) {
        if (param.packageName != Package.SYSTEMUI) return
        val dateTimeFormatter = try {
            DateTimeFormatter.ofPattern(format)
        } catch (_: DateTimeException) {
            DateTimeFormatter.ofPattern("HH:mm")
        }
        setStatusBarClockText { dateTimeFormatter.format(LocalDateTime.now()) }
    }


    @SuppressLint("PrivateApi")
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun setStatusBarClockText(block: () -> String) {
        if (param.packageName != Package.SYSTEMUI) return
        try {
            val classLoader = param.classLoader
            val clockClass = classLoader.loadClass("com.android.systemui.statusbar.policy.QSClockIndicatorView")
            val qsClockBellSoundClass = classLoader.loadClass("com.android.systemui.statusbar.policy.QSClockBellSound")
            val method = clockClass.getDeclaredMethod("notifyTimeChanged", qsClockBellSoundClass)
            xposedModule.hook(method).intercept { chain ->
                val clockTextView = chain.thisObject as TextView
                val dateTime = block()
                clockTextView.text = dateTime
                clockTextView.contentDescription = dateTime
            }
        } catch (t: Throwable) {
            xposedModule.log(
                Log.ERROR,
                TAG,
                "setStatusBarClockText",
                t
            )
        }
    }
}