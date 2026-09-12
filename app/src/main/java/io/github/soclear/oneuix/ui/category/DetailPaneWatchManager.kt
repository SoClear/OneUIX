package io.github.soclear.oneuix.ui.category

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.component.SelectItem
import io.github.soclear.oneuix.ui.component.SwitchItem

private const val WATCH_PAIRING_MODE_CN = 1

@Composable
fun DetailPaneWatchManager(
    uiState: Preference.Other,
    onEvent: (WatchManagerEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    PackagePane(modifier) {
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
            onSelectedIndexChange = { onEvent(WatchManagerEvent.WatchPairingConnectionMode(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.lock_open),
            title = stringResource(id = R.string.bypassWatchPairingRegionCheck_title),
            summary = stringResource(id = R.string.bypassWatchPairingRegionCheck_summary),
            checked = uiState.bypassWatchPairingRegionCheck,
            onCheckedChange = { onEvent(WatchManagerEvent.BypassWatchPairingRegionCheck(it)) }
        )
        if (uiState.watchPairingConnectionMode == WATCH_PAIRING_MODE_CN) {
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.google_play),
                title = stringResource(id = R.string.supplementChinaWearOsGms_title),
                summary = stringResource(id = R.string.supplementChinaWearOsGms_summary),
                checked = uiState.supplementChinaWearOsGms,
                onCheckedChange = { onEvent(WatchManagerEvent.SupplementChinaWearOsGms(it)) }
            )
        }
    }
}

sealed interface WatchManagerEvent {
    @JvmInline
    value class BypassWatchPairingRegionCheck(val value: Boolean) : WatchManagerEvent

    @JvmInline
    value class WatchPairingConnectionMode(val value: Int) : WatchManagerEvent

    @JvmInline
    value class SupplementChinaWearOsGms(val value: Boolean) : WatchManagerEvent
}

fun SettingViewModel.onWatchManagerEvent(event: WatchManagerEvent) {
    updateData { preference ->
        when (event) {
            is WatchManagerEvent.BypassWatchPairingRegionCheck -> preference.copy(
                other = preference.other.copy(
                    bypassWatchPairingRegionCheck = event.value
                )
            )

            is WatchManagerEvent.WatchPairingConnectionMode -> preference.copy(
                other = preference.other.copy(
                    watchPairingConnectionMode = event.value,
                    supplementChinaWearOsGms = if (event.value == WATCH_PAIRING_MODE_CN) {
                        preference.other.supplementChinaWearOsGms
                    } else {
                        false
                    }
                )
            )

            is WatchManagerEvent.SupplementChinaWearOsGms -> preference.copy(
                other = preference.other.copy(
                    supplementChinaWearOsGms = event.value
                )
            )
        }
    }
}
