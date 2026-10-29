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
fun DetailPaneOther(
    uiState: Preference.Other,
    onEvent: (OtherEvent) -> Unit,
    onNavigateToWritingToolkitApps: () -> Unit,
    onNavigateToScreenshotDetectionApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    PackagePane(modifier) {
        SwitchItem(
            icon = ImageVector.vectorResource(R.drawable.format_letter_spacing),
            title = stringResource(R.string.disableWritingToolkit_title),
            summary = stringResource(R.string.app_picker_selected_count, uiState.disableWritingToolkitPackages.size) +
                "\n" + stringResource(R.string.disableWritingToolkit_summary),
            clickable = true,
            onClick = onNavigateToWritingToolkitApps,
            checked = uiState.disableWritingToolkit,
            onCheckedChange = {
                onEvent(OtherEvent.DisableWritingToolkit(it))
                if (it && uiState.disableWritingToolkitPackages.isEmpty()) onNavigateToWritingToolkitApps()
            }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(R.drawable.screenshot),
            title = stringResource(R.string.hideScreenshotDetection_title),
            summary = stringResource(R.string.app_picker_selected_count, uiState.hideScreenshotDetectionPackages.size) +
                "\n" + stringResource(R.string.hideScreenshotDetection_summary),
            clickable = true,
            onClick = onNavigateToScreenshotDetectionApps,
            checked = uiState.hideScreenshotDetection,
            onCheckedChange = {
                onEvent(OtherEvent.HideScreenshotDetection(it))
                if (it && uiState.hideScreenshotDetectionPackages.isEmpty()) onNavigateToScreenshotDetectionApps()
            }
        )
    }
}

sealed interface OtherEvent {
    @JvmInline
    value class DisableWritingToolkit(val value: Boolean) : OtherEvent

    @JvmInline
    value class DisableWritingToolkitPackages(val value: Set<String>) : OtherEvent

    @JvmInline
    value class HideScreenshotDetection(val value: Boolean) : OtherEvent

    @JvmInline
    value class HideScreenshotDetectionPackages(val value: Set<String>) : OtherEvent
}

fun SettingViewModel.onOtherEvent(event: OtherEvent) {
    updateData { preference ->
        preference.copy(other = when (event) {
            is OtherEvent.DisableWritingToolkit -> preference.other.copy(disableWritingToolkit = event.value)
            is OtherEvent.DisableWritingToolkitPackages -> preference.other.copy(disableWritingToolkitPackages = event.value)
            is OtherEvent.HideScreenshotDetection -> preference.other.copy(hideScreenshotDetection = event.value)
            is OtherEvent.HideScreenshotDetectionPackages -> preference.other.copy(hideScreenshotDetectionPackages = event.value)
        })
    }
}
