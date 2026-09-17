package io.github.soclear.oneuix.hook

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.icu.text.Collator
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.getSystemContext
import io.github.soclear.oneuix.hook.util.xlog

object DualApp {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun makeAllUserAppsAvailable() {
        if (param.packageName != Package.DUAL_APP) return
        val packageManager = getSystemContext().packageManager
        // 创建比较器，使用Collator进行本地化排序
        val collator = Collator.getInstance()

        @SuppressLint("QueryPermissionsNeeded")
        val dualAppPackages = packageManager
            // 获取已安装应用
            .getInstalledApplications(PackageManager.GET_META_DATA)
            // 使用位运算排除系统应用
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            // 包名对应应用名称
            .map { it.packageName to it.loadLabel(packageManager) }
            // 按应用名称排序
            .sortedWith { a, b -> collator.compare(a.second, b.second) }
            // 转包名列表
            .map { it.first }

        try {
            val clazz = runCatching {
                param.classLoader.loadClass("com.samsung.android.da.daagent.fwwrapper.PmWrapper")
            }.getOrNull() ?: runCatching {
                param.classLoader.loadClass("com.samsung.android.da.daagent.activity.DualAppActivity")
            }.getOrNull() ?: return

            clazz.declaredMethods.filter {
                it.name == "getPossibleDualAppPackages"
            }.forEach {
                xposedModule.hook(it).intercept { dualAppPackages }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
