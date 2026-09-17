package io.github.soclear.oneuix.hook.util

import io.github.libxposed.api.XposedModule
import io.github.soclear.oneuix.common.IgnoreUnknownKeysJson
import io.github.soclear.oneuix.common.Preference
import java.io.FileInputStream

object PreferenceProvider {
    // libxposed 102 通过框架的 remote file 机制读取模块配置
    // （宿主进程对 remote file 只读，由模块 App 通过 libxposed service 推送到共享目录）
    context(xposedModule: XposedModule)
    fun loadPreference(): Preference? = try {
        xposedModule.openRemoteFile(Preference.FILE_NAME).use { parcelFileDescriptor ->
            val preferenceJson = FileInputStream(parcelFileDescriptor.fileDescriptor)
                .use { it.readBytes() }
                .decodeToString()
            IgnoreUnknownKeysJson.decodeFromString<Preference>(preferenceJson)
        }
    } catch (_: Throwable) {
        // 模块 App 尚未推送配置文件（或用户未设置过偏好）时不启用任何 hook
        null
    }
}
