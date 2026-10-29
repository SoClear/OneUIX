package io.github.soclear.oneuix.ui.category

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.common.NetworkSpeedOption
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.ui.component.SelectItem
import io.github.soclear.oneuix.ui.component.SwitchItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtraPaneNetworkSpeed(
    uiState: Preference.SystemUI,
    onEvent: (SystemUIEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusBar = uiState.statusBar
    val speedLayout = NetworkSpeedOption.Layout.entries.getOrElse(statusBar.networkSpeedLayout) {
        NetworkSpeedOption.Layout.SYSTEM
    }
    val speedUnitPrefix = NetworkSpeedOption.UNITS.getOrElse(statusBar.networkSpeedUnit) {
        NetworkSpeedOption.UNITS[0]
    }.kiloPrefix

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.supportRealTimeNetworkSpeed_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.arrow_back),
                            contentDescription = null
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            SwitchItem(
                title = stringResource(id = R.string.supportRealTimeNetworkSpeed_title),
                summary = stringResource(id = R.string.supportRealTimeNetworkSpeed_summary),
                checked = statusBar.supportRealTimeNetworkSpeed,
                onCheckedChange = { onEvent(SystemUIEvent.StatusBar.SupportRealTimeNetworkSpeed(it)) }
            )

            AnimatedVisibility(visible = statusBar.supportRealTimeNetworkSpeed) {
                Column {
                    SelectItem(
                        title = stringResource(id = R.string.networkSpeedUnit_title),
                        entries = listOf(
                            "B/s, KB/s, MB/s (1024)",
                            "B/s, KB/s, MB/s (1000)",
                            "b/s, Kb/s, Mb/s (1000)",
                            "b/s, Kib/s, Mib/s (1024)"
                        ),
                        selectedIndex = statusBar.networkSpeedUnit.coerceIn(0, 3),
                        onSelectedIndexChange = {
                            onEvent(
                                SystemUIEvent.StatusBar.NetworkSpeedUnit(
                                    it
                                )
                            )
                        }
                    )

                    val systemSizeLabel = stringResource(id = R.string.networkSpeedSize_system)

                    SliderItem(
                        title = stringResource(id = R.string.networkSpeedTextSize_title),
                        value = statusBar.networkSpeedTextSizeSp,
                        valueRange = 0f..24f,
                        steps = 23,
                        display = { if (it <= 0f) systemSizeLabel else "%.0f sp".format(it) },
                        onCommit = { onEvent(SystemUIEvent.StatusBar.NetworkSpeedTextSize(it)) }
                    )

                    var thresholdInput by rememberSaveable(statusBar.networkSpeedThreshold) {
                        mutableStateOf(statusBar.networkSpeedThreshold.toString())
                    }
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.networkSpeedThreshold_title)) },
                        trailingContent = {
                            Text(
                                text = "${statusBar.networkSpeedThreshold} $speedUnitPrefix/s",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    OutlinedTextField(
                        value = thresholdInput,
                        onValueChange = { input ->
                            val value = input.toIntOrNull()
                            if (input.isEmpty() || (value != null && value >= 0)) {
                                thresholdInput = input
                                value?.let { onEvent(SystemUIEvent.StatusBar.NetworkSpeedThreshold(it)) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        supportingText = {
                            Text("0: " + stringResource(R.string.networkSpeedThreshold_summary_off))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    SelectItem(
                        title = stringResource(id = R.string.networkSpeedLayout_title),
                        entries = listOf(
                            stringResource(id = R.string.networkSpeedLayout_systemDefault),
                            stringResource(id = R.string.networkSpeedLayout_splitHorizontal),
                            stringResource(id = R.string.networkSpeedLayout_splitVertical),
                            stringResource(id = R.string.networkSpeedLayout_activeDirection)
                        ),
                        selectedIndex = speedLayout.ordinal,
                        onSelectedIndexChange = {
                            onEvent(
                                SystemUIEvent.StatusBar.NetworkSpeedLayout(
                                    it
                                )
                            )
                        }
                    )

                    if (speedLayout == NetworkSpeedOption.Layout.SPLIT_VERTICAL) {
                        SliderItem(
                            title = stringResource(id = R.string.networkSpeedLineSpacing_title),
                            value = statusBar.networkSpeedLineSpacingDp,
                            valueRange = 0f..16f,
                            steps = 15,
                            display = { if (it <= 0f) systemSizeLabel else "%.0f dp".format(it) },
                            onCommit = { onEvent(SystemUIEvent.StatusBar.NetworkSpeedLineSpacing(it)) }
                        )
                    }

                    AnimatedVisibility(visible = speedLayout != NetworkSpeedOption.Layout.SYSTEM) {
                        Column {
                            SwitchItem(
                                title = stringResource(id = R.string.showNetworkSpeedArrows_title),
                                summary = stringResource(id = R.string.showNetworkSpeedArrows_summary),
                                checked = statusBar.showNetworkSpeedArrows,
                                onCheckedChange = {
                                    onEvent(
                                        SystemUIEvent.StatusBar.ShowNetworkSpeedArrows(
                                            it
                                        )
                                    )
                                }
                            )

                            AnimatedVisibility(visible = statusBar.showNetworkSpeedArrows) {
                                Column {
                                    SelectItem(
                                        title = stringResource(id = R.string.networkSpeedMarker_title),
                                        entries = NetworkSpeedOption.MARKERS_UP.indices.map {
                                            "${NetworkSpeedOption.MARKERS_UP[it]}  ${NetworkSpeedOption.MARKERS_DOWN[it]}"
                                        },
                                        selectedIndex = statusBar.networkSpeedMarker.coerceIn(
                                            0,
                                            NetworkSpeedOption.MARKERS_UP.lastIndex
                                        ),
                                        onSelectedIndexChange = {
                                            onEvent(
                                                SystemUIEvent.StatusBar.NetworkSpeedMarker(
                                                    it
                                                )
                                            )
                                        }
                                    )

                                    val sameAsTextLabel =
                                        stringResource(id = R.string.networkSpeedSize_sameAsText)
                                    SliderItem(
                                        title = stringResource(id = R.string.networkSpeedMarkerSize_title),
                                        value = statusBar.networkSpeedMarkerSizeSp,
                                        valueRange = 0f..32f,
                                        steps = 31,
                                        display = {
                                            if (it <= 0f) sameAsTextLabel else "%.0f sp".format(
                                                it
                                            )
                                        },
                                        onCommit = {
                                            onEvent(
                                                SystemUIEvent.StatusBar.NetworkSpeedMarkerSize(
                                                    it
                                                )
                                            )
                                        }
                                    )

                                    SliderItem(
                                        title = stringResource(id = R.string.networkSpeedMarkerGap_title),
                                        value = statusBar.networkSpeedMarkerGap,
                                        valueRange = 0f..3f,
                                        steps = 29,
                                        display = { "%.2fx".format(it) },
                                        onCommit = {
                                            onEvent(
                                                SystemUIEvent.StatusBar.NetworkSpeedMarkerGap(
                                                    it
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderItem(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0,
) {
    var current by remember(value) { mutableFloatStateOf(value) }
    Column(modifier = modifier) {
        ListItem(
            headlineContent = { Text(text = title) },
            trailingContent = {
                Text(
                    text = display(current),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        )
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onCommit(current) },
            modifier = Modifier.padding(horizontal = 16.dp),
            valueRange = valueRange,
            steps = steps
        )
    }
}
