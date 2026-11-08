package io.github.soclear.oneuix.common

import android.content.res.Configuration

/** Samsung's main display is 0; the Fold cover display is 5. */
fun Configuration.semDisplayDeviceTypeOrNull(): Int? = runCatching {
    javaClass.getField("semDisplayDeviceType").getInt(this)
}.getOrNull()
