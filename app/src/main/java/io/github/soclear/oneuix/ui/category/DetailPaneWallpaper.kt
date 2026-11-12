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
fun DetailPaneWallpaper(
    uiState: Preference.Other,
    onEvent: (WallpaperEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    PackagePane(modifier) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.wallpaper_live),
            title = stringResource(id = R.string.restoreLiveWallpaperEntry_title),
            summary = stringResource(id = R.string.restoreLiveWallpaperEntry_summary),
            checked = uiState.restoreLiveWallpaperEntry,
            onCheckedChange = { onEvent(WallpaperEvent.RestoreLiveWallpaperEntry(it)) }
        )
    }
}

sealed interface WallpaperEvent {
    @JvmInline
    value class RestoreLiveWallpaperEntry(val value: Boolean) : WallpaperEvent
}

fun SettingViewModel.onWallpaperEvent(event: WallpaperEvent) {
    updateData { preference ->
        preference.copy(other = when (event) {
            is WallpaperEvent.RestoreLiveWallpaperEntry -> preference.other.copy(restoreLiveWallpaperEntry = event.value)
        })
    }
}
