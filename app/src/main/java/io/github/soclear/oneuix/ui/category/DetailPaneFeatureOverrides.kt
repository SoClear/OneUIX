package io.github.soclear.oneuix.ui.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.common.FeatureOverride
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.component.SwitchItem

/** The tabs: one per feature table. */
private enum class GateTab(val titleRes: Int) {
    Csc(R.string.featureOverrides_csc),
    Floating(R.string.featureOverrides_floating)
}

/**
 * What the list is filtered to: what a gate holds, or whether the module has been told to answer it.
 */
private enum class GateFilter(val titleRes: Int) {
    All(R.string.featureOverride_filter_all),
    Enabled(R.string.featureOverride_filter_enabled),
    Disabled(R.string.featureOverride_filter_disabled),
    Overridden(R.string.featureOverride_filter_overridden)
}

/** The values a gate can hold and be shown as a switch for. */
private val BOOLEAN_VALUES = setOf("true", "false", "1", "0")

/**
 * The Samsung feature switches this module can answer.
 *
 * A gate is shown as a switch when it holds a yes or no, and as a box to type a value into when it
 * holds anything else. A switch sets what the gate answers, so a gate the ROM holds as TRUE can be
 * answered with FALSE, and a box does the same for a value that is not a yes or no. Either way the
 * gate is answered in every app in the module's scope once it has been set, which the Overridden
 * filter lists.
 */
@Composable
fun DetailPaneFeatureOverrides(
    uiState: List<FeatureOverride>,
    deviceValues: Map<String, String>,
    onEvent: (FeatureOverrideEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by rememberSaveable { mutableIntStateOf(GateTab.Csc.ordinal) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by remember { mutableStateOf(GateFilter.All) }
    var adding by remember { mutableStateOf(false) }
    val selected = GateTab.entries[tab]

    val gates = uiState
        .filter {
            when (selected) {
                GateTab.Csc -> it.kind == FeatureOverride.CSC
                GateTab.Floating -> it.kind == FeatureOverride.FLOATING_FEATURE
            }
        }
        .filter { query.isBlank() || it.key.contains(query, true) || it.title.contains(query, true) }
        .filter {
            when (filter) {
                GateFilter.All -> true
                GateFilter.Enabled -> it.isYesOrNo() && it.answersYes()
                GateFilter.Disabled -> it.isYesOrNo() && !it.answersYes()
                GateFilter.Overridden -> it.enabled
            }
        }

    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            GateTab.entries.forEach { option ->
                Tab(
                    selected = selected == option,
                    onClick = { tab = option.ordinal },
                    text = { Text(text = stringResource(id = option.titleRes)) }
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text(text = stringResource(id = R.string.featureOverride_search)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Four chips do not fit across a phone, so the row scrolls.
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GateFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(text = stringResource(id = option.titleRes)) }
                )
            }
        }

        Button(
            onClick = { adding = true },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(text = stringResource(id = R.string.featureOverride_add))
        }

        if (gates.isEmpty()) {
            Text(
                text = stringResource(id = R.string.featureOverride_none),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // The device carries hundreds of gates, so only the rows on screen are built.
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(gates, key = { it.key }) { gate ->
                if (gate.enabled) {
                    // A gate the module answers carries its own reset, next to the switch or the box
                    // that set it, so it is clear which gate is being put back.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            GateRow(
                                gate = gate,
                                deviceValues = deviceValues,
                                onEvent = onEvent
                            )
                        }
                        IconButton(onClick = { onEvent(FeatureOverrideEvent.Revert(gate.key)) }) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_revert),
                                contentDescription =
                                    stringResource(id = R.string.featureOverride_revert)
                            )
                        }
                    }
                } else {
                    GateRow(gate = gate, deviceValues = deviceValues, onEvent = onEvent)
                }
            }
        }
    }

    if (adding) {
        AddGateDialog(
            kind = if (selected == GateTab.Floating) {
                FeatureOverride.FLOATING_FEATURE
            } else {
                FeatureOverride.CSC
            },
            onDismiss = { adding = false },
            onAdd = { key, value ->
                onEvent(FeatureOverrideEvent.Add(key, value))
                adding = false
            }
        )
    }
}

/** One gate: a switch when it holds a yes or no, a box to type into when it holds anything else. */
@Composable
private fun GateRow(
    gate: FeatureOverride,
    deviceValues: Map<String, String>,
    onEvent: (FeatureOverrideEvent) -> Unit
) {
    if (gate.isYesOrNo()) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.ic_dot),
            iconTint = gate.dotColour(deviceValues),
            title = gate.title,
            summary = gate.key,
            checked = gate.answersYes(),
            onCheckedChange = {
                onEvent(FeatureOverrideEvent.Set(gate.key, gate.yesOrNo(it), enabled = true))
            }
        )
    } else {
        ValueItem(gate = gate, deviceValues = deviceValues, onEvent = onEvent)
    }
}

/**
 * Green when the value this row answers with is not the one the ROM holds, so answering it changes
 * what the apps see, and red when it is the same and answering it would change nothing.
 */
private fun FeatureOverride.dotColour(deviceValues: Map<String, String>): Color {
    val rom = deviceValues[key] ?: if (kind == FeatureOverride.FLOATING_FEATURE) "0" else ""
    return if (sameAnswer(value, rom)) ChangesNothing else ChangesThings
}

/** Whether two answers mean the same thing, counting the ways a yes or a no is written. */
private fun sameAnswer(one: String, other: String): Boolean {
    if (one.trim().equals(other.trim(), ignoreCase = true)) return true
    val yesOrNo = one.asYesOrNo() ?: return false
    return yesOrNo == other.asYesOrNo()
}

private fun String.asYesOrNo(): Boolean? = when (lowercase().trim()) {
    "true", "1", "on", "yes" -> true
    "false", "0", "off", "no", "" -> false
    else -> null
}

private val ChangesThings = Color(0xFF4CAF50)
private val ChangesNothing = Color(0xFFE53935)

/** A gate whose value is not a yes or no, set through a box rather than a switch. */
@Composable
private fun ValueItem(
    gate: FeatureOverride,
    deviceValues: Map<String, String>,
    onEvent: (FeatureOverrideEvent) -> Unit
) {
    var text by remember(gate.key) { mutableStateOf(gate.value) }

    fun save() {
        if (text != gate.value) onEvent(FeatureOverrideEvent.Set(gate.key, text, enabled = true))
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_dot),
                contentDescription = null,
                tint = gate.dotColour(deviceValues),
                modifier = Modifier.size(10.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = gate.title)
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            label = { Text(text = gate.key) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) save() }
        )
    }
}

/** A key and a value typed by hand, for a gate the device does not carry. */
@Composable
private fun AddGateDialog(kind: Int, onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var key by remember {
        mutableStateOf(if (kind == FeatureOverride.CSC) "CscFeature_" else "SEC_FLOATING_FEATURE_")
    }
    var value by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.featureOverride_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    singleLine = true,
                    label = { Text(text = stringResource(id = R.string.featureOverride_add_key)) }
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    label = { Text(text = stringResource(id = R.string.featureOverride_add_value)) }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(key.trim(), value.trim()) },
                enabled = key.trim().length > 1
            ) {
                Text(text = stringResource(id = R.string.featureOverride_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.featureOverride_cancel))
            }
        }
    )
}

/** Whether the gate holds a yes or a no, which is what it is shown as a switch for. */
private fun FeatureOverride.isYesOrNo() = value.trim().lowercase() in BOOLEAN_VALUES

private fun FeatureOverride.answersYes() = value.trim().lowercase() in setOf("true", "1")

/** The value to answer with, written the way this gate already writes it. */
private fun FeatureOverride.yesOrNo(yes: Boolean): String = when {
    value.trim() in setOf("1", "0") -> if (yes) "1" else "0"
    value.trim() == value.trim().uppercase() -> if (yes) "TRUE" else "FALSE"
    else -> if (yes) "true" else "false"
}

sealed interface FeatureOverrideEvent {
    /**
     * What a gate is to answer, and whether it is to answer at all. A gate the device carries but
     * the file does not hold yet is added by this, so a switch works on a row that was never
     * written down.
     */
    data class Set(val key: String, val value: String, val enabled: Boolean) : FeatureOverrideEvent

    data class Add(val key: String, val value: String) : FeatureOverrideEvent

    data class Revert(val key: String) : FeatureOverrideEvent
}

fun SettingViewModel.onFeatureOverrideEvent(event: FeatureOverrideEvent) {
    updateData { preference ->
        val overrides = FeatureOverride.normalize(preference.featureOverrides)
        when (event) {
            is FeatureOverrideEvent.Set -> preference.copy(
                featureOverrides = overrides.withGate(event.key, event.value, event.enabled)
            )

            is FeatureOverrideEvent.Add -> preference.copy(
                featureOverrides = overrides.withGate(event.key, event.value, enabled = true)
            )

            // Stops answering the gate. One the catalogue offers goes back to the value it offers,
            // so the row reads as it did before it was touched. One only this device carries is
            // dropped, which leaves the ROM's own answer in place.
            is FeatureOverrideEvent.Revert -> {
                val offered = FeatureOverride.presets().firstOrNull { it.key == event.key }?.value
                preference.copy(
                    featureOverrides = if (offered != null) {
                        overrides.withGate(event.key, offered, enabled = false)
                    } else {
                        overrides.filterNot { it.key == event.key }
                    }
                )
            }
        }
    }
}

/** The list with one gate set, adding the gate if it is not in the list yet. */
private fun List<FeatureOverride>.withGate(
    key: String,
    value: String,
    enabled: Boolean
): List<FeatureOverride> = if (any { it.key == key }) {
    map { if (it.key == key) it.copy(value = value, enabled = enabled) else it }
} else {
    this + FeatureOverride(
        key = key,
        value = value,
        kind = FeatureOverride.kindOf(key),
        title = key.removePrefix("CscFeature_").removePrefix("SEC_FLOATING_FEATURE_"),
        enabled = enabled
    )
}
