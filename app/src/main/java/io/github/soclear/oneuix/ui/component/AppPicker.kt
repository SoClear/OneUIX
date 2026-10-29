package io.github.soclear.oneuix.ui.component

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.soclear.oneuix.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class PickerApp(val info: ApplicationInfo, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPicker(
    selectedPackages: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val packageManager = LocalContext.current.applicationContext.packageManager
    var query by rememberSaveable { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<PickerApp>?>(null) }
    val filteredApps = remember(apps, query, selectedPackages) {
        val search = query.trim()
        val (selectedApps, otherApps) = apps.orEmpty().filter {
            it.label.contains(search, ignoreCase = true) ||
                it.info.packageName.contains(search, ignoreCase = true)
        }.partition { it.info.packageName in selectedPackages }
        selectedApps + otherApps
    }

    val isWindowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(isWindowFocused) {
        // Reload when returning from the app-list permission dialog.
        if (isWindowFocused) {
            apps = withContext(Dispatchers.IO) {
                packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
                    .map { PickerApp(it, it.loadLabel(packageManager).toString()) }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(ImageVector.vectorResource(R.drawable.arrow_back), contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.app_picker_search_hint)) },
                singleLine = true
            )
            Text(stringResource(R.string.app_picker_selected_count, selectedPackages.size))
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when {
                    apps == null -> CircularProgressIndicator()
                    filteredApps.isEmpty() -> Text(stringResource(R.string.app_picker_empty))
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredApps, key = { it.info.packageName }) { app ->
                            val packageName = app.info.packageName
                            val checked = packageName in selectedPackages
                            ListItem(
                                headlineContent = { Text(app.label) },
                                supportingContent = { Text(packageName) },
                                leadingContent = {
                                    AppPickerIcon(app.info, packageManager)
                                },
                                trailingContent = {
                                    Checkbox(checked = checked, onCheckedChange = null)
                                },
                                modifier = Modifier.toggleable(
                                    value = checked,
                                    role = Role.Checkbox,
                                    onValueChange = { selected ->
                                        onSelectionChange(
                                            if (selected) selectedPackages + packageName
                                            else selectedPackages - packageName
                                        )
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPickerIcon(
    info: ApplicationInfo,
    packageManager: PackageManager
) {
    val iconSizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, info, packageManager, iconSizePx) {
        value = withContext(Dispatchers.IO) {
            info.loadIcon(packageManager)
                .toBitmap(width = iconSizePx, height = iconSizePx)
                .asImageBitmap()
        }
    }
    Box(modifier = Modifier.size(40.dp)) {
        icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}
