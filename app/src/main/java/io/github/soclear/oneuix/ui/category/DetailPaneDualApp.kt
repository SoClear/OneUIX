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
fun DetailPaneDualApp(
    uiState: Preference.Other,
    onEvent: (DualAppEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    PackagePane(modifier) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.apk_document),
            title = stringResource(id = R.string.makeAllUserAppsAvailable_title),
            checked = uiState.makeAllUserAppsAvailable,
            onCheckedChange = { onEvent(DualAppEvent.MakeAllUserAppsAvailable(it)) }
        )
    }
}

sealed interface DualAppEvent {
    @JvmInline
    value class MakeAllUserAppsAvailable(val value: Boolean) : DualAppEvent
}

fun SettingViewModel.onDualAppEvent(event: DualAppEvent) {
    updateData { preference ->
        when (event) {
            is DualAppEvent.MakeAllUserAppsAvailable -> preference.copy(
                other = preference.other.copy(
                    makeAllUserAppsAvailable = event.value
                )
            )
        }
    }
}
