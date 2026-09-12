package io.github.soclear.oneuix.common

import kotlinx.serialization.Serializable

@Serializable
data class PowerMenuAction(
    val name: String,
    val visible: Boolean = true,
) {
    companion object {
        const val POWER = "power"
        const val DATA_MODE = "data_mode"
        const val RESTART = "restart"
        const val SAFE_MODE = "safe_mode"
        const val LOCK_DOWN_MODE = "lock_down_mode"
        const val EMERGENCY_CALL = "emergency_call"
        const val MEDICAL_INFO = "medical_info"
        const val SIDE_KEY_SETTINGS = "side_key_settings"
        const val FORCE_RESTART_MESSAGE = "force_restart_message"
        const val RESTART_SYSTEMUI = "restart_systemui"
        const val RESTART_RECOVERY = "restart_recovery"
        const val RESTART_DOWNLOAD = "restart_download"

        val DEFAULT_ORDER: List<String> = listOf(
            POWER,
            DATA_MODE,
            RESTART,
            SAFE_MODE,
            LOCK_DOWN_MODE,
            EMERGENCY_CALL,
            MEDICAL_INFO,
            SIDE_KEY_SETTINGS,
            FORCE_RESTART_MESSAGE,
            RESTART_SYSTEMUI,
            RESTART_RECOVERY,
            RESTART_DOWNLOAD,
        )

        fun defaultPreferences(): List<PowerMenuAction> =
            DEFAULT_ORDER.map { PowerMenuAction(it) }

        fun normalize(actions: List<PowerMenuAction>): List<PowerMenuAction> {
            val knownActions = actions
                .filter { it.name in DEFAULT_ORDER }
                .distinctBy { it.name }
            val missingActions = DEFAULT_ORDER
                .filterNot { name -> knownActions.any { it.name == name } }
                .map { PowerMenuAction(it) }

            return knownActions + missingActions
        }
    }
}
