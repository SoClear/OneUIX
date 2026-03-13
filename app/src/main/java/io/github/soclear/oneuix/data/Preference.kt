package io.github.soclear.oneuix.data

import kotlinx.serialization.Serializable

@Serializable
data class Preference(
    val android: Android = Android(),
    val systemUI: SystemUI = SystemUI(),
    val settings: Settings = Settings(),
    val call: Call = Call(),
    val camera: Camera = Camera(),
    val other: Other = Other(),
) {
    @Serializable
    data class Android(
        val disablePinVerifyPer72h: Boolean = false,
        val modifyMaxNeverKilledAppNum: Boolean = false,
        val maxNeverKilledAppNum: Int = 5,
        val setBlockableNotificationChannel: Boolean = false,
        val supportAppJumpBlock: Boolean = false,
    )

    @Serializable
    data class SystemUI(
        val statusBar: StatusBar = StatusBar(),
        val qs: QS = QS(),
        val aod: AOD = AOD(),
        val other: Other = Other(),
    ) {
        @Serializable
        data class StatusBar(
            val modifyStatusBarLeftPadding: Boolean = false,
            val statusBarLeftPaddingDp: Float = 0f,
            val modifyStatusBarRightPadding: Boolean = false,
            val statusBarRightPaddingDp: Float = 0f,
            val hideBatteryPercentageSign: Boolean = false,
            val supportRealTimeNetworkSpeed: Boolean = true,
            val showSeparateUpDownNetworkSpeeds: Boolean = false,
            val setStatusBarClockFormat: Boolean = false,
            val statusBarClockFormat: String = "HH:mm",
            val updateStatusBarClockEverySecond: Boolean = false,
            val setCompactChineseDateTime: Boolean = false,
            val hideSecureFolderStatusBarIcon: Boolean = false,
            val doubleTapStatusBarToSleep: Boolean = false,
            val modifyStatusBarMaxNotificationIcons: Boolean = false,
            val statusBarMaxNotificationIcons: Int = 4,
        )

        @Serializable
        data class QS(
            val setQsClockMonospaced: Boolean = false,
            val hideDeviceControlQsTile: Boolean = false,
            val turnOn5gQsTile: Boolean = false,
            val hideQsBarMediaPlayer: Boolean = false,
            val hideQsBarNearbyDevicesAndDeviceControl: Boolean = false,
            val hideQsBarSecurityFooter: Boolean = false,
            val hideQsBarSmartViewAndModes: Boolean = false,
            val alwaysExpandQsTileChunk: Boolean = false,
            val alwaysShowTimeDateOnQs: Boolean = false,
            val addBrightnessProgressToQsBar: Boolean = false,
            val addVolumeProgressToQsBar: Boolean = false,
            val showTraditionalChineseDateOnQS: Boolean = false,
            val modifyQSClockTextSize: Boolean = false,
            val qsClockTextSize: Float = 32f,
        )

        @Serializable
        data class AOD(
            val hideAODStatusBar: Boolean = false,
            val aodLockSupportLunar: Boolean = false,
        )

        @Serializable
        data class Other(
            val disableScreenshotCaptureSound: Boolean = false,
        )
    }

    @Serializable
    data class Settings(
        val showForcePeakRefreshRatePreference: Boolean = true,
        val showMoreBatteryInfo: Boolean = true,
        val showPackageInfo: Boolean = true,
        val showWiFiLinkSpeed: Boolean = false,
        val supportAnyFont: Boolean = true,
        val supportAutoPowerOnOff: Boolean = false,
    )

    @Serializable
    data class Call(
        val supportVoiceCallRecording: Boolean = true,
        val preferRecordingButton: Boolean = true,
        val showGeocodedLocationInRecentCall: Boolean = false,
        val isOpStyleCHN: Boolean = false,
    )

    @Serializable
    data class Camera(
        val supportAllCameraMenu: Boolean = true,
        val disableCameraTemperatureCheck: Boolean = false,
    )

    @Serializable
    data class Other(
        val blockGalaxyStoreAds: Boolean = true,
        val makeAllUserAppsAvailable: Boolean = true,
        val setWeatherProviderCN: Boolean = false,
        val showMemoryUsageInRecents: Boolean = false,
        val showMorePlaybackSpeeds: Boolean = false,
        val supportAllGallerySettings: Boolean = true,
        val supportAllNotesFeatures: Boolean = true,
        val enableChineseHolidayDisplay: Boolean = false,
        val supportBlockMessage: Boolean = true,
        val setThemeTrialNeverExpired: Boolean = true,
        val customizeBrowserSearchEngine: Boolean = false,
        val noAIWatermark: Boolean = true,
        val bypassHealthMonitorCountryCheck: Boolean = false,
    )
}
