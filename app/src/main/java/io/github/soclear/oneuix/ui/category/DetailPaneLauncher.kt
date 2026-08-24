package io.github.soclear.oneuix.ui.category

import android.os.Build
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
fun DetailPaneLauncher(
    uiState: Preference.Other,
    onEvent: (LauncherEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    PackagePane(modifier) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.memory),
                title = stringResource(id = R.string.showMemoryUsageInRecents_title),
                checked = uiState.showMemoryUsageInRecents,
                onCheckedChange = { onEvent(LauncherEvent.ShowMemoryUsageInRecents(it)) }
            )
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.apps),
                title = stringResource(id = R.string.hideRecentsCloseAllButton_title),
                summary = stringResource(id = R.string.hideRecentsCloseAllButton_summary),
                checked = uiState.hideRecentsCloseAllButton,
                onCheckedChange = { onEvent(LauncherEvent.HideRecentsCloseAllButton(it)) }
            )
        }
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.apps),
            title = stringResource(id = R.string.hideAppsSearchBar_title),
            checked = uiState.hideAppsSearchBar,
            onCheckedChange = { onEvent(LauncherEvent.HideAppsSearchBar(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.position_bottom_right),
            title = stringResource(id = R.string.removeShortcutBadge_title),
            summary = stringResource(id = R.string.removeShortcutBadge_summary),
            checked = uiState.removeShortcutBadge,
            onCheckedChange = { onEvent(LauncherEvent.RemoveShortcutBadge(it)) }
        )
    }
}

sealed interface LauncherEvent {
    @JvmInline
    value class ShowMemoryUsageInRecents(val value: Boolean) : LauncherEvent

    @JvmInline
    value class HideRecentsCloseAllButton(val value: Boolean) : LauncherEvent

    @JvmInline
    value class HideAppsSearchBar(val value: Boolean) : LauncherEvent

    @JvmInline
    value class RemoveShortcutBadge(val value: Boolean) : LauncherEvent
}

fun SettingViewModel.onLauncherEvent(event: LauncherEvent) {
    updateData { preference ->
        when (event) {
            is LauncherEvent.ShowMemoryUsageInRecents -> preference.copy(
                other = preference.other.copy(
                    showMemoryUsageInRecents = event.value
                )
            )

            is LauncherEvent.HideRecentsCloseAllButton -> preference.copy(
                other = preference.other.copy(
                    hideRecentsCloseAllButton = event.value
                )
            )

            is LauncherEvent.HideAppsSearchBar -> preference.copy(
                other = preference.other.copy(
                    hideAppsSearchBar = event.value
                )
            )

            is LauncherEvent.RemoveShortcutBadge -> preference.copy(
                other = preference.other.copy(
                    removeShortcutBadge = event.value
                )
            )
        }
    }
}
