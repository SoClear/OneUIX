package io.github.soclear.oneuix.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.soclear.oneuix.R

@Composable
fun SimOperatorItem(
    title: String,
    checked: Boolean,
    value: String,
    onCheckedChange: (Boolean) -> Unit,
    onConfirm: (String) -> Unit,
) {
    Column {
        val uriHandler = LocalUriHandler.current
        var expanded by rememberSaveable { mutableStateOf(false) }
        SwitchItem(
            title = title,
            modifier = Modifier.animateContentSize(),
            summary = if (checked) value else null,
            icon = ImageVector.vectorResource(R.drawable.sim_card),
            clickable = true,
            onClick = { expanded = !expanded },
            checked = checked,
            onCheckedChange = {
                if (it && value.isEmpty()) expanded = true
                else if (!it) expanded = false
                onCheckedChange(it)
            },
        )
        AnimatedVisibility(expanded && checked) {
            var simOperator by rememberSaveable(value) {
                mutableStateOf(value)
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = simOperator,
                        onValueChange = {
                            if (it.length <= 6 && it.all { c -> c in '0'..'9' }) simOperator = it
                        },
                        modifier = Modifier.weight(1f),
                        isError = simOperator.length in 1..4,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(simOperator) },
                        enabled = simOperator.isEmpty() || simOperator.length in 5..6
                    ) { Text(stringResource(R.string.confirm)) }
                }
                Text(stringResource(R.string.simOperator_summary))
                TextButton(onClick = {
                    uriHandler.openUri("https://www.itu.int/pub/T-SP-E.212B")
                }) { Text(stringResource(R.string.simOperator_reference)) }
            }
        }
    }
}
