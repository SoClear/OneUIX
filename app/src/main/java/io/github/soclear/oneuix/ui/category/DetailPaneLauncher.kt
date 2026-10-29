package io.github.soclear.oneuix.ui.category

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
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
        }
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.tile_medium),
            title = stringResource(id = R.string.recentsGridThreeRows_title),
            summary = stringResource(id = R.string.recentsGridThreeRows_summary),
            checked = uiState.recentsGridThreeRows,
            onCheckedChange = { onEvent(LauncherEvent.RecentsGridThreeRows(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.block),
            title = stringResource(id = R.string.hideRecentsCloseAllButton_title),
            checked = uiState.hideRecentsCloseAllButton,
            onCheckedChange = { onEvent(LauncherEvent.HideRecentsCloseAllButton(it)) }
        )
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
        Column {
            var padding by remember(uiState.dockBottomPaddingDp) {
                mutableFloatStateOf(
                    uiState.dockBottomPaddingDp.takeIf { it.isFinite() }?.coerceIn(0f, 100f) ?: 0f
                )
            }
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.padding),
                title = stringResource(R.string.modifyDockBottomPadding_title),
                summary = stringResource(R.string.modifyDockBottomPadding_summary, padding),
                checked = uiState.modifyDockBottomPadding,
                onCheckedChange = { onEvent(LauncherEvent.ModifyDockBottomPadding(it)) }
            )
            AnimatedVisibility(uiState.modifyDockBottomPadding) {
                Slider(
                    value = padding,
                    onValueChange = { padding = it },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    valueRange = 0f..100f,
                    onValueChangeFinished = { onEvent(LauncherEvent.DockBottomPaddingDp(padding)) }
                )
            }
        }
    }
}

sealed interface LauncherEvent {
    @JvmInline
    value class ModifyDockBottomPadding(val value: Boolean) : LauncherEvent

    @JvmInline
    value class DockBottomPaddingDp(val value: Float) : LauncherEvent

    @JvmInline
    value class ShowMemoryUsageInRecents(val value: Boolean) : LauncherEvent

    @JvmInline
    value class RecentsGridThreeRows(val value: Boolean) : LauncherEvent

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
            is LauncherEvent.ModifyDockBottomPadding -> preference.copy(
                other = preference.other.copy(modifyDockBottomPadding = event.value)
            )

            is LauncherEvent.DockBottomPaddingDp -> preference.copy(
                other = preference.other.copy(dockBottomPaddingDp = event.value)
            )

            is LauncherEvent.ShowMemoryUsageInRecents -> preference.copy(
                other = preference.other.copy(
                    showMemoryUsageInRecents = event.value
                )
            )

            is LauncherEvent.RecentsGridThreeRows -> preference.copy(
                other = preference.other.copy(
                    recentsGridThreeRows = event.value
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
