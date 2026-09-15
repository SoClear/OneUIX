package io.github.soclear.oneuix.hook

import android.util.Log
import android.widget.Toast
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.systemui.StatusBar102
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.xlog

class Main : XposedModule() {
    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
//        val processName = param.processName
//        xlog(processName)
    }
    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) = with(param) {

        when (param.packageName) {
            Package.SYSTEMUI -> {
                xlog("11")
                StatusBar102.setStatusBarClockFormat("HH:mm EEEEEd")
                xlog("22")
            }

            Package.GALLERY -> {
                xlog("1")
                afterAttach {
                    xlog("2")
                    Toast.makeText(this, "hel2o", Toast.LENGTH_SHORT).show()
                }
                xlog("3")
            }
        }
    }
}