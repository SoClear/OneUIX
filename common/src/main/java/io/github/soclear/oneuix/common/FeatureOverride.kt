package io.github.soclear.oneuix.common

import kotlinx.serialization.Serializable

/**
 * A Samsung feature gate answered with a different value.
 *
 * Samsung keeps its feature switches in two tables: CSC features, which `SemCscFeature` answers and
 * which carriers and regions fill in, and floating features, which `SemFloatingFeature` answers and
 * which the build fills in. Both are fixed on the device, and the CSC files are encrypted, so the
 * way to reach a gate is to answer the lookup: the module hooks those getters in each hooked app and
 * hands back [value] for [key].
 *
 * [kind] says which of the two tables the key belongs to. The presets below are the gates from the
 * CSC and floating feature catalogues the community keeps, each one off until it is turned on.
 */
@Serializable
data class FeatureOverride(
    val key: String,
    val value: String,
    val kind: Int,
    val title: String,
    val enabled: Boolean = false
) {
    companion object {
        /** A key `SemCscFeature` answers, such as `CscFeature_VoiceCall_ConfigRecording`. */
        const val CSC = 0

        /** A key `SemFloatingFeature` answers, such as `SEC_FLOATING_FEATURE_LCD_CONFIG_AOD_FULLSCREEN`. */
        const val FLOATING_FEATURE = 1

        /** What every key of either table starts with. */
        private const val CSC_PREFIX = "CscFeature_"
        private const val FLOATING_PREFIX = "SEC_FLOATING_FEATURE_"

        /** The table a key belongs to, taken from its name. */
        fun kindOf(key: String): Int =
            if (key.startsWith(CSC_PREFIX)) CSC else FLOATING_FEATURE

        /**
         * The gates offered by the app: the catalogue below and the gates the device itself
         * carries, each one off until it is turned on.
         */
        fun presets(): List<FeatureOverride> = catalogue()

        /**
         * The gates collected from the CSC and the floating feature catalogues.
         *
         * The gates a device carries are not written down here: they are read from its own files at
         * run time, since a list that aimed to cover every device would be wrong on all of them.
         */
        private fun catalogue(): List<FeatureOverride> = listOf(
            csc("CscFeature_Calendar_EnableLunar", "TRUE", "Lunar dates in Calendar"),
            csc(
                "CscFeature_Calendar_EnableLocalHolidayDisplay",
                "CHINA",
                "Local holidays in Calendar"
            ),
            csc("CscFeature_Weather_ConfigCpType", "CMA", "Weather provider"),
            csc(
                "CscFeature_Setting_SupportRealTimeNetworkSpeed",
                "TRUE",
                "Real time network speed"
            ),
            csc("CscFeature_VoiceCall_ConfigRecording", "RecordingAllowed", "Voice call recording"),
            csc(
                "CscFeature_Setting_EnableMenuBlockCallMsg",
                "TRUE",
                "Block calls and messages menu"
            ),
            csc("CscFeature_Contact_ConfigForYellowPage", "SHOW", "Yellow pages"),
            csc(
                "CscFeature_Setting_EnableHwVersionDisplay",
                "TRUE",
                "Hardware version in About phone"
            ),
            csc(
                "CscFeature_Setting_SupportPreInstallerShowPreloadAppList",
                "TRUE",
                "Preinstalled app list"
            ),
            csc("CscFeature_Clock_EnableAutoPowerOnOffMenu", "TRUE", "Auto power on/off menu"),
            csc(
                "CscFeature_Clock_SupportAlarmOptionMenuForWorkingDay",
                "TRUE",
                "Working day alarm option"
            ),
            csc(
                "CscFeature_Audio_SupportSafeMediaVolumeDialog",
                "TRUE",
                "Safe media volume warning"
            ),
            csc(
                "CscFeature_SystemUI_SupportDataUsageViewOnQuickPanel",
                "TRUE",
                "Data usage on the quick panel"
            ),
            csc(
                "CscFeature_SystemUI_SupportRecentAppProtection",
                "TRUE",
                "Protect recently used apps"
            ),
            csc(
                "CscFeature_SystemUI_SupportAssistanceAppChooser",
                "TRUE",
                "Choose the default assistant"
            ),
            csc(
                "CscFeature_SystemUI_ConfigOpBranding5GIcon",
                "5GAvailable,RRCStateCheck,UseDisplayTimer",
                "5G icon branding"
            ),
            csc(
                "CscFeature_SystemUI_ConfigOverrideDataIcon",
                "LTE",
                "Override the data icon"
            ),
            csc(
                "CscFeature_Contact_EnableMultiplePinyinSearch",
                "TRUE",
                "Pinyin search in Contacts"
            ),
            csc("CscFeature_Contact_EnablePuzzySearch", "CHNGSM", "Fuzzy search in Contacts"),
            csc("CscFeature_Contact_UseUniNameField", "TRUE", "Unified name field in Contacts"),
            csc("CscFeature_Calculator_ConfigConverter", "CHN", "Chinese units in Calculator"),
            csc("CscFeature_RIL_CallerIdMatchingDigit", "11", "Caller ID matching digits"),
            csc(
                "CscFeature_VoiceCall_ConfigOpStyleForBixbyTextCall",
                "true,zh-CN,default_on,consent_com,cs_cn",
                "Bixby text call"
            ),
            csc(
                "CscFeature_VoiceCall_ConfigOpStyleForLiveTranslate",
                "true,zh-CN,en-US,default_on",
                "Live call translation"
            ),
            csc("CscFeature_Common_EnableVIPMode", "TRUE", "VIP mode"),
            csc(
                "CscFeature_Common_ConfigDynamicLockScreenPolicy",
                "MagazineLockScreen",
                "Magazine lock screen"
            ),
            csc(
                "CscFeature_Common_SupportGmsAlarmManager",
                "FALSE",
                "Turn off the GMS alarm manager"
            ),
            csc(
                "CscFeature_SmartManager_ConfigSubFeatures",
                "appdatacleaner|trafficmanager_auto|fake_base_station|rootbadgefeature|badappnoti|trafficmanager|applock|paymentsafety|riskcontrol|newdataplan",
                "Smart Manager features"
            ),
            csc(
                "CscFeature_SmartManager_DisableAntiMalware",
                "TRUE",
                "Turn off Smart Manager anti-malware"
            ),
            csc("CscFeature_Common_SupportPrivateMode", "TRUE", "Private mode"),
            csc("CscFeature_Gallery_SupportAliveZoom", "TRUE", "Gallery alive zoom"),
            csc(
                "CscFeature_Sip_ConfigAlivePrediction",
                "multilingual",
                "Multilingual keyboard suggestions"
            ),
            csc(
                "CscFeature_Camera_ShutterSoundMenu",
                "TRUE",
                "Camera shutter sound menu (no effect on Android 14 and later)"
            ),
            floating(
                "SEC_FLOATING_FEATURE_COMMON_CONFIG_MULTIWINDOW_TRAY",
                "TRUE",
                "Multiwindow tray"
            ),
            floating(
                "SEC_FLOATING_FEATURE_LAUNCHER_CONFIG_ANIMATION_TYPE",
                "HighEnd",
                "Flagship launcher animations"
            ),
            floating(
                "SEC_FLOATING_FEATURE_COMMON_SUPPORT_HIGH_PERFORMANCE_MODE",
                "TRUE",
                "High performance mode"
            ),
            floating(
                "SEC_FLOATING_FEATURE_SYSTEM_SUPPORT_ENHANCED_CPU_RESPONSIVENESS",
                "TRUE",
                "Enhanced CPU responsiveness"
            ),
            floating(
                "SEC_FLOATING_FEATURE_SYSTEM_SUPPORT_ENHANCED_PROCESSING",
                "TRUE",
                "Enhanced processing"
            ),
            floating(
                "SEC_FLOATING_FEATURE_SYSTEM_SUPPORT_LOW_HEAT_MODE",
                "TRUE",
                "Low heat mode"
            ),
            floating(
                "SEC_FLOATING_FEATURE_BATTERY_SUPPORT_BSOH_SETTINGS",
                "TRUE",
                "Battery health in Settings"
            ),
            floating(
                "SEC_FLOATING_FEATURE_BATTERY_SUPPORT_BSOH_GALAXYDIAGNOSTICS",
                "TRUE",
                "Battery health diagnostics"
            ),
            floating(
                "SEC_FLOATING_FEATURE_BATTERY_SUPPORT_PASS_THROUGH",
                "TRUE",
                "Pass through charging"
            ),
            floating(
                "SEC_FLOATING_FEATURE_BATTERY_SUPPORT_WIRELESS_TX",
                "TRUE",
                "Wireless power sharing"
            ),
            floating(
                "SEC_FLOATING_FEATURE_COMMON_CONFIG_DEX_MODE",
                "standalone,dual,wireless",
                "DeX modes"
            ),
            floating(
                "SEC_FLOATING_FEATURE_COMMON_SUPPORT_BIXBY_TOUCH",
                "TRUE",
                "Bixby Touch"
            ),
            floating(
                "SEC_FLOATING_FEATURE_COMMON_SUPPORT_SAMSUNG_MARKETING_INFO",
                "FALSE",
                "Turn off Samsung marketing information"
            ),
            floating(
                "SEC_FLOATING_FEATURE_WLAN_SUPPORT_SECURE_WIFI",
                "FALSE",
                "Turn off Secure Wi-Fi"
            ),
            floating(
                "SEC_FLOATING_FEATURE_AUDIO_SUPPORT_MICMODE_QUICK_PANEL",
                "TRUE",
                "Microphone mode on the quick panel"
            ),
            floating("SEC_FLOATING_FEATURE_AUDIO_SUPPORT_VOICE_CHANGER", "TRUE", "Voice changer"),
            floating("SEC_FLOATING_FEATURE_AUDIO_SUPPORT_BT_RECORDING", "TRUE", "Bluetooth recording"),
            floating(
                "SEC_FLOATING_FEATURE_AUDIO_SUPPORT_RECORDING_POPUP",
                "TRUE",
                "Recording popup"
            ),
            floating(
                "SEC_FLOATING_FEATURE_SYSTEMUI_SUPPORT_BRIEF_NOTIFICATION",
                "TRUE",
                "Brief notification style"
            ),
            floating(
                "SEC_FLOATING_FEATURE_LCD_SUPPORT_EXTRA_BRIGHTNESS",
                "TRUE",
                "Extra brightness (outdoor mode)"
            ),
            floating(
                "SEC_FLOATING_FEATURE_LCD_CONFIG_AOD_FULLSCREEN",
                "1",
                "Full screen always on display"
            ),
            floating(
                "SEC_FLOATING_FEATURE_GRAPHICS_SUPPORT_TOUCH_FAST_RESPONSE",
                "TRUE",
                "High touch sampling rate"
            ),
            floating(
                "SEC_FLOATING_FEATURE_SETTINGS_SUPPORT_AUTO_POWER_ON_OFF",
                "TRUE",
                "Auto power on/off"
            ),
            floating(
                "SEC_FLOATING_FEATURE_SYSTEM_SUPPORT_SCREEN_CURTAIN",
                "TRUE",
                "Screen curtain"
            ),
            floating(
                "SEC_FLOATING_FEATURE_FRAMEWORK_SUPPORT_SMART_SUGGESTIONS_WIDGET",
                "TRUE",
                "Smart suggestions widget"
            ),
            floating(
                "SEC_FLOATING_FEATURE_GENAI_SUPPORT_OFFLINE_LANGUAGEMODEL",
                "TRUE",
                "On-device language model"
            ),
            floating("SEC_FLOATING_FEATURE_COMMON_SUPPORT_CARLIFE", "TRUE", "CarLife"),
        )

        /**
         * Keeps a stored list in step with the presets.
         *
         * A preset supplies the table a key belongs to and its name, so a corrected entry reaches a
         * device that already stored it. What the gate answers and whether it is on are kept from
         * the stored list, so a value changed in the app is not written over on the next start. A
         * key that is not a preset is left untouched, so an entry added by hand survives.
         */
        fun normalize(overrides: List<FeatureOverride>): List<FeatureOverride> {
            val presets = presets()
            val stored = overrides.associateBy { it.key }
            val presetKeys = presets.mapTo(mutableSetOf()) { it.key }

            return presets.map { preset ->
                stored[preset.key]?.let {
                    preset.copy(value = it.value, enabled = it.enabled)
                } ?: preset
            } + overrides.filterNot { it.key in presetKeys }
        }

        private fun csc(key: String, value: String, title: String) =
            FeatureOverride(key, value, CSC, title)

        private fun floating(key: String, value: String, title: String) =
            FeatureOverride(key, value, FLOATING_FEATURE, title)
    }
}
