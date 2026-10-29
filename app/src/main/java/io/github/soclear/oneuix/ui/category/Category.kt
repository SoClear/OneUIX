package io.github.soclear.oneuix.ui.category

import io.github.soclear.oneuix.common.Package

enum class Category(val packageName: String) {
    Android(Package.ANDROID),
    SystemUI(Package.SYSTEMUI),
    Settings(Package.SETTINGS),
    Call(Package.DIALER),
    Camera(Package.CAMERA),
    Bixby(Package.BIXBY_AGENT),
    Browser(Package.BROWSER),
    Calendar(Package.CALENDAR),
    DualApp(Package.DUAL_APP),
    Gallery(Package.GALLERY),
    GalaxyStore(Package.STORE),
    Health(Package.HEALTH),
    HealthMonitor(Package.HEALTH_MONITOR),
    Launcher(Package.LAUNCHER),
    Messaging(Package.MESSAGING),
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
}
