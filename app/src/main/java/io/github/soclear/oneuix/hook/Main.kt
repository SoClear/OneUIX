package io.github.soclear.oneuix.hook

import de.robv.android.xposed.IXposedHookInitPackageResources
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.BuildConfig
import io.github.soclear.oneuix.data.Package
import io.github.soclear.oneuix.hook.util.PreferenceProvider


@Suppress("unused")
class Main : IXposedHookLoadPackage, IXposedHookInitPackageResources {
    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName == BuildConfig.APPLICATION_ID) {
            Self.enableDataStoreFileSharing(lpparam)
        }

        val preference = PreferenceProvider.preference ?: return

        when (lpparam.packageName) {
            Package.ANDROID -> {
                if (preference.android.disablePinVerifyPer72h) {
                    Android.disablePinVerifyPer72h(lpparam)
                }

                if (preference.android.modifyMaxNeverKilledAppNum) {
                    Android.setMaxNeverKilledAppNum(
                        lpparam,
                        preference.android.maxNeverKilledAppNum
                    )
                }

                if (preference.android.setBlockableNotificationChannel) {
                    Android.setBlockableNotificationChannel()
                }

                if (preference.android.supportAppJumpBlock) {
                    CoreRune.supportAppJumpBlock(lpparam)
                }
            }

            Package.BROWSER -> {
                if (preference.other.showMorePlaybackSpeeds) {
                    Browser.showMorePlaybackSpeeds(lpparam)
                }

                if (preference.other.customizeBrowserSearchEngine) {
                    Browser.setCountryIsoCode(lpparam, "US")
                }
            }

            Package.CALENDAR -> {
                if (preference.other.enableChineseHolidayDisplay) {
                    Calendar.enableChineseHolidayDisplay(lpparam)
                }
            }

            Package.CAMERA -> {
                Camera.setBooleanFeature(
                    loadPackageParam = lpparam,
                    supportAllMenu = preference.camera.supportAllCameraMenu,
                    disableTemperatureCheck = preference.camera.disableCameraTemperatureCheck
                )
            }

            Package.DIALER -> {
                if (preference.call.supportVoiceCallRecording) {
                    Call.supportVoiceCallRecording(
                        lpparam,
                        preference.call.preferRecordingButton
                    )
                }

                if (preference.call.showGeocodedLocationInRecentCall) {
                    Call.showGeocodedLocationInRecentCall(lpparam)
                }

                if (preference.call.isOpStyleCHN) {
                    Call.isOpStyleCHN(lpparam)
                }
            }

            Package.DUAL_APP -> {
                if (preference.other.makeAllUserAppsAvailable) {
                    DualApp.makeAllUserAppsAvailable(lpparam)
                }
            }

            Package.GALLERY -> {
                if (preference.other.supportAllGallerySettings) {
                    Gallery.supportAllSettings(lpparam)
                }
            }

            Package.INCALLUI -> {
                if (preference.call.supportVoiceCallRecording) {
                    Call.supportVoiceCallRecording(
                        lpparam,
                        preference.call.preferRecordingButton
                    )
                }
            }

            Package.LAUNCHER -> {
                if (preference.other.showMemoryUsageInRecents) {
                    Launcher.showMemoryUsageInRecents(lpparam)
                }
            }

            Package.MESSAGING -> {
                if (preference.other.supportBlockMessage) {
                    Messaging.isSupportBlock(lpparam)
                }
            }

            Package.NOTES -> {
                if (preference.other.supportAllNotesFeatures) {
                    Notes.supportAllFeatures(lpparam)
                }
            }

            Package.PHOTO_RETOUCHING -> {
                if (preference.other.noAIWatermark) {
                    PhotoRetouching.noAIWatermark()
                }
            }

            Package.SETTINGS -> {
                if (preference.settings.showForcePeakRefreshRatePreference) {
                    Settings.showForcePeakRefreshRatePreference(lpparam)
                }

                if (preference.settings.showMoreBatteryInfo) {
                    Settings.showMoreBatteryInfo(lpparam)
                }

                if (preference.settings.showPackageInfo) {
                    Settings.showPackageInfo(lpparam)
                }

                if (preference.settings.showWiFiLinkSpeed) {
                    Network.showWiFiLinkSpeed(lpparam)
                }

                if (preference.settings.supportAnyFont) {
                    Settings.supportAnyFont(lpparam)
                }

                if (preference.android.supportAppJumpBlock) {
                    CoreRune.supportAppJumpBlock(lpparam)
                }

                if (preference.systemUI.statusBar.supportRealTimeNetworkSpeed) {
                    Network.supportRealTimeNetworkSpeed(lpparam)
                }

                if (preference.settings.supportAutoPowerOnOff) {
                    Settings.supportAutoPowerOnOff(lpparam)
                }
            }

            Package.STORE -> {
                if (preference.other.blockGalaxyStoreAds) {
                    GalaxyStore.blockGalaxyStoreAds(lpparam)
                }
            }

            Package.SYSTEMUI -> {
                run {
                    val leftPaddingDp =
                        if (preference.systemUI.statusBar.modifyStatusBarLeftPadding) {
                            preference.systemUI.statusBar.statusBarLeftPaddingDp
                        } else null
                    val rightPaddingDp =
                        if (preference.systemUI.statusBar.modifyStatusBarRightPadding) {
                            preference.systemUI.statusBar.statusBarRightPaddingDp
                        } else null
                    SystemUI.setStatusBarPaddingDp(lpparam, leftPaddingDp, rightPaddingDp)
                }

                if (preference.systemUI.statusBar.supportRealTimeNetworkSpeed) {
                    Network.supportRealTimeNetworkSpeed(lpparam)
                }

                if (preference.systemUI.statusBar.showSeparateUpDownNetworkSpeeds) {
                    Network.showSeparateUpDownNetworkSpeeds(lpparam)
                }

                if (preference.systemUI.statusBar.setStatusBarClockFormat) {
                    val format = preference.systemUI.statusBar.statusBarClockFormat
                    SystemUI.setStatusBarClockFormat(lpparam, format)
                }

                if (preference.systemUI.statusBar.updateStatusBarClockEverySecond) {
                    SystemUI.updateStatusBarClockEverySecond(lpparam)
                }

                if (preference.systemUI.statusBar.setCompactChineseDateTime) {
                    SystemUI.setCompactChineseDateTime(lpparam)
                }

                if (preference.systemUI.statusBar.hideSecureFolderStatusBarIcon) {
                    SystemUI.hideSecureFolderStatusBarIcon(lpparam)
                }

                if (preference.systemUI.statusBar.doubleTapStatusBarToSleep) {
                    SystemUI.doubleTapStatusBarToSleep(lpparam)
                }

                if (preference.systemUI.statusBar.modifyStatusBarMaxNotificationIcons) {
                    val max = preference.systemUI.statusBar.statusBarMaxNotificationIcons
                    SystemUI.setStatusBarMaxNotificationIcons(lpparam, max)
                }

                run {
                    val monospaced = preference.systemUI.qs.setQsClockMonospaced
                    val modifyTextSize = preference.systemUI.qs.modifyQSClockTextSize
                    val textSize = preference.systemUI.qs.qsClockTextSize
                    SystemUI.setQsClockStyle(lpparam, monospaced, modifyTextSize, textSize)
                }

                if (preference.systemUI.qs.hideDeviceControlQsTile) {
                    SystemUI.hideDeviceControlQsTile(lpparam)
                }

                if (preference.systemUI.qs.turnOn5gQsTile) {
                    Network.turnOn5gQsTile(lpparam)
                }

                run {
                    val qsBarSet = buildSet {
                        if (preference.systemUI.qs.hideQsBarMediaPlayer) {
                            add(SystemUI.QsBar.MediaPlayer)
                        }
                        if (preference.systemUI.qs.hideQsBarNearbyDevicesAndDeviceControl) {
                            add(SystemUI.QsBar.NearbyDevicesAndDeviceControl)
                        }
                        if (preference.systemUI.qs.hideQsBarSecurityFooter) {
                            add(SystemUI.QsBar.SecurityFooter)
                        }
                        if (preference.systemUI.qs.hideQsBarSmartViewAndModes) {
                            add(SystemUI.QsBar.SmartViewAndModes)
                        }
                    }

                    SystemUI.hideQsBar(lpparam, qsBarSet)
                }

                if (preference.systemUI.qs.alwaysExpandQsTileChunk) {
                    SystemUI.alwaysExpandQsTileChunk(lpparam)
                }

                if (preference.systemUI.qs.alwaysShowTimeDateOnQs) {
                    SystemUI.alwaysShowTimeDateOnQs(lpparam)
                }

                if (preference.systemUI.qs.addBrightnessProgressToQsBar) {
                    SystemUI.addBrightnessProgressToQsBar(lpparam)
                }

                if (preference.systemUI.qs.addVolumeProgressToQsBar) {
                    SystemUI.addVolumeProgressToQsBar(lpparam)
                }

                if (preference.systemUI.qs.showTraditionalChineseDateOnQS) {
                    SystemUI.showTraditionalChineseDateOnQS(lpparam)
                }

                if (preference.systemUI.aod.hideAODStatusBar) {
                    SystemUI.hideAODStatusBar(lpparam)
                }

                if (preference.systemUI.aod.aodLockSupportLunar) {
                    SystemUI.aodLockSupportLunar(lpparam)
                }

                if (preference.systemUI.other.disableScreenshotCaptureSound) {
                    SystemUI.disableScreenshotCaptureSound(lpparam)
                }
            }

            Package.TELEPHONYUI -> {
                if (preference.call.supportVoiceCallRecording) {
                    Call.supportVoiceCallRecording(
                        lpparam,
                        preference.call.preferRecordingButton
                    )
                }

                if (preference.systemUI.qs.turnOn5gQsTile) {
                    Network.turnOn5gQsTile(lpparam)
                }
            }

            Package.THEME_CENTER -> {
                if (preference.other.setThemeTrialNeverExpired) {
                    ThemeCenter.setTrialNeverExpired(lpparam)
                }
            }

            Package.WEATHER -> {
                if (preference.other.setWeatherProviderCN) {
                    Weather.setProviderCN(lpparam)
                }
            }

            Package.VIDEO -> {
                if (preference.other.showMorePlaybackSpeeds) {
                    Video.showMorePlaybackSpeeds(lpparam)
                }
            }

            Package.HEALTH_MONITOR -> {
                if (preference.other.bypassHealthMonitorCountryCheck) {
                    HealthMonitor.bypassCountryCheck(lpparam)
                }
            }
        }
    }

    override fun handleInitPackageResources(resparam: InitPackageResourcesParam) {
        if (resparam.packageName != Package.SYSTEMUI) {
            return
        }
        val preference = PreferenceProvider.preference ?: return
        if (preference.systemUI.statusBar.hideBatteryPercentageSign) {
            SystemUI.hideBatteryPercentageSign(resparam)
        }
    }
}
