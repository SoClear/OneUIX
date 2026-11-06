package io.github.soclear.oneuix.hook

import io.github.libxposed.api.XposedModule
import io.github.soclear.oneuix.hook.util.afterAttachTry
import io.github.soclear.oneuix.hook.util.longVersionCode

object MyFiles {
    private const val KEY_LATEST_VERSION = "myfiles_latest_version"
    private const val KEY_UPDATE_AVAILABLE = "avail_app_update"

    /**
     * 国际版"我的文件"在国行系统上点击"网络存储"时，会因为缓存的最新版本与已安装版本不一致
     * 而跳转到 Galaxy Store 更新页。这里让这两个偏好读出来始终表示"没有可用更新"。
     */
    context(xposedModule: XposedModule)
    fun fixNetworkStorageUpdateCheck() = afterAttachTry {
        val installedVersion = longVersionCode.toInt()
        val prefsClass = classLoader.loadClass("android.app.SharedPreferencesImpl")

        xposedModule.hook(
            prefsClass.getDeclaredMethod("getInt", String::class.java, Int::class.javaPrimitiveType)
        ).intercept { chain ->
            if (chain.args[0] == KEY_LATEST_VERSION) installedVersion else chain.proceed()
        }

        xposedModule.hook(
            prefsClass.getDeclaredMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
        ).intercept { chain ->
            if (chain.args[0] == KEY_UPDATE_AVAILABLE) false else chain.proceed()
        }
    }
}
