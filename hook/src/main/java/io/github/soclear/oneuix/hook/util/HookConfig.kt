package io.github.soclear.oneuix.hook.util

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

interface HookConfig {
    val versionCode: Long
}

inline fun <reified T : HookConfig> Context.getHookConfig(
    file: File = File(filesDir, "HookConfig.json"),
    getHookConfigFromDexKit: Context.() -> T?,
): T? {
    // 先从本地文件加载 hook 配置
    val hookConfigFromFile: T? = try {
        val string = file.readText()
        Json.decodeFromString(string)
    } catch (_: Exception) {
        null
    }
    // 如果本地 hook 配置匹配当前应用版本，则返回
    if (hookConfigFromFile?.versionCode == packageManager.getPackageInfo(
            packageName,
            0
        ).longVersionCode
    ) {
        return hookConfigFromFile
    }
    // 否则从 DexKit 解析 hook 配置
    val hookConfigFromDexKit = getHookConfigFromDexKit()
    if (hookConfigFromDexKit != null) {
        // 解析成功，保存到本地并返回
        val string = Json.encodeToString(hookConfigFromDexKit)
        file.writeText(string)
        return hookConfigFromDexKit
    }
    // DexKit 解析 hook 配置失败
    return null
}
