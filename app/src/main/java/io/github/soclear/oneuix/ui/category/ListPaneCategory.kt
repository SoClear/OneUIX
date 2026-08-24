package io.github.soclear.oneuix.ui.category

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.soclear.oneuix.R

@Composable
fun ListPaneCategory(
    categoryAppInfoList: List<CategoryAppInfo>,
    hiddenCategoryNames: Set<String>,
    onItemClick: (Category) -> Unit,
    onCategoryVisibilityChange: (Category, Boolean) -> Unit,
    onShowAllCategories: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenuManager by rememberSaveable { mutableStateOf(false) }
    val visibleCategories = categoryAppInfoList.filterNot {
        it.category.name in hiddenCategoryNames
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onBackup,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(id = R.string.backup_config))
            }
            Button(
                onClick = onRestore,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(id = R.string.restore_config))
            }
        }
        OutlinedButton(
            onClick = { showMenuManager = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(text = stringResource(R.string.manage_main_menu))
        }
        if (visibleCategories.isEmpty()) {
            Text(
                text = stringResource(R.string.all_main_menu_categories_hidden),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            )
        }
        visibleCategories.forEach {
            ListItem(
                headlineContent = {
                    Text(text = it.label, fontSize = 20.sp)
                },
                modifier = Modifier.clickable {
                    onItemClick(it.category)
                },
                leadingContent = {
                    Image(
                        bitmap = it.icon,
                        contentDescription = it.label,
                        modifier = Modifier.size(60.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            )
        }
    }

    if (showMenuManager) {
        MenuVisibilityDialog(
            categoryAppInfoList = categoryAppInfoList,
            hiddenCategoryNames = hiddenCategoryNames,
            onCategoryVisibilityChange = onCategoryVisibilityChange,
            onShowAllCategories = onShowAllCategories,
            onDismiss = { showMenuManager = false },
        )
    }
}

@Composable
private fun MenuVisibilityDialog(
    categoryAppInfoList: List<CategoryAppInfo>,
    hiddenCategoryNames: Set<String>,
    onCategoryVisibilityChange: (Category, Boolean) -> Unit,
    onShowAllCategories: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_main_menu)) },
        text = {
            Column {
                Text(stringResource(R.string.manage_main_menu_summary))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                    items(
                        items = categoryAppInfoList,
                        key = { it.category.name },
                    ) { appInfo ->
                        val visible = appInfo.category.name !in hiddenCategoryNames
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = visible,
                                    onValueChange = {
                                        onCategoryVisibilityChange(appInfo.category, it)
                                    },
                                    role = Role.Checkbox,
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                bitmap = appInfo.icon,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Text(
                                text = appInfo.label,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                            )
                            Checkbox(checked = visible, onCheckedChange = null)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = {
            TextButton(onClick = onShowAllCategories) {
                Text(stringResource(R.string.show_all_categories))
            }
        },
    )
}
