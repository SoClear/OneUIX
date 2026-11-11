package io.github.soclear.oneuix.ui.category

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.component.ForceStopApp
import io.github.soclear.oneuix.ui.component.SwitchItem

@Composable
fun DetailPaneMyFiles(
    uiState: Preference.Other,
    onEvent: (MyFilesEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    PackagePane(modifier) {
        ForceStopApp(Package.MY_FILES)
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.folder_managed),
            title = stringResource(id = R.string.fixMyFilesNetworkStorage_title),
            summary = stringResource(id = R.string.fixMyFilesNetworkStorage_summary),
            checked = uiState.fixMyFilesNetworkStorage,
            onCheckedChange = { onEvent(MyFilesEvent.FixNetworkStorage(it)) }
        )
    }
}

sealed interface MyFilesEvent {
    @JvmInline
    value class FixNetworkStorage(val value: Boolean) : MyFilesEvent
}

fun SettingViewModel.onMyFilesEvent(event: MyFilesEvent) {
    updateData { preference ->
        preference.copy(other = when (event) {
            is MyFilesEvent.FixNetworkStorage -> preference.other.copy(fixMyFilesNetworkStorage = event.value)
        })
    }
}
