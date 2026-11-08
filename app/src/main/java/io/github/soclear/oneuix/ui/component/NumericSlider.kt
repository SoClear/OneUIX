package io.github.soclear.oneuix.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.soclear.oneuix.R
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

internal fun normalizeSliderValue(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    decimalPlaces: Int,
    steps: Int = 0,
): Float {
    val snapped = if (steps > 0) {
        val interval = (range.endInclusive - range.start) / (steps + 1)
        range.start + ((value - range.start) / interval).roundToInt() * interval
    } else value
    return BigDecimal(snapped.toString()).setScale(decimalPlaces, RoundingMode.HALF_UP)
        .toFloat().coerceIn(range)
}

internal fun parseSliderInput(
    input: String,
    range: ClosedFloatingPointRange<Float>,
    locale: Locale = Locale.getDefault(),
): Float? {
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    val normalized = input.trim().replace(separator, '.')
    // Accept decimal input only; grouping, exponent notation and non-finite values are invalid.
    if (!normalized.matches(Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))) return null
    return normalized.toFloatOrNull()?.takeIf { it.isFinite() && it in range }
}

/** Numeric edits commit explicitly; opening/focusing never replaces a native-default sentinel. */
@Composable
fun NumericSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    onReset: () -> Unit,
    label: String,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    unit: String = "",
    decimalPlaces: Int = 1,
    steps: Int = 0,
    enabled: Boolean = true,
    defaultDescription: String? = null,
    unknownDefault: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    val locale = LocalConfiguration.current.locales[0]
    fun format(number: Float) = String.format(locale, "%.${decimalPlaces}f", number)
    var input by remember(value, unknownDefault, defaultDescription, locale) {
        mutableStateOf(if (unknownDefault) "" else format(value))
    }
    var edited by remember(value, unknownDefault, defaultDescription) { mutableStateOf(false) }
    var dragValue by remember(value) { mutableStateOf(value) }
    val parsed = parseSliderInput(input, valueRange, locale)
    val error = edited && parsed == null
    val rangeHint = stringResource(
        R.string.slider_numericRange,
        format(valueRange.start), format(valueRange.endInclusive),
    )
    fun commitInput() {
        if (!edited) {
            focusManager.clearFocus()
            return
        }
        val number = parsed ?: return
        val committed = normalizeSliderValue(number, valueRange, decimalPlaces, steps)
        input = format(committed)
        edited = false
        onValueChange(committed)
        onCommit(committed)
        focusManager.clearFocus()
    }
    Column(modifier) {
        Slider(
            value = value,
            onValueChange = {
                val normalized = normalizeSliderValue(it, valueRange, decimalPlaces, steps)
                dragValue = normalized
                input = format(normalized)
                edited = false
                onValueChange(normalized)
            },
            onValueChangeFinished = { onCommit(dragValue) },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; edited = true },
                modifier = Modifier.weight(1f).semantics { contentDescription = label },
                enabled = enabled,
                suffix = if (unit.isNotEmpty()) ({ Text(unit) }) else null,
                placeholder = if (unknownDefault) ({ Text(stringResource(R.string.slider_systemDefault)) }) else null,
                supportingText = { Text(if (!edited && defaultDescription != null) defaultDescription else rangeHint) },
                isError = error,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (decimalPlaces == 0) KeyboardType.Number else KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { commitInput() }),
                trailingIcon = if (edited) ({
                    TextButton(onClick = { commitInput() }, enabled = enabled && !error) {
                        Text(stringResource(R.string.confirm))
                    }
                }) else null,
            )
            TextButton(
                onClick = {
                    edited = false
                    focusManager.clearFocus()
                    onReset()
                    // Reset also discards an unfinished edit when the value was already default.
                    input = if (unknownDefault) "" else format(value)
                },
                enabled = enabled,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.slider_systemDefault))
            }
        }
    }
}
