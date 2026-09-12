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
fun DetailPaneSketchBook(
    uiState: Preference.Other,
    onEvent: (SketchBookEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    PackagePane(modifier) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.branding_watermark),
            title = stringResource(id = R.string.noAIWatermark_title),
            summary = stringResource(id = R.string.noAIWatermark_summary),
            checked = uiState.noAIWatermark,
            onCheckedChange = { onEvent(SketchBookEvent.NoAIWatermark(it)) }
        )
    }
}

sealed interface SketchBookEvent {
    @JvmInline
    value class NoAIWatermark(val value: Boolean) : SketchBookEvent
}

fun SettingViewModel.onSketchBookEvent(event: SketchBookEvent) {
    updateData { preference ->
        when (event) {
            is SketchBookEvent.NoAIWatermark -> preference.copy(
                other = preference.other.copy(
                    noAIWatermark = event.value
                )
            )
        }
    }
}
