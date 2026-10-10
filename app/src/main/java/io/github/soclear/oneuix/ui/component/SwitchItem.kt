package io.github.soclear.oneuix.ui.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun SwitchItem(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    clickable: Boolean = false,
    onClick: () -> Unit = {},
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val listItemModifier = if (clickable) {
        modifier.clickable(
            onClick = onClick,
            role = Role.Button
        )
    } else {
        modifier.toggleable(
            value = checked,
            onValueChange = onCheckedChange,
            role = Role.Switch,
            interactionSource = interactionSource,
            indication = LocalIndication.current
        )
    }

    ListItem(
        headlineContent = { Text(title) },
        modifier = listItemModifier,
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = icon?.let {
            { Icon(it, title, tint = iconTint ?: LocalContentColor.current) }
        },
        trailingContent = {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (clickable) {
                    VerticalDivider(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(start = 0.dp, top = 8.dp, end = 15.dp, bottom = 8.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.width(16.dp))
                }

                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    interactionSource = if (clickable) null else interactionSource
                )
            }
        }
    )
}
