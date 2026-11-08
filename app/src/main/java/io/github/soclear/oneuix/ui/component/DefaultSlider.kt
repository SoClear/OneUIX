package io.github.soclear.oneuix.ui.component

import android.annotation.SuppressLint
import android.util.TypedValue
import android.view.Surface
import android.view.WindowManager
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.common.ONE_UI_VERSION

/** Resets through the same commit path as a drag, including any native-default sentinel. */
@Composable
fun DefaultSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
        )
        TextButton(onClick = onReset, enabled = enabled, modifier = Modifier.padding(start = 8.dp)) {
            Text(stringResource(R.string.slider_systemDefault))
        }
    }
}

/** Read the unrounded dp/sp value from the active ROM, including resource overlays. */
@SuppressLint("DiscouragedApi") // These IDs belong to SystemUI, not this APK.
@Composable
fun rememberSystemUiDimension(name: String, unit: Int = TypedValue.COMPLEX_UNIT_DIP): Float? {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(configuration, name, unit) {
        runCatching {
            val resources = context.createPackageContext("com.android.systemui", 0)
                .createConfigurationContext(configuration).resources
            val id = resources.getIdentifier(name, "dimen", "com.android.systemui")
            val value = TypedValue()
            resources.getValue(id, value, true)
            if (value.type == TypedValue.TYPE_DIMENSION && value.complexUnit == unit) {
                TypedValue.complexToFloat(value.data)
            } else {
                @Suppress("DEPRECATION")
                val density = if (unit == TypedValue.COMPLEX_UNIT_SP)
                    resources.displayMetrics.scaledDensity else resources.displayMetrics.density
                resources.getDimension(id) / density
            }
        }.getOrNull()
    }
}

/**
 * Preview the directly readable corner-padding branch of Samsung's garden algorithm.
 * Other layouts retain a native-default sentinel; SystemUI computes their actual padding.
 */
@SuppressLint("DiscouragedApi") // Framework resource IDs must be resolved on the running ROM.
@Composable
fun rememberNativeStatusBarSidePaddingDp(): Float? {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        runCatching {
            val windowManager = context.getSystemService(WindowManager::class.java)
            val cutout = windowManager.currentWindowMetrics.windowInsets.displayCutout
            if ((cutout?.safeInsetLeft ?: 0) != 0 || (cutout?.safeInsetRight ?: 0) != 0) {
                return@runCatching null
            }
            // These versions choose the corner-padding branch under different conditions.
            val usesCornerPadding = when (ONE_UI_VERSION) {
                in 90000..99999 -> (cutout?.safeInsetTop ?: 0) > 0
                in 80500..89999 -> context.display?.rotation == Surface.ROTATION_0
                else -> false
            }
            if (!usesCornerPadding) return@runCatching null
            val resources = context.createPackageContext("com.android.systemui", 0)
                .createConfigurationContext(configuration).resources
            val cornerId = resources.getIdentifier("status_bar_indicator_corner_padding", "dimen", "android")
            if (cornerId == 0) return@runCatching null
            val pixels = resources.getDimensionPixelSize(cornerId)
            if (pixels > 0) pixels / resources.displayMetrics.density else null
        }.getOrNull()
    }
}
