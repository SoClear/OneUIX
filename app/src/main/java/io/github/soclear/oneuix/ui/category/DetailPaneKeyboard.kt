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
fun DetailPaneKeyboard(
    uiState: Preference.Other,
    onEvent: (KeyboardEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    PackagePane(modifier) {
        SwitchItem(
            icon = ImageVector.vectorResource(R.drawable.language_us),
            title = stringResource(R.string.unlockKeyboardShuangpinLayout_title),
            summary = stringResource(R.string.unlockKeyboardShuangpinLayout_summary),
            checked = uiState.unlockKeyboardShuangpinLayout,
            onCheckedChange = { onEvent(KeyboardEvent.UnlockShuangpinLayout(it)) }
        )
    }
}

sealed interface KeyboardEvent {
    @JvmInline
    value class UnlockShuangpinLayout(val value: Boolean) : KeyboardEvent
}

fun SettingViewModel.onKeyboardEvent(event: KeyboardEvent) {
    updateData { preference ->
        when (event) {
            is KeyboardEvent.UnlockShuangpinLayout -> preference.copy(
                other = preference.other.copy(unlockKeyboardShuangpinLayout = event.value)
            )
        }
    }
}
