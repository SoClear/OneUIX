package io.github.soclear.oneuix.ui.category

import io.github.soclear.oneuix.BuildConfig
import io.github.soclear.oneuix.data.Package

enum class Category(val packageName: String) {
    Android(Package.ANDROID),
    SystemUI(Package.SYSTEMUI),
    Settings(Package.SETTINGS),
    Call(Package.DIALER),
    Camera(Package.CAMERA),
    Bixby(Package.BIXBY_AGENT),
    Other(BuildConfig.APPLICATION_ID);
}
