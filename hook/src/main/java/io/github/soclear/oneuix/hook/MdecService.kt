package io.github.soclear.oneuix.hook

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.xlog

object MdecService {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun supportCallAndTextOnOtherDevices() {
        if (param.packageName != Package.MDEC_SERVICE) return
        try {
            val clazz = param.classLoader.loadClass("com.samsung.android.mdeccommon.utils.SimUtils")
            val method = clazz.getDeclaredMethod("isChinaSIMActive", Context::class.java)
            xposedModule.hook(method).intercept { false }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
