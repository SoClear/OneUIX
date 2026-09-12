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
fun DetailPaneSPen(
    uiState: Preference.Other,
    onEvent: (SPenEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    PackagePane(modifier) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.spen),
            title = stringResource(id = R.string.useSPenGoogleTranslate_title),
            summary = stringResource(id = R.string.useSPenGoogleTranslate_summary),
            checked = uiState.useSPenGoogleTranslate,
            onCheckedChange = { onEvent(SPenEvent.UseSPenGoogleTranslate(it)) }
        )
    }
}

sealed interface SPenEvent {
    @JvmInline
    value class UseSPenGoogleTranslate(val value: Boolean) : SPenEvent
}

fun SettingViewModel.onSPenEvent(event: SPenEvent) {
    updateData { preference ->
        when (event) {
            is SPenEvent.UseSPenGoogleTranslate -> preference.copy(
                other = preference.other.copy(
                    useSPenGoogleTranslate = event.value
                )
            )
        }
    }
}
