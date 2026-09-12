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
fun DetailPaneGallery(
    uiState: Preference.Other,
    onEvent: (GalleryEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    PackagePane(modifier) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.photo_library),
            title = stringResource(id = R.string.supportAllGallerySettings_title),
            summary = stringResource(id = R.string.supportAllGallerySettings_summary),
            checked = uiState.supportAllGallerySettings,
            onCheckedChange = { onEvent(GalleryEvent.SupportAllGallerySettings(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.photo_library),
            title = stringResource(id = R.string.supportSharedAlbumsInHide_title),
            summary = stringResource(id = R.string.supportSharedAlbumsInHide_summary),
            checked = uiState.supportSharedAlbumsInHide,
            onCheckedChange = { onEvent(GalleryEvent.SupportSharedAlbumsInHide(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.photo_library),
            title = stringResource(id = R.string.hideVideoEditorStudio_title),
            checked = uiState.hideVideoEditorStudio,
            onCheckedChange = { onEvent(GalleryEvent.HideVideoEditorStudio(it)) }
        )
    }
}

sealed interface GalleryEvent {
    @JvmInline
    value class SupportAllGallerySettings(val value: Boolean) : GalleryEvent

    @JvmInline
    value class SupportSharedAlbumsInHide(val value: Boolean) : GalleryEvent

    @JvmInline
    value class HideVideoEditorStudio(val value: Boolean) : GalleryEvent
}

fun SettingViewModel.onGalleryEvent(event: GalleryEvent) {
    updateData { preference ->
        when (event) {
            is GalleryEvent.SupportAllGallerySettings -> preference.copy(
                other = preference.other.copy(
                    supportAllGallerySettings = event.value
                )
            )

            is GalleryEvent.SupportSharedAlbumsInHide -> preference.copy(
                other = preference.other.copy(
                    supportSharedAlbumsInHide = event.value
                )
            )

            is GalleryEvent.HideVideoEditorStudio -> preference.copy(
                other = preference.other.copy(
                    hideVideoEditorStudio = event.value
                )
            )
        }
    }
}
