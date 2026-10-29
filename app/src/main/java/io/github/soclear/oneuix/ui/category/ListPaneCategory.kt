package io.github.soclear.oneuix.ui.category

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.soclear.oneuix.R

@Composable
fun ListPaneCategory(
    categoryAppInfoList: List<CategoryAppInfo>,
    onItemClick: (Category) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        categoryAppInfoList.forEach {
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
        ListItem(
            headlineContent = { Text(stringResource(R.string.other), fontSize = 20.sp) },
            modifier = Modifier.clickable { onItemClick(Category.Other) },
            leadingContent = {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.apps),
                    contentDescription = null,
                    modifier = Modifier.size(60.dp)
                )
            }
        )
    }
}
