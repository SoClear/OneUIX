package io.github.soclear.oneuix.hook.util

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

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
        val string = Json.encodeToString(hookConfigFromDexKit)
        // 缓存仅用于加速。开机首次解锁前 CE 目录可能不可用，写入失败不能中断 Hook。
        try {
            file.parentFile?.mkdirs()
            file.writeText(string)
        } catch (e: IOException) {
            Log.w("OneUIX", "Cannot save hook config to $file; using parsed config", e)
        }
        return hookConfigFromDexKit
    }
    // DexKit 解析 hook 配置失败
    return null
}
