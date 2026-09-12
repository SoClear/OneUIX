package io.github.soclear.oneuix.ui.category

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.component.SwitchItem

@Composable
fun DetailPaneHealthMonitor(
    uiState: Preference.Other,
    onEvent: (HealthMonitorEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    PackagePane(modifier) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.health_metrics),
            title = stringResource(id = R.string.bypassHealthMonitorCountryCheck_title),
            summary = stringResource(id = R.string.bypassHealthMonitorCountryCheck_summary),
            checked = uiState.bypassHealthMonitorCountryCheck,
            onCheckedChange = { onEvent(HealthMonitorEvent.BypassHealthMonitorCountryCheck(it)) }
        )
    }
}

sealed interface HealthMonitorEvent {
    @JvmInline
    value class BypassHealthMonitorCountryCheck(val value: Boolean) : HealthMonitorEvent
}

fun SettingViewModel.onHealthMonitorEvent(event: HealthMonitorEvent) {
    updateData { preference ->
        when (event) {
            is HealthMonitorEvent.BypassHealthMonitorCountryCheck -> preference.copy(
                other = preference.other.copy(
                    bypassHealthMonitorCountryCheck = event.value
                )
            )
        }
    }
}
