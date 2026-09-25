package io.github.soclear.oneuix.common

object ChargingControlKeys {
    const val LIMIT = "oneuix_charge_limit"
    const val ACTIVE = "oneuix_charge_active"
    const val REMEMBER = "oneuix_charge_remember"
    const val OWNED_BYPASS = "oneuix_charge_paused"
    const val BYPASS = "pass_through"
    const val SAMSUNG_PROTECTION = "protect_battery"
    const val SAMSUNG_THRESHOLD = "battery_protection_threshold"
    const val KERNEL_THRESHOLD = "/sys/class/power_supply/battery/batt_full_capacity"
    const val SAVED_PROTECTION = "oneuix_charge_saved_protection"
    const val SAVED_THRESHOLD = "oneuix_charge_saved_threshold"
}
