package io.github.soclear.oneuix.ui.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.ChargingError
import io.github.soclear.oneuix.ui.ChargingUiState
import io.github.soclear.oneuix.ui.component.SwitchItem
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun DetailPaneSettings(
    uiState: Preference.Settings,
    onEvent: (SettingsEvent) -> Unit,
    chargingState: ChargingUiState,
    onChargingRefresh: () -> Unit,
    onRememberChargingLimit: (Boolean) -> Unit,
    onChargingLimitChange: (Int) -> Unit,
    onPauseChargingAtCurrentLevel: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) { onChargingRefresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onChargingRefresh() }
    val charging = chargingState.system
    var selectedLimit by remember(charging?.limit, chargingState.error) {
        mutableIntStateOf(charging?.limit ?: 80)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SwitchItem(
            title = stringResource(id = R.string.showForcePeakRefreshRatePreference_title),
            summary = stringResource(id = R.string.showForcePeakRefreshRatePreference_summary),
            icon = ImageVector.vectorResource(id = R.drawable.logo_dev),
            checked = uiState.showForcePeakRefreshRatePreference,
            onCheckedChange = { onEvent(SettingsEvent.ShowForcePeakRefreshRatePreference(it)) },
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.light_mode),
            title = stringResource(id = R.string.supportOutdoorMode_title),
            checked = uiState.supportOutdoorMode,
            onCheckedChange = { onEvent(SettingsEvent.SupportOutdoorMode(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.battery),
            title = stringResource(id = R.string.showMoreBatteryInfo_title),
            summary = stringResource(id = R.string.showMoreBatteryInfo_summary),
            checked = uiState.showMoreBatteryInfo,
            onCheckedChange = { onEvent(SettingsEvent.ShowMoreBatteryInfo(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.apk_document),
            title = stringResource(id = R.string.showPackageInfo_title),
            summary = stringResource(id = R.string.showPackageInfo_summary),
            checked = uiState.showPackageInfo,
            onCheckedChange = { onEvent(SettingsEvent.ShowPackageInfo(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.wifi_link_speed),
            title = stringResource(id = R.string.showWiFiLinkSpeed_title),
            summary = stringResource(id = R.string.showWiFiLinkSpeed_summary),
            checked = uiState.showWiFiLinkSpeed,
            onCheckedChange = { onEvent(SettingsEvent.ShowWiFiLinkSpeed(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.font),
            title = stringResource(id = R.string.supportAnyFont_title),
            checked = uiState.supportAnyFont,
            onCheckedChange = { onEvent(SettingsEvent.SupportAnyFont(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.power_settings_new),
            title = stringResource(id = R.string.supportAutoPowerOnOff_title),
            summary = stringResource(id = R.string.supportAutoPowerOnOff_summary),
            checked = uiState.supportAutoPowerOnOff,
            onCheckedChange = { onEvent(SettingsEvent.SupportAutoPowerOnOff(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.mobile_screensaver),
            title = stringResource(id = R.string.spoofPhoneStatusAsOfficial_title),
            summary = stringResource(id = R.string.spoofPhoneStatusAsOfficial_summary),
            checked = uiState.spoofPhoneStatusAsOfficial,
            onCheckedChange = { onEvent(SettingsEvent.SpoofPhoneStatusAsOfficial(it)) }
        )
        DividerText(R.string.charging_control_title)
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.sim_card),
            title = stringResource(R.string.remember_charging_limit_title),
            summary = stringResource(R.string.remember_charging_limit_summary),
            checked = charging?.remember == true,
            onCheckedChange = onRememberChargingLimit,
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.charging_limit_title)) },
            supportingContent = {
                Text(stringResource(R.string.charging_limit_summary))
            },
            leadingContent = {
                Icon(ImageVector.vectorResource(R.drawable.battery), contentDescription = null)
            },
            trailingContent = { Text(stringResource(R.string.charging_limit_value, selectedLimit)) },
        )
        Slider(
            value = selectedLimit.toFloat(),
            onValueChange = { selectedLimit = it.roundToInt() },
            valueRange = 20f..100f,
            steps = 79,
            onValueChangeFinished = {
                if (selectedLimit != charging?.limit || charging?.active != true) {
                    onChargingLimitChange(selectedLimit)
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp),
            enabled = charging != null && !chargingState.busy,
        )
        Text(
            text = stringResource(R.string.charging_limit_mode_hint),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (charging != null && !charging.active) {
            Text(
                text = stringResource(R.string.charging_limit_inactive),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.bypass_charging_title)) },
            supportingContent = {
                Text(stringResource(
                    if (charging?.quickPauseEnabled == true) R.string.pause_at_current_level_off
                    else R.string.pause_at_current_level_summary
                ))
            },
            leadingContent = {
                Icon(ImageVector.vectorResource(R.drawable.power_settings_new), contentDescription = null)
            },
            modifier = Modifier.clickable(
                enabled = !chargingState.busy &&
                    (charging?.quickPauseEnabled == true ||
                        (charging?.pluggedIn == true && (charging.batteryLevel ?: 0) >= 20)),
                onClick = onPauseChargingAtCurrentLevel,
            ),
        )
        if (charging != null && (!charging.pluggedIn || (charging.batteryLevel ?: 0) < 20)) {
            Text(
                text = stringResource(R.string.bypass_charging_requires_power),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (chargingState.error != null) {
            Text(
                text = stringResource(
                    when (chargingState.error) {
                        ChargingError.Limit -> R.string.charging_limit_failed
                        ChargingError.Bypass -> R.string.bypass_charging_failed
                        ChargingError.Remember -> R.string.remember_charging_limit_failed
                    }
                ),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

sealed interface SettingsEvent {
    @JvmInline
    value class ShowForcePeakRefreshRatePreference(val value: Boolean) : SettingsEvent

    @JvmInline
    value class SupportOutdoorMode(val value: Boolean) : SettingsEvent

    @JvmInline
    value class ShowMoreBatteryInfo(val value: Boolean) : SettingsEvent

    @JvmInline
    value class ShowPackageInfo(val value: Boolean) : SettingsEvent

    @JvmInline
    value class ShowWiFiLinkSpeed(val value: Boolean) : SettingsEvent

    @JvmInline
    value class SupportAnyFont(val value: Boolean) : SettingsEvent

    @JvmInline
    value class SupportAutoPowerOnOff(val value: Boolean) : SettingsEvent

    @JvmInline
    value class SpoofPhoneStatusAsOfficial(val value: Boolean) : SettingsEvent
}

fun SettingViewModel.onSettingsEvent(event: SettingsEvent) {
    updateData { preference ->
        when (event) {
            is SettingsEvent.ShowForcePeakRefreshRatePreference -> preference.copy(
                settings = preference.settings.copy(
                    showForcePeakRefreshRatePreference = event.value
                )
            )

            is SettingsEvent.SupportOutdoorMode -> preference.copy(
                settings = preference.settings.copy(
                    supportOutdoorMode = event.value
                )
            )

            is SettingsEvent.ShowMoreBatteryInfo -> preference.copy(
                settings = preference.settings.copy(
                    showMoreBatteryInfo = event.value
                )
            )

            is SettingsEvent.ShowPackageInfo -> preference.copy(
                settings = preference.settings.copy(
                    showPackageInfo = event.value
                )
            )

            is SettingsEvent.ShowWiFiLinkSpeed -> preference.copy(
                settings = preference.settings.copy(
                    showWiFiLinkSpeed = event.value
                )
            )

            is SettingsEvent.SupportAnyFont -> preference.copy(
                settings = preference.settings.copy(
                    supportAnyFont = event.value
                )
            )
            is SettingsEvent.SupportAutoPowerOnOff -> preference.copy(
                settings = preference.settings.copy(
                    supportAutoPowerOnOff = event.value
                )
            )

            is SettingsEvent.SpoofPhoneStatusAsOfficial -> preference.copy(
                settings = preference.settings.copy(
                    spoofPhoneStatusAsOfficial = event.value
                )
            )
        }
    }
}
