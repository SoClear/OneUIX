package io.github.soclear.oneuix.ui.category

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.ui.component.AppPicker
import io.github.soclear.oneuix.ui.component.SwitchItem

@Composable
fun MediaPageSettings(uiState: Preference.Other, onEvent: (LauncherEvent) -> Unit) {
    val context = LocalContext.current
    val savedPackage = uiState.mediaPagePackage
    var picking by rememberSaveable { mutableStateOf(false) }
    val label = remember(savedPackage) {
        runCatching { context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(savedPackage, 0)).toString()
        }.getOrDefault(savedPackage)
    }
    SwitchItem(
        icon = ImageVector.vectorResource(R.drawable.apps),
        title = stringResource(R.string.mediaPage_title),
        summary = label.ifEmpty { stringResource(R.string.mediaPage_selectApp) } +
            "\n" + stringResource(R.string.mediaPage_summary),
        clickable = true,
        onClick = { picking = true },
        checked = uiState.embedAppInMediaPage,
        onCheckedChange = {
            onEvent(LauncherEvent.EmbedAppInMediaPage(it))
            if (it && savedPackage.isEmpty()) picking = true
        },
    )
    if (picking) Dialog(onDismissRequest = { picking = false },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding()) {
            AppPicker(
                selectedPackages = if (savedPackage.isEmpty()) emptySet() else setOf(savedPackage),
                onSelectionChange = { onEvent(LauncherEvent.MediaPagePackage(it.single())); picking = false },
                onBack = { picking = false },
                singleSelection = true,
                launchableOnly = true,
                excludedPackages = setOf(context.packageName, "com.sec.android.app.launcher"),
            )
        }
    }
}
