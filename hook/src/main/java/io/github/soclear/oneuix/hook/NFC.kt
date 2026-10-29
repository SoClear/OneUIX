package io.github.soclear.oneuix.hook

import android.annotation.SuppressLint
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.hook.util.PreferenceProvider
import io.github.soclear.oneuix.hook.util.reflect

@SuppressLint("PrivateApi")
object NFC {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun filterNfcCards() {
        val classLoader = param.classLoader
        val handler = classLoader.loadClass($$"com.android.nfc.NfcService$NfcServiceHandler")
        val endpoint = classLoader.loadClass($$"com.android.nfc.DeviceHost$TagEndpoint")
        val readerMode = classLoader.loadClass($$"com.android.nfc.NfcService$ReaderModeParams")
        val method = handler.getDeclaredMethod("dispatchTagEndpoint", endpoint, readerMode)
        xposedModule.hook(method).intercept { chain ->
            // 保留前台读卡模式，允许在设置页读取被过滤卡片的 UID。
            if (chain.args[1] != null) return@intercept chain.proceed()
            val uid = (chain.args[0].reflect.call("getUid") as? ByteArray)
                ?.toHexString(HexFormat.UpperCase) ?: return@intercept chain.proceed()
            val config =
                PreferenceProvider.loadPreference()?.other ?: return@intercept chain.proceed()
            val listed = uid in config.nfcCards
            // 移除检测已由系统启动；在 OEM 分发回调前终止，避免触发首次读卡通知。
            if (config.nfcFilterMode == 1 && listed || config.nfcFilterMode == 2 && !listed) null
            else chain.proceed()
        }
    }
}
