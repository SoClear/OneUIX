package io.github.soclear.oneuix.ui.category

import android.annotation.SuppressLint
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
import io.github.soclear.oneuix.ui.component.SwitchItem

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
        if (isCHC) {
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.search),
                title = stringResource(id = R.string.customizeBrowserSearchEngine_title),
                summary = stringResource(id = R.string.customizeBrowserSearchEngine_summary),
                checked = uiState.customizeBrowserSearchEngine,
                onCheckedChange = { onEvent(OtherEvent.CustomizeBrowserSearchEngine(it)) }
            )
        }
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.branding_watermark),
            title = stringResource(id = R.string.noAIWatermark_title),
            checked = uiState.noAIWatermark,
            onCheckedChange = { onEvent(OtherEvent.NoAIWatermark(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.person_pin_circle),
            title = stringResource(id = R.string.bypassHealthMonitorCountryCheck_title),
            summary = stringResource(id = R.string.bypassHealthMonitorCountryCheck_summary),
            checked = uiState.bypassHealthMonitorCountryCheck,
            onCheckedChange = { onEvent(OtherEvent.BypassHealthMonitorCountryCheck(it)) }
        )
    }
}

private val isCHC by lazy {
    try {
        @SuppressLint("PrivateApi")
        val systemPropertiesClass = Class.forName("android.os.SystemProperties")
        val getMethod = systemPropertiesClass.getMethod("get", String::class.java)
        getMethod(null, "ro.csc.sales_code") == "CHC"
    } catch (_: Throwable) {
        false
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
    value class CustomizeBrowserSearchEngine(val value: Boolean) : OtherEvent

    @JvmInline
    value class NoAIWatermark(val value: Boolean) : OtherEvent

    @JvmInline
    value class BypassHealthMonitorCountryCheck(val value: Boolean) : OtherEvent
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

            is OtherEvent.CustomizeBrowserSearchEngine -> preference.copy(
                other = preference.other.copy(
                    customizeBrowserSearchEngine = event.value
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
        }
    }
}
