package io.github.soclear.oneuix.common

object ChargingControlPolicy {
    fun requireSupportedLimit(value: Int): Int {
        require(value in 20..100) { "Unsupported charging limit: $value" }
        return value
    }

    fun shouldPause(level: Int, limit: Int, pluggedIn: Boolean): Boolean =
        pluggedIn && level >= 20 && level >= requireSupportedLimit(limit)

    fun hasNativeThreshold(limit: Int): Boolean = limit in 80..95 && limit % 5 == 0

    fun isQuickPauseApplied(
        owned: Boolean,
        active: Boolean,
        savedLimit: Int,
        kernelThreshold: Int?,
    ): Boolean = owned && active && kernelThreshold == savedLimit
}
