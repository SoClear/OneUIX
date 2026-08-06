package io.github.soclear.oneuix.ui.category

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.data.Preference
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.component.SelectItem
import io.github.soclear.oneuix.ui.component.SwitchItem

private const val WATCH_PAIRING_MODE_CN = 1

@Composable
fun DetailPaneOther(
    uiState: Preference.Other,
    onEvent: (OtherEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.ad_off),
            title = stringResource(id = R.string.blockGalaxyStoreAds_title),
            summary = stringResource(id = R.string.blockGalaxyStoreAds_summary),
            checked = uiState.blockGalaxyStoreAds,
            onCheckedChange = { onEvent(OtherEvent.BlockGalaxyStoreAds(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.apk_document),
            title = stringResource(id = R.string.makeAllUserAppsAvailable_title),
            checked = uiState.makeAllUserAppsAvailable,
            onCheckedChange = { onEvent(OtherEvent.MakeAllUserAppsAvailable(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.partly_cloudy_day),
            title = stringResource(id = R.string.setWeatherProviderCN_title),
            checked = uiState.setWeatherProviderCN,
            onCheckedChange = { onEvent(OtherEvent.SetWeatherProviderCN(it)) }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.memory),
                title = stringResource(id = R.string.showMemoryUsageInRecents_title),
                checked = uiState.showMemoryUsageInRecents,
                onCheckedChange = { onEvent(OtherEvent.ShowMemoryUsageInRecents(it)) }
            )
        }
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.fast_forward),
            title = stringResource(id = R.string.showMorePlaybackSpeeds_title),
            summary = stringResource(id = R.string.showMorePlaybackSpeeds_summary),
            checked = uiState.showMorePlaybackSpeeds,
            onCheckedChange = { onEvent(OtherEvent.ShowMorePlaybackSpeeds(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.tab_move),
            title = stringResource(id = R.string.redirect_custom_tab_title),
            summary = stringResource(id = R.string.redirect_custom_tab_summary),
            checked = uiState.redirectCustomTab,
            onCheckedChange = { onEvent(OtherEvent.RedirectCustomTab(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.photo_library),
            title = stringResource(id = R.string.supportAllGallerySettings_title),
            summary = stringResource(id = R.string.supportAllGallerySettings_summary),
            checked = uiState.supportAllGallerySettings,
            onCheckedChange = { onEvent(OtherEvent.SupportAllGallerySettings(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.notes),
            title = stringResource(id = R.string.supportAllNotesFeatures_title),
            summary = stringResource(id = R.string.supportAllNotesFeatures_summary),
            checked = uiState.supportAllNotesFeatures,
            onCheckedChange = { onEvent(OtherEvent.SupportAllNotesFeatures(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.today),
            title = stringResource(id = R.string.enableChineseHolidayDisplay_title),
            checked = uiState.enableChineseHolidayDisplay,
            onCheckedChange = { onEvent(OtherEvent.EnableChineseHolidayDisplay(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.sms),
            title = stringResource(id = R.string.supportBlockMessage_title),
            checked = uiState.supportBlockMessage,
            onCheckedChange = { onEvent(OtherEvent.SupportBlockMessage(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.format_paint),
            title = stringResource(id = R.string.setThemeTrialNeverExpired_title),
            checked = uiState.setThemeTrialNeverExpired,
            onCheckedChange = { onEvent(OtherEvent.SetThemeTrialNeverExpired(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.language_us),
            title = stringResource(id = R.string.spoofBrowserCountryCodeToUS_title),
            summary = stringResource(id = R.string.spoofBrowserCountryCodeToUS_summary),
            checked = uiState.spoofBrowserCountryCodeToUS,
            onCheckedChange = { onEvent(OtherEvent.SpoofBrowserCountryCodeToUS(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.branding_watermark),
            title = stringResource(id = R.string.noAIWatermark_title),
            summary = stringResource(id = R.string.noAIWatermark_summary),
            checked = uiState.noAIWatermark,
            onCheckedChange = { onEvent(OtherEvent.NoAIWatermark(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.health_metrics),
            title = stringResource(id = R.string.bypassHealthMonitorCountryCheck_title),
            summary = stringResource(id = R.string.bypassHealthMonitorCountryCheck_summary),
            checked = uiState.bypassHealthMonitorCountryCheck,
            onCheckedChange = { onEvent(OtherEvent.BypassHealthMonitorCountryCheck(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.spen),
            title = stringResource(id = R.string.useSPenGoogleTranslate_title),
            summary = stringResource(id = R.string.useSPenGoogleTranslate_summary),
            checked = uiState.useSPenGoogleTranslate,
            onCheckedChange = { onEvent(OtherEvent.UseSPenGoogleTranslate(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.wifi_link_speed),
            title = stringResource(id = R.string.unlockBixbyOfflineProcessing_title),
            summary = stringResource(id = R.string.unlockBixbyOfflineProcessing_summary),
            checked = uiState.unlockBixbyOfflineProcessing,
            onCheckedChange = { onEvent(OtherEvent.UnlockBixbyOfflineProcessing(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.phone_forwarded),
            title = stringResource(id = R.string.supportBixbyCustomWakeup_title),
            summary = stringResource(id = R.string.supportBixbyCustomWakeup_summary),
            checked = uiState.supportBixbyCustomWakeup,
            onCheckedChange = { onEvent(OtherEvent.SupportBixbyCustomWakeup(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.lock_open),
            title = stringResource(id = R.string.bypassBixbyWakeWordRestrictions_title),
            summary = stringResource(id = R.string.bypassBixbyWakeWordRestrictions_summary),
            checked = uiState.bypassBixbyWakeWordRestrictions,
            onCheckedChange = { onEvent(OtherEvent.BypassBixbyWakeWordRestrictions(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.apps),
            title = stringResource(id = R.string.hideAppsSearchBar_title),
            checked = uiState.hideAppsSearchBar,
            onCheckedChange = { onEvent(OtherEvent.HideAppsSearchBar(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.position_bottom_right),
            title = stringResource(id = R.string.removeShortcutBadge_title),
            summary = stringResource(id = R.string.removeShortcutBadge_summary),
            checked = uiState.removeShortcutBadge,
            onCheckedChange = { onEvent(OtherEvent.RemoveShortcutBadge(it)) }
        )
        SelectItem(
            icon = ImageVector.vectorResource(id = R.drawable.watch_pairing),
            title = stringResource(id = R.string.watchPairing_connectionMode_title),
            summary = stringResource(id = R.string.watchPairing_connectionMode_summary),
            entries = listOf(
                stringResource(id = R.string.watchPairing_mode_none),
                stringResource(id = R.string.watchPairing_mode_wearos_cn),
                stringResource(id = R.string.watchPairing_mode_wearos_global)
            ),
            selectedIndex = uiState.watchPairingConnectionMode,
            onSelectedIndexChange = { onEvent(OtherEvent.WatchPairingConnectionMode(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.lock_open),
            title = stringResource(id = R.string.bypassWatchPairingRegionCheck_title),
            summary = stringResource(id = R.string.bypassWatchPairingRegionCheck_summary),
            checked = uiState.bypassWatchPairingRegionCheck,
            onCheckedChange = { onEvent(OtherEvent.BypassWatchPairingRegionCheck(it)) }
        )
        if (uiState.watchPairingConnectionMode == WATCH_PAIRING_MODE_CN) {
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.google_play),
                title = stringResource(id = R.string.supplementChinaWearOsGms_title),
                summary = stringResource(id = R.string.supplementChinaWearOsGms_summary),
                checked = uiState.supplementChinaWearOsGms,
                onCheckedChange = { onEvent(OtherEvent.SupplementChinaWearOsGms(it)) }
            )
        }
    }
}


sealed interface OtherEvent {
    @JvmInline
    value class BlockGalaxyStoreAds(val value: Boolean) : OtherEvent

    @JvmInline
    value class MakeAllUserAppsAvailable(val value: Boolean) : OtherEvent

    @JvmInline
    value class SetWeatherProviderCN(val value: Boolean) : OtherEvent

    @JvmInline
    value class ShowMemoryUsageInRecents(val value: Boolean) : OtherEvent

    @JvmInline
    value class ShowMorePlaybackSpeeds(val value: Boolean) : OtherEvent

    @JvmInline
    value class RedirectCustomTab(val value: Boolean) : OtherEvent

    @JvmInline
    value class SupportAllGallerySettings(val value: Boolean) : OtherEvent

    @JvmInline
    value class SupportAllNotesFeatures(val value: Boolean) : OtherEvent

    @JvmInline
    value class EnableChineseHolidayDisplay(val value: Boolean) : OtherEvent

    @JvmInline
    value class SupportBlockMessage(val value: Boolean) : OtherEvent

    @JvmInline
    value class SetThemeTrialNeverExpired(val value: Boolean) : OtherEvent

    @JvmInline
    value class SpoofBrowserCountryCodeToUS(val value: Boolean) : OtherEvent

    @JvmInline
    value class NoAIWatermark(val value: Boolean) : OtherEvent

    @JvmInline
    value class BypassHealthMonitorCountryCheck(val value: Boolean) : OtherEvent

    @JvmInline
    value class UseSPenGoogleTranslate(val value: Boolean) : OtherEvent

    @JvmInline
    value class UnlockBixbyOfflineProcessing(val value: Boolean) : OtherEvent

    @JvmInline
    value class SupportBixbyCustomWakeup(val value: Boolean) : OtherEvent

    @JvmInline
    value class BypassBixbyWakeWordRestrictions(val value: Boolean) : OtherEvent

    @JvmInline
    value class HideAppsSearchBar(val value: Boolean) : OtherEvent

    @JvmInline
    value class RemoveShortcutBadge(val value: Boolean) : OtherEvent

    @JvmInline
    value class BypassWatchPairingRegionCheck(val value: Boolean) : OtherEvent

    @JvmInline
    value class WatchPairingConnectionMode(val value: Int) : OtherEvent

    @JvmInline
    value class SupplementChinaWearOsGms(val value: Boolean) : OtherEvent
}

fun SettingViewModel.onOtherEvent(event: OtherEvent) {
    updateData { preference ->
        when (event) {
            is OtherEvent.BlockGalaxyStoreAds -> preference.copy(
                other = preference.other.copy(
                    blockGalaxyStoreAds = event.value
                )
            )

            is OtherEvent.MakeAllUserAppsAvailable -> preference.copy(
                other = preference.other.copy(
                    makeAllUserAppsAvailable = event.value
                )
            )

            is OtherEvent.SetWeatherProviderCN -> preference.copy(
                other = preference.other.copy(
                    setWeatherProviderCN = event.value
                )
            )

            is OtherEvent.ShowMemoryUsageInRecents -> preference.copy(
                other = preference.other.copy(
                    showMemoryUsageInRecents = event.value
                )
            )

            is OtherEvent.ShowMorePlaybackSpeeds -> preference.copy(
                other = preference.other.copy(
                    showMorePlaybackSpeeds = event.value
                )
            )

            is OtherEvent.RedirectCustomTab -> preference.copy(
                other = preference.other.copy(
                    redirectCustomTab = event.value
                )
            )

            is OtherEvent.SupportAllGallerySettings -> preference.copy(
                other = preference.other.copy(
                    supportAllGallerySettings = event.value
                )
            )

            is OtherEvent.SupportAllNotesFeatures -> preference.copy(
                other = preference.other.copy(
                    supportAllNotesFeatures = event.value
                )
            )

            is OtherEvent.EnableChineseHolidayDisplay -> preference.copy(
                other = preference.other.copy(
                    enableChineseHolidayDisplay = event.value
                )
            )

            is OtherEvent.SupportBlockMessage -> preference.copy(
                other = preference.other.copy(
                    supportBlockMessage = event.value
                )
            )

            is OtherEvent.SetThemeTrialNeverExpired -> preference.copy(
                other = preference.other.copy(
                    setThemeTrialNeverExpired = event.value
                )
            )

            is OtherEvent.SpoofBrowserCountryCodeToUS -> preference.copy(
                other = preference.other.copy(
                    spoofBrowserCountryCodeToUS = event.value
                )
            )

            is OtherEvent.NoAIWatermark -> preference.copy(
                other = preference.other.copy(
                    noAIWatermark = event.value
                )
            )

            is OtherEvent.BypassHealthMonitorCountryCheck -> preference.copy(
                other = preference.other.copy(
                    bypassHealthMonitorCountryCheck = event.value
                )
            )

            is OtherEvent.UseSPenGoogleTranslate -> preference.copy(
                other = preference.other.copy(
                    useSPenGoogleTranslate = event.value
                )
            )

            is OtherEvent.UnlockBixbyOfflineProcessing -> preference.copy(
                other = preference.other.copy(
                    unlockBixbyOfflineProcessing = event.value
                )
            )

            is OtherEvent.SupportBixbyCustomWakeup -> preference.copy(
                other = preference.other.copy(
                    supportBixbyCustomWakeup = event.value
                )
            )

            is OtherEvent.BypassBixbyWakeWordRestrictions -> preference.copy(
                other = preference.other.copy(
                    bypassBixbyWakeWordRestrictions = event.value
                )
            )

            is OtherEvent.HideAppsSearchBar -> preference.copy(
                other = preference.other.copy(
                    hideAppsSearchBar = event.value
                )
            )

            is OtherEvent.RemoveShortcutBadge -> preference.copy(
                other = preference.other.copy(
                    removeShortcutBadge = event.value
                )
            )

            is OtherEvent.BypassWatchPairingRegionCheck -> preference.copy(
                other = preference.other.copy(
                    bypassWatchPairingRegionCheck = event.value
                )
            )

            is OtherEvent.WatchPairingConnectionMode -> preference.copy(
                other = preference.other.copy(
                    watchPairingConnectionMode = event.value,
                    supplementChinaWearOsGms = if (event.value == WATCH_PAIRING_MODE_CN) {
                        preference.other.supplementChinaWearOsGms
                    } else {
                        false
                    }
                )
            )

            is OtherEvent.SupplementChinaWearOsGms -> preference.copy(
                other = preference.other.copy(
                    supplementChinaWearOsGms = event.value
                )
            )
        }
    }
}
