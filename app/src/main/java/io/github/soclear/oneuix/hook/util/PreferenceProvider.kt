package io.github.soclear.oneuix.hook.util

import android.annotation.SuppressLint
import de.robv.android.xposed.XSharedPreferences
import io.github.soclear.oneuix.BuildConfig
import io.github.soclear.oneuix.data.IgnoreUnknownKeysJson
import io.github.soclear.oneuix.data.Preference
import java.io.File

object PreferenceProvider {
    private const val PREFERENCE_FILE_NAME = "preference.json"

    val preference: Preference? = try {
        IgnoreUnknownKeysJson.decodeFromString<Preference>(getPreferenceFile().readText())
    } catch (_: Throwable) {
        null
    }

    fun getPreferenceFile(): File {
        val path = XSharedPreferences(BuildConfig.APPLICATION_ID).file.parent
        val file = File(path, PREFERENCE_FILE_NAME)

        if (!file.exists()) {
            file.writeText("{}")
            @SuppressLint("SetWorldReadable")
            file.setReadable(true, false)
        }

        return file
    }
}
