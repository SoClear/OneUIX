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
fun DetailPaneSamsungHealth(
    uiState: Preference.Other,
    onEvent: (SamsungHealthEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    PackagePane(modifier) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.health_metrics),
            title = stringResource(id = R.string.hideSamsungHealthDiscover_title),
            checked = uiState.hideSamsungHealthDiscover,
            onCheckedChange = { onEvent(SamsungHealthEvent.HideDiscover(it)) }
        )
    }
}

sealed interface SamsungHealthEvent {
    @JvmInline
    value class HideDiscover(val value: Boolean) : SamsungHealthEvent
}

fun SettingViewModel.onSamsungHealthEvent(event: SamsungHealthEvent) {
    updateData { preference ->
        when (event) {
            is SamsungHealthEvent.HideDiscover -> preference.copy(
                other = preference.other.copy(
                    hideSamsungHealthDiscover = event.value
                )
            )
        }
    }
}
