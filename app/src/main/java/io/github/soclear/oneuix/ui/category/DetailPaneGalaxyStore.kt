package io.github.soclear.oneuix.ui.category

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
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.component.SwitchItem

@Composable
fun DetailPaneGalaxyStore(
    uiState: Preference.Other,
    onEvent: (GalaxyStoreEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    PackagePane(modifier) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.ad_off),
            title = stringResource(id = R.string.blockGalaxyStoreAds_title),
            summary = stringResource(id = R.string.blockGalaxyStoreAds_summary),
            checked = uiState.blockGalaxyStoreAds,
            onCheckedChange = { onEvent(GalaxyStoreEvent.BlockGalaxyStoreAds(it)) }
        )
        Column {
            val uriHandler = LocalUriHandler.current
            var expanded by rememberSaveable { mutableStateOf(false) }
            SwitchItem(
                title = stringResource(R.string.galaxyStoreRegion_title),
                modifier = Modifier.animateContentSize(),
                summary = if (uiState.setGalaxyStoreSimOperator) uiState.galaxyStoreSimOperator else null,
                icon = ImageVector.vectorResource(R.drawable.sim_card),
                clickable = true,
                onClick = { expanded = !expanded },
                checked = uiState.setGalaxyStoreSimOperator,
                onCheckedChange = {
                    if (it && uiState.galaxyStoreSimOperator.isEmpty()) expanded = true
                    else if (!it) expanded = false
                    onEvent(GalaxyStoreEvent.SetSimOperator(it))
                },
            )
            AnimatedVisibility(expanded && uiState.setGalaxyStoreSimOperator) {
                var simOperator by rememberSaveable(uiState.galaxyStoreSimOperator) {
                    mutableStateOf(uiState.galaxyStoreSimOperator)
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
                            onClick = { onEvent(GalaxyStoreEvent.SimOperator(simOperator)) },
                            enabled = simOperator.isEmpty() || simOperator.length in 5..6
                        ) { Text(stringResource(R.string.confirm)) }
                    }
                    Text(stringResource(R.string.galaxyStoreRegion_summary))
                    TextButton(onClick = {
                        uriHandler.openUri("https://www.ituob.org/registers/e212-mnc/at/1174/")
                    }) { Text(stringResource(R.string.galaxyStoreRegion_reference)) }
                }
            }
        }
    }
}

sealed interface GalaxyStoreEvent {
    @JvmInline
    value class BlockGalaxyStoreAds(val value: Boolean) : GalaxyStoreEvent

    @JvmInline
    value class SetSimOperator(val value: Boolean) : GalaxyStoreEvent

    @JvmInline
    value class SimOperator(val value: String) : GalaxyStoreEvent
}

fun SettingViewModel.onGalaxyStoreEvent(event: GalaxyStoreEvent) {
    updateData { preference ->
        preference.copy(other = when (event) {
            is GalaxyStoreEvent.SetSimOperator -> preference.other.copy(setGalaxyStoreSimOperator = event.value)
            is GalaxyStoreEvent.SimOperator -> preference.other.copy(galaxyStoreSimOperator = event.value)
            is GalaxyStoreEvent.BlockGalaxyStoreAds -> preference.other.copy(blockGalaxyStoreAds = event.value)
        })
    }
}
