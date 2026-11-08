package io.github.soclear.oneuix.ui.category

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.ui.component.DefaultSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private const val DEFAULT_CHARGING_LIMIT = 100
private const val PROTECTION = "protect_battery"
private const val THRESHOLD = "battery_protection_threshold"

@Composable
fun ChargingLimitItem() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var limit by remember { mutableIntStateOf(readChargingLimit(context)) }
    var busy by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        limit = readChargingLimit(context)
    }
    fun applyLimit(target: Int) {
        busy = true
        scope.launch {
            val applied = runCatching {
                withContext(Dispatchers.IO) { setChargingLimit(context, target) }
            }.isSuccess
            limit = readChargingLimit(context)
            busy = false
            if (!applied) {
                Toast.makeText(context, R.string.chargingLimit_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.chargingLimit_title)) },
        supportingContent = { Text(stringResource(R.string.chargingLimit_summary)) },
        leadingContent = {
            Icon(ImageVector.vectorResource(R.drawable.battery), contentDescription = null)
        },
        trailingContent = { Text("$limit%") },
    )
    DefaultSlider(
        value = limit.toFloat(),
        onValueChange = { limit = it.roundToInt() },
        valueRange = 20f..100f,
        steps = 79,
        enabled = !busy,
        modifier = Modifier.padding(horizontal = 16.dp),
        onReset = { applyLimit(DEFAULT_CHARGING_LIMIT) },
        onValueChangeFinished = { applyLimit(limit) },
    )
}

private fun readChargingLimit(context: Context): Int =
    if (Settings.Global.getInt(context.contentResolver, PROTECTION, 0) == 1) {
        Settings.Global.getInt(context.contentResolver, THRESHOLD, 80).coerceIn(20, 100)
    } else DEFAULT_CHARGING_LIMIT

private fun setChargingLimit(context: Context, limit: Int) {
    require(limit in 20..100)
    val permission = Manifest.permission.WRITE_SECURE_SETTINGS
    if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
        val process = ProcessBuilder("su", "-c", "pm grant ${context.packageName} $permission").start()
        try {
            check(process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0)
        } finally {
            process.destroy()
        }
    }
    // Samsung's BatteryService applies and persists the threshold, including nonstandard values.
    check(Settings.Global.putInt(context.contentResolver, THRESHOLD, limit))
    check(Settings.Global.putInt(context.contentResolver, PROTECTION, if (limit == 100) 0 else 1))
}
