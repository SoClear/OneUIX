package io.github.soclear.oneuix.ui.category

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
fun DetailPaneBixby(
    uiState: Preference.Bixby,
    onEvent: (BixbyEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SwitchItem(
            title = stringResource(R.string.bixby_offline_title),
            summary = stringResource(R.string.bixby_offline_summary),
            icon = ImageVector.vectorResource(R.drawable.wifi_link_speed),
            checked = uiState.injectModel,
            onCheckedChange = { onEvent(BixbyEvent.InjectModel(it)) },
        )
        SwitchItem(
            title = stringResource(R.string.bixby_custom_wakeup_title),
            summary = stringResource(R.string.bixby_custom_wakeup_summary),
            icon = ImageVector.vectorResource(R.drawable.phone_forwarded),
            checked = uiState.labsMgr,
            onCheckedChange = { onEvent(BixbyEvent.LabsMgr(it)) },
        )
        SwitchItem(
            title = stringResource(R.string.bixby_wwv_bypass_title),
            summary = stringResource(R.string.bixby_wwv_bypass_summary),
            icon = ImageVector.vectorResource(R.drawable.phone_forwarded),
            checked = uiState.wwvBypass,
            onCheckedChange = { onEvent(BixbyEvent.WwvBypass(it)) },
        )
    }
}

sealed interface BixbyEvent {
    @JvmInline value class InjectModel(val value: Boolean) : BixbyEvent
    @JvmInline value class LabsMgr(val value: Boolean) : BixbyEvent
    @JvmInline value class WwvBypass(val value: Boolean) : BixbyEvent
}

fun SettingViewModel.onBixbyEvent(event: BixbyEvent) {
    updateData { preference ->
        when (event) {
            is BixbyEvent.InjectModel -> preference.copy(bixby = preference.bixby.copy(injectModel = event.value))
            is BixbyEvent.LabsMgr -> preference.copy(bixby = preference.bixby.copy(labsMgr = event.value))
            is BixbyEvent.WwvBypass -> preference.copy(bixby = preference.bixby.copy(wwvBypass = event.value))
        }
    }
}
