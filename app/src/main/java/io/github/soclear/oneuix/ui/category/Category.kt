package io.github.soclear.oneuix.ui.category

import io.github.soclear.oneuix.common.Package

enum class Category(val packageName: String, val additionalPackages: List<String> = emptyList()) {
    Android(Package.ANDROID),
    SystemUI(Package.SYSTEMUI),
    Settings(Package.SETTINGS, listOf(Package.SM_CN)),
    Call(Package.DIALER, listOf(Package.INCALLUI, Package.TELEPHONYUI, Package.MDEC_SERVICE)),
    Camera(Package.CAMERA),
    Bixby(Package.BIXBY_AGENT, listOf(Package.BIXBY_WAKEUP)),
    Browser(Package.BROWSER),
    Calendar(Package.CALENDAR),
    DualApp(Package.DUAL_APP),
    Gallery(Package.GALLERY),
    GalaxyStore(Package.STORE),
    Health(Package.HEALTH),
    HealthMonitor(Package.HEALTH_MONITOR),
    Launcher(Package.LAUNCHER),
    Messaging(Package.MESSAGING),
    MyFiles(Package.MY_FILES),
    NFC(Package.NFC),
    Notes(Package.NOTES),
    PhotoRetouching(Package.PHOTO_RETOUCHING),
    SketchBook(Package.SKETCH_BOOK),
    SPen(Package.TRANSLATION),
    ThemeCenter(Package.THEME_CENTER),
    Video(Package.VIDEO),
    WatchManager(Package.WATCH_MANAGER),
    Weather(Package.WEATHER),
    Other("");

    val packages: List<String>
        get() = listOf(packageName) + additionalPackages
}
