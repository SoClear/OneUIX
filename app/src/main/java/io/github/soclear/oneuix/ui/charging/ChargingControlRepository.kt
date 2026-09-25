package io.github.soclear.oneuix.ui.charging

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import io.github.soclear.oneuix.common.ChargingControlKeys
import io.github.soclear.oneuix.common.ChargingControlPolicy
import java.util.concurrent.TimeUnit

data class ChargingControlState(
    val limit: Int,
    val remember: Boolean,
    val active: Boolean,
    val quickPauseEnabled: Boolean,
    val bypassEnabled: Boolean,
    val pluggedIn: Boolean,
    val batteryLevel: Int?,
)

class ChargingControlRepository(private val context: Context) {
    private val engine = ChargingControlEngine(object : ChargingControlGateway {
        override fun read(field: ChargeField): Int? {
            if (field == ChargeField.KernelThreshold) return readKernelThreshold()
            val key = field.settingKey()
            return if (field == ChargeField.Bypass) {
                Settings.System.getString(context.contentResolver, key)?.toIntOrNull()
            } else {
                Settings.Global.getString(context.contentResolver, key)?.toIntOrNull()
            }
        }

        override fun write(field: ChargeField, value: Int?): Boolean {
            if (field == ChargeField.KernelThreshold) {
                return value != null && writeKernelThreshold(value)
            }
            val namespace = if (field == ChargeField.Bypass) "system" else "global"
            val key = field.settingKey()
            val command = if (value == null) "settings delete $namespace $key"
            else "settings put $namespace $key $value"
            return runAsRoot(command) != null && read(field) == value
        }
    })

    fun read(): ChargingControlState {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return ChargingControlState(
            limit = Settings.Global.getInt(context.contentResolver, ChargingControlKeys.LIMIT, 80)
                .coerceIn(20, 100),
            remember = Settings.Global.getInt(context.contentResolver, ChargingControlKeys.REMEMBER, 0) == 1,
            active = Settings.Global.getInt(context.contentResolver, ChargingControlKeys.ACTIVE, 0) == 1,
            quickPauseEnabled = Settings.Global.getInt(context.contentResolver, ChargingControlKeys.OWNED_BYPASS, 0) == 1,
            bypassEnabled = Settings.System.getInt(context.contentResolver, ChargingControlKeys.BYPASS, 0) == 1,
            pluggedIn = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0,
            batteryLevel = if (level >= 0 && scale > 0) level * 100 / scale else null,
        )
    }

    fun setRemember(enabled: Boolean): Boolean =
        writeSetting("global", ChargingControlKeys.REMEMBER, if (enabled) 1 else 0) &&
            (Settings.Global.getInt(context.contentResolver, ChargingControlKeys.REMEMBER, -1) == if (enabled) 1 else 0)

    fun setLimit(limit: Int): Boolean = engine.setLimit(limit)

    fun pauseAtCurrentLevel(): Boolean {
        val state = read()
        val level = state.batteryLevel ?: return false
        if (!state.pluggedIn || level !in 20..100) return false
        return engine.setLimit(level, quickPause = true)
    }

    fun stopQuickPause(): Boolean = engine.stopQuickPause()

    fun isQuickPauseApplied(state: ChargingControlState = read()): Boolean =
        ChargingControlPolicy.isQuickPauseApplied(
            state.quickPauseEnabled, state.active, state.limit, readKernelThreshold()
        )

    fun toggleQuickPause(): Boolean =
        if (isQuickPauseApplied()) {
            stopQuickPause()
        } else {
            pauseAtCurrentLevel()
        }

    fun restoreAfterBoot() {
        val resolver = context.contentResolver
        if (Settings.Global.getInt(resolver, ChargingControlKeys.REMEMBER, 0) == 1) {
            if (Settings.Global.getInt(resolver, ChargingControlKeys.ACTIVE, 0) == 1) {
                writeKernelThreshold(read().limit)
            }
        } else if (Settings.Global.getInt(resolver, ChargingControlKeys.OWNED_BYPASS, 0) == 1) {
            stopQuickPause()
        } else if (Settings.Global.getInt(resolver, ChargingControlKeys.LIMIT, -1) in 20..100) {
            writeSetting("global", ChargingControlKeys.ACTIVE, 0)
            if (Settings.Global.getInt(resolver, ChargingControlKeys.OWNED_BYPASS, 0) == 1) {
                writeSetting("global", ChargingControlKeys.OWNED_BYPASS, 0)
                writeSetting("system", ChargingControlKeys.BYPASS, 0)
            }
            writeSetting("global", ChargingControlKeys.SAMSUNG_PROTECTION, 0)
            writeKernelThreshold(100)
        }
    }

    companion object {
        private fun ChargeField.settingKey(): String = when (this) {
            ChargeField.KernelThreshold -> error("Kernel threshold is not a settings key")
            ChargeField.Limit -> ChargingControlKeys.LIMIT
            ChargeField.Active -> ChargingControlKeys.ACTIVE
            ChargeField.Protection -> ChargingControlKeys.SAMSUNG_PROTECTION
            ChargeField.NativeThreshold -> ChargingControlKeys.SAMSUNG_THRESHOLD
            ChargeField.OwnedBypass -> ChargingControlKeys.OWNED_BYPASS
            ChargeField.Bypass -> ChargingControlKeys.BYPASS
            ChargeField.SavedProtection -> ChargingControlKeys.SAVED_PROTECTION
            ChargeField.SavedThreshold -> ChargingControlKeys.SAVED_THRESHOLD
        }

        private fun readKernelThreshold(): Int? =
            runAsRoot("cat ${ChargingControlKeys.KERNEL_THRESHOLD}")?.toIntOrNull()

        private fun writeKernelThreshold(limit: Int): Boolean =
            runAsRoot("echo $limit > ${ChargingControlKeys.KERNEL_THRESHOLD} && cat ${ChargingControlKeys.KERNEL_THRESHOLD}")
                ?.toIntOrNull() == limit

        private fun writeSetting(namespace: String, key: String, value: Int): Boolean =
            runAsRoot("settings put $namespace $key $value") != null

        private fun runAsRoot(command: String): String? = try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true).start()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                null
            } else {
                if (process.exitValue() == 0) process.inputStream.bufferedReader().readText().trim()
                else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
