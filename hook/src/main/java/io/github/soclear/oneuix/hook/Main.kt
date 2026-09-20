package io.github.soclear.oneuix.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.systemui.AOD
import io.github.soclear.oneuix.hook.systemui.ESIM
import io.github.soclear.oneuix.hook.systemui.HideBatteryIcon
import io.github.soclear.oneuix.hook.systemui.Notification
import io.github.soclear.oneuix.hook.systemui.Other
import io.github.soclear.oneuix.hook.systemui.QS
import io.github.soclear.oneuix.hook.systemui.StatusBar
import io.github.soclear.oneuix.hook.systemui.powermenu.PowerMenu
import io.github.soclear.oneuix.hook.util.PreferenceProvider
import io.github.soclear.oneuix.hook.util.addAssetPath

class Main : XposedModule() {
    private var processName = ""

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        processName = param.processName
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) = with(param) {
        val preference = PreferenceProvider.loadPreference() ?: return@with

        when (packageName) {
            Package.BROWSER -> {
                if (preference.other.showMorePlaybackSpeeds) {
                    Browser.showMorePlaybackSpeeds()
                }

                if (preference.other.spoofBrowserCountryCodeToUS) {
                    Browser.setCountryIsoCode("US")
                }

                if (preference.other.redirectCustomTab) {
                    Browser.redirectCustomTab()
                }
            }

            Package.CALENDAR -> {
                if (preference.other.enableChineseHolidayDisplay) {
                    Calendar.enableChineseHolidayDisplay()
                }
            }

            Package.CAMERA -> {
                Camera.setBooleanFeature(
                    supportAllMenu = preference.camera.supportAllCameraMenu,
                    disableTemperatureCheck = preference.camera.disableCameraTemperatureCheck
                )
            }

            Package.DIALER -> {
                if (preference.call.supportVoiceCallRecording) {
                    Call.supportVoiceCallRecording(
                        preference.call.preferRecordingButton
                    )
                }

                if (preference.call.showGeocodedLocationInRecentCall) {
                    Call.showGeocodedLocationInRecentCall()
                }

                if (preference.call.isOpStyleCHN) {
                    Call.isOpStyleCHN()
                }
            }

            Package.DUAL_APP -> {
                if (preference.other.makeAllUserAppsAvailable) {
                    DualApp.makeAllUserAppsAvailable()
                }
            }

            Package.GALLERY -> {
                if (preference.other.supportAllGallerySettings) {
                    Gallery.supportAllSettings()
                }

                if (preference.other.supportSharedAlbumsInHide) {
                    Gallery.supportSharedAlbumsInHide()
                }

                if (preference.other.hideVideoEditorStudio) {
                    Gallery.hideVideoEditorStudio()
                }
            }

            Package.HEALTH_MONITOR -> {
                if (preference.other.bypassHealthMonitorCountryCheck) {
                    HealthMonitor.bypassCountryCheck()
                }
            }

            Package.INCALLUI -> {
                if (preference.call.supportVoiceCallRecording) {
                    Call.supportVoiceCallRecording(
                        preference.call.preferRecordingButton
                    )
                }
            }

            Package.LAUNCHER -> {
                if (preference.other.showMemoryUsageInRecents) {
                    Launcher.showMemoryUsageInRecents()
                }

                if (preference.other.hideRecentsCloseAllButton) {
                    Launcher.hideRecentsCloseAllButton()
                }

                if (preference.other.hideAppsSearchBar) {
                    Launcher.hideAppsSearchBar()
                }

                if (preference.other.removeShortcutBadge) {
                    Launcher.removeShortcutBadge()
                }
            }

            Package.MDEC_SERVICE -> {
                if (preference.call.supportCallAndTextOnOtherDevices) {
                    MdecService.supportCallAndTextOnOtherDevices()
                }
            }

            Package.MESSAGING -> {
                if (preference.other.supportBlockMessage) {
                    Messaging.isSupportBlock()
                }
            }

            Package.NOTES -> {
                if (preference.other.supportAllNotesFeatures) {
                    Notes.supportAllFeatures()
                }
            }

            Package.PHOTO_RETOUCHING -> {
                if (preference.other.noAIWatermark) {
                    PhotoRetouching.noAIWatermark()
                }
            }

            Package.SETTINGS -> {
                if (preference.android.setBlockableNotificationChannel) {
                    Android.setBlockableNotificationChannel()
                }

                if (preference.settings.showForcePeakRefreshRatePreference) {
                    Settings.showForcePeakRefreshRatePreference()
                }

                if (preference.settings.supportOutdoorMode) {
                    Settings.supportOutdoorMode()
                }

                if (preference.settings.showMoreBatteryInfo) {
                    Settings.showMoreBatteryInfo()
                }

                if (preference.settings.showPackageInfo) {
                    Settings.showPackageInfo()
                }

                if (preference.settings.showWiFiLinkSpeed) {
                    Network.showWiFiLinkSpeed()
                }

                if (preference.settings.supportAnyFont) {
                    Settings.supportAnyFont()
                }

                if (preference.android.supportAppJumpBlock) {
                    CoreRune.supportAppJumpBlockSettings()
                }

                if (preference.systemUI.statusBar.supportRealTimeNetworkSpeed) {
                    Network.supportRealTimeNetworkSpeed()
                }

                if (preference.settings.supportAutoPowerOnOff) {
                    Settings.supportAutoPowerOnOff()
                }

                if (preference.settings.spoofPhoneStatusAsOfficial) {
                    Settings.spoofPhoneStatusAsOfficial()
                }
            }

            Package.SKETCH_BOOK -> {
                if (preference.other.noAIWatermark) {
                    SketchBook.noAIWatermark()
                }
            }

            Package.SM_CN -> {
                if (preference.settings.spoofPhoneStatusAsOfficial) {
                    SMCN.spoofPhoneStatusAsOfficial()
                }
            }

            Package.STORE -> {
                if (preference.other.blockGalaxyStoreAds) {
                    GalaxyStore.blockGalaxyStoreAds()
                }
            }

            Package.SYSTEMUI if (processName == Package.SYSTEMUI) -> {
                if (preference.android.setBlockableNotificationChannel) {
                    Android.setBlockableNotificationChannel()
                }

                if (preference.systemUI.notification.autoExpandNotifications) {
                    Notification.autoExpandNotifications()
                }

                run {
                    val leftPaddingDp =
                        if (preference.systemUI.statusBar.modifyStatusBarLeftPadding) {
                            preference.systemUI.statusBar.statusBarLeftPaddingDp
                        } else null
                    val rightPaddingDp =
                        if (preference.systemUI.statusBar.modifyStatusBarRightPadding) {
                            preference.systemUI.statusBar.statusBarRightPaddingDp
                        } else null
                    StatusBar.setStatusBarPaddingDp(leftPaddingDp, rightPaddingDp)
                }

                run {
                    val widthScale = if (preference.systemUI.statusBar.setBatteryIconWidthScale) {
                        preference.systemUI.statusBar.batteryIconWidthScale
                    } else null
                    val heightScale = if (preference.systemUI.statusBar.setBatteryIconHeightScale) {
                        preference.systemUI.statusBar.batteryIconHeightScale
                    } else null
                    StatusBar.setBatteryIconScale(widthScale, heightScale)
                }

                if (preference.systemUI.statusBar.hideBatteryPercentageSign) {
                    StatusBar.hideBatteryPercentageSign()
                }

                if (preference.systemUI.statusBar.hideBatteryIcon) {
                    HideBatteryIcon.apply()
                }

                if (preference.systemUI.statusBar.addBatteryLevelText) {
                    StatusBar.addBatteryLevelText(
                        hidePercentSign = preference.systemUI.statusBar.hideBatteryLevelTextPercentageSign,
                        hideChargingIcon = preference.systemUI.statusBar.hideBatteryLevelTextChargingIcon,
                    )
                }

                if (preference.systemUI.statusBar.supportRealTimeNetworkSpeed) {
                    Network.supportRealTimeNetworkSpeed()
                }

                if (preference.systemUI.statusBar.showSeparateUpDownNetworkSpeeds) {
                    Network.showSeparateUpDownNetworkSpeeds(
                        thresholdKb = preference.systemUI.statusBar.networkSpeedThresholdKb
                    )
                }

                if (preference.systemUI.statusBar.setStatusBarClockFormat) {
                    val format = preference.systemUI.statusBar.statusBarClockFormat
                    StatusBar.setStatusBarClockFormat(format)
                }

                if (preference.systemUI.statusBar.setStatusBarClockTextScale) {
                    val scale = preference.systemUI.statusBar.statusBarClockTextScale
                    StatusBar.setStatusBarClockTextScale(scale)
                }

                if (preference.systemUI.statusBar.updateStatusBarClockEverySecond) {
                    StatusBar.updateStatusBarClockEverySecond()
                }

                if (preference.systemUI.statusBar.hideSecureFolderStatusBarIcon) {
                    StatusBar.hideSecureFolderStatusBarIcon()
                }

                if (preference.systemUI.statusBar.restoreBluetoothStatusBarIcon) {
                    StatusBar.restoreBluetoothStatusBarIcon()
                }

                if (preference.systemUI.statusBar.physicalEsimAdapterWorkaround) {
                    ESIM.workaroundPhysicalEsimAdapter(
                        preference.systemUI.statusBar.physicalEsimAdapterSimSlot
                    )
                }

                if (preference.systemUI.statusBar.doubleTapStatusBarToSleep) {
                    StatusBar.doubleTapStatusBarToSleep()
                }

                if (preference.systemUI.statusBar.modifyStatusBarMaxNotificationIcons) {
                    val max = preference.systemUI.statusBar.statusBarMaxNotificationIcons
                    Notification.setStatusBarMaxNotificationIcons(max)
                }

                if (preference.systemUI.statusBar.setCustomCarrierName) {
                    StatusBar.setCustomCarrierName(preference.systemUI.statusBar.customCarrierName)
                }

                if (preference.systemUI.statusBar.hideLockscreenStatusBar) {
                    StatusBar.hideLockscreenStatusBar()
                }

                if (preference.settings.supportOutdoorMode) {
                    QS.supportOutdoorMode()
                }

                run {
                    val monospaced = preference.systemUI.qs.setQsClockMonospaced
                    val modifyTextSize = preference.systemUI.qs.modifyQSClockTextSize
                    val textSize = preference.systemUI.qs.qsClockTextSize
                    QS.setQsClockStyle(monospaced, modifyTextSize, textSize)
                }

                if (preference.systemUI.qs.hideDeviceControlQsTile) {
                    QS.hideDeviceControlQsTile()
                }

                if (preference.systemUI.qs.hideSmartViewQsTile) {
                    QS.hideSmartViewQsTile()
                }

                if (preference.systemUI.qs.turnOn5gQsTile) {
                    Network.turnOn5gQsTile()
                }

                run {
                    val qsBarSet = buildSet {
                        if (preference.systemUI.qs.hideQsBarMediaPlayer) {
                            add(QS.QsBar.MediaPlayer)
                        }
                        if (preference.systemUI.qs.hideQsBarNearbyDevicesAndDeviceControl) {
                            add(QS.QsBar.NearbyDevicesAndDeviceControl)
                        }
                        if (preference.systemUI.qs.hideQsBarSecurityFooter) {
                            add(QS.QsBar.SecurityFooter)
                        }
                        if (preference.systemUI.qs.hideQsBarDataUsage) {
                            add(QS.QsBar.DataUsage)
                        }
                        if (preference.systemUI.qs.hideQsBarSmartViewAndModes) {
                            add(QS.QsBar.SmartViewAndModes)
                        }
                    }

                    QS.hideQsBar(qsBarSet)
                }

                if (preference.systemUI.qs.alwaysExpandQsTileChunk) {
                    QS.alwaysExpandQsTileChunk()
                }

                if (preference.systemUI.qs.alwaysShowTimeDateOnQs) {
                    QS.alwaysShowTimeDateOnQs()
                }

                if (preference.systemUI.qs.addBrightnessProgressToQsBar) {
                    QS.addBrightnessProgressToQsBar()
                }

                if (preference.systemUI.qs.addVolumeProgressToQsBar) {
                    QS.addVolumeProgressToQsBar()
                }

                if (preference.systemUI.qs.showTraditionalChineseDateOnQS) {
                    QS.showTraditionalChineseDateOnQS()
                }

                if (preference.systemUI.aod.hideAODStatusBar) {
                    AOD.hideAODStatusBar()
                }

                if (preference.systemUI.aod.aodLockSupportLunar) {
                    AOD.aodLockSupportLunar()
                }

                if (preference.systemUI.other.disableScreenshotCaptureSound) {
                    Other.disableScreenshotCaptureSound()
                }
                if (preference.systemUI.notification.disableNotificationGrouping) {
                    Notification.disableNotificationGrouping()
                }
                if (preference.systemUI.notification.hideOngoingActivityMedia) {
                    Notification.hideOngoingActivityMedia(
                        preference.systemUI.notification.hideOngoingActivityMediaPackages
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .toSet()
                    )
                }
                if (preference.systemUI.other.customPowerMenu) {
                    addAssetPath(moduleApplicationInfo.sourceDir)
                    PowerMenu.hookPowerMenuActions(
                        preference.systemUI.other.powerMenuActions,
                    )
                }
            }

            Package.TELEPHONYUI -> {
                if (preference.call.supportVoiceCallRecording) {
                    Call.supportVoiceCallRecording(
                        preference.call.preferRecordingButton
                    )
                }

                if (preference.systemUI.qs.turnOn5gQsTile) {
                    Network.turnOn5gQsTile()
                }
            }

            Package.THEME_CENTER -> {
                if (preference.other.setThemeTrialNeverExpired) {
                    ThemeCenter.setTrialNeverExpired()
                }
            }

            Package.WEATHER -> {
                if (preference.other.setWeatherProviderCN) {
                    Weather.setProviderCN()
                }
            }

            Package.VIDEO -> {
                if (preference.other.showMorePlaybackSpeeds) {
                    Video.showMorePlaybackSpeeds()
                }
            }

            Package.WATCH_MANAGER -> {
                if (preference.other.bypassWatchPairingRegionCheck ||
                    preference.other.watchPairingConnectionMode != WatchPairing.MODE_NONE
                ) {
                    WatchPairing.init(
                        bypassRegionCheck = preference.other.bypassWatchPairingRegionCheck,
                        connectionMode = preference.other.watchPairingConnectionMode,
                        supplementChinaWearOsGms = preference.other.supplementChinaWearOsGms
                    )
                }
            }

            Package.TRANSLATION -> {
                if (preference.other.useSPenGoogleTranslate) {
                    SPen.switchTranslateSource(useGoogle = true)
                }
            }
        }
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) = with(param) {
        val preference = PreferenceProvider.loadPreference() ?: return@with

        if (preference.android.disableWritingToolkitGlobally) {
            Android.disableWritingToolkitGlobally()
        }

        if (preference.android.disablePinVerifyPer72h) {
            Android.disablePinVerifyPer72h()
        }

        if (preference.android.modifyMaxNeverKilledAppNum) {
            Android.setMaxNeverKilledAppNum(
                preference.android.maxNeverKilledAppNum
            )
        }

        if (preference.android.setBlockableNotificationChannel) {
            Android.setBlockableNotificationChannel()
        }

        if (preference.android.supportAppJumpBlock) {
            CoreRune.supportAppJumpBlockAndroid()
        }

        if (preference.android.allowAllRotation) {
            CoreRune.allowAllRotation()
        }

        if (preference.android.liftFcmNetworkLimit) {
            Android.liftFcmNetworkLimit()
        }

        if (preference.android.disableScreenWakeOnPowerUnplugged) {
            Android.disableScreenWakeOnPowerUnplugged()
        }
    }
}
