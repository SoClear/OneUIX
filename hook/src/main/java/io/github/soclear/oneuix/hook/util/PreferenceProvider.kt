package io.github.soclear.oneuix.hook.util

import de.robv.android.xposed.XSharedPreferences
import io.github.soclear.oneuix.common.BuildConfig
import io.github.soclear.oneuix.common.IgnoreUnknownKeysJson
import io.github.soclear.oneuix.common.Preference
import java.io.File

object PreferenceProvider {
    val preference: Preference? = try {
        IgnoreUnknownKeysJson.decodeFromString<Preference>(getPreferenceFile().readText())
    } catch (_: Throwable) {
        // 如果一个用户启用模块后，没有点过任何偏好设置
        // 那么调用 getPreferenceFile().readText() 会 FileNotFoundException
        // 导致 preference 为空，于是不会有任何 hook 生效
        // 意料之外，情理之中
        null
    }

    fun getPreferenceFile(): File {
        val path = XSharedPreferences(BuildConfig.MODULE_APPLICATION_ID).file.parent
        return File(path, Preference.FILE_NAME)
    }
}
