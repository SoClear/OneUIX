package io.github.soclear.oneuix.ui.category

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.component.ForceStopApp
import io.github.soclear.oneuix.ui.component.SimOperatorItem

@Composable
fun DetailPaneThemeStore(
    uiState: Preference.Other,
    onEvent: (ThemeStoreEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    PackagePane(modifier) {
        ForceStopApp(Package.THEME_STORE)
        SimOperatorItem(
            title = stringResource(R.string.themeStoreRegion_title),
            checked = uiState.setThemeStoreSimOperator,
            value = uiState.themeStoreSimOperator,
            onCheckedChange = { onEvent(ThemeStoreEvent.SetSimOperator(it)) },
            onConfirm = { onEvent(ThemeStoreEvent.SimOperator(it)) },
        )
    }
}

sealed interface ThemeStoreEvent {
    @JvmInline
    value class SetSimOperator(val value: Boolean) : ThemeStoreEvent

    @JvmInline
    value class SimOperator(val value: String) : ThemeStoreEvent
}

fun SettingViewModel.onThemeStoreEvent(event: ThemeStoreEvent) {
    updateData { preference ->
        preference.copy(other = when (event) {
            is ThemeStoreEvent.SetSimOperator -> preference.other.copy(setThemeStoreSimOperator = event.value)
            is ThemeStoreEvent.SimOperator -> preference.other.copy(themeStoreSimOperator = event.value)
        })
    }
}
