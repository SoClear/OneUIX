package io.github.soclear.oneuix.ui.category

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.component.ForceStopApp
import io.github.soclear.oneuix.ui.component.SwitchItem

@Composable
fun DetailPaneBixby(
    uiState: Preference.Other,
    onEvent: (BixbyEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    PackagePane(modifier) {
        ForceStopApp(Package.BIXBY_AGENT)
        SwitchItem(
            icon = ImageVector.vectorResource(R.drawable.wifi_link_speed),
            title = stringResource(R.string.unlockBixbyOfflineProcessing_title),
            summary = stringResource(R.string.unlockBixbyOfflineProcessing_summary),
            checked = uiState.unlockBixbyOfflineProcessing,
            onCheckedChange = { onEvent(BixbyEvent.UnlockOfflineProcessing(it)) },
        )
        SwitchItem(
            icon = ImageVector.vectorResource(R.drawable.phone_forwarded),
            title = stringResource(R.string.supportBixbyCustomWakeup_title),
            summary = stringResource(R.string.supportBixbyCustomWakeup_summary),
            checked = uiState.supportBixbyCustomWakeup,
            onCheckedChange = { onEvent(BixbyEvent.SupportCustomWakeup(it)) },
        )
        SwitchItem(
            icon = ImageVector.vectorResource(R.drawable.lock_open),
            title = stringResource(R.string.bypassBixbyWakeWordRestrictions_title),
            summary = stringResource(R.string.bypassBixbyWakeWordRestrictions_summary),
            checked = uiState.bypassBixbyWakeWordRestrictions,
            onCheckedChange = { onEvent(BixbyEvent.BypassWakeWordRestrictions(it)) },
        )
    }
}

sealed interface BixbyEvent {
    @JvmInline
    value class UnlockOfflineProcessing(val value: Boolean) : BixbyEvent
    @JvmInline
    value class SupportCustomWakeup(val value: Boolean) : BixbyEvent
    @JvmInline
    value class BypassWakeWordRestrictions(val value: Boolean) : BixbyEvent
}

fun SettingViewModel.onBixbyEvent(event: BixbyEvent) {
    updateData { preference ->
        preference.copy(other = when (event) {
            is BixbyEvent.UnlockOfflineProcessing -> preference.other.copy(unlockBixbyOfflineProcessing = event.value)
            is BixbyEvent.SupportCustomWakeup -> preference.other.copy(supportBixbyCustomWakeup = event.value)
            is BixbyEvent.BypassWakeWordRestrictions -> preference.other.copy(bypassBixbyWakeWordRestrictions = event.value)
        })
    }
}
