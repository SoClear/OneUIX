package io.github.soclear.oneuix.ui.charging

import io.github.soclear.oneuix.common.ChargingControlPolicy

internal enum class ChargeField {
    KernelThreshold,
    Limit,
    Active,
    Protection,
    NativeThreshold,
    OwnedBypass,
    Bypass,
    SavedProtection,
    SavedThreshold,
}

internal interface ChargingControlGateway {
    fun read(field: ChargeField): Int?
    fun write(field: ChargeField, value: Int?): Boolean
}

internal class ChargingControlEngine(private val gateway: ChargingControlGateway) {
    fun setLimit(limit: Int, quickPause: Boolean = false): Boolean {
        ChargingControlPolicy.requireSupportedLimit(limit)
        val priorProtection = gateway.read(ChargeField.Protection) ?: 0
        val priorThreshold = gateway.read(ChargeField.NativeThreshold) ?: 100
        return change { tx ->
            // Verify the physical control before changing any protection settings.
            if (!tx.put(ChargeField.KernelThreshold, limit)) return@change false
            if (quickPause) {
                if (!tx.put(ChargeField.SavedProtection, priorProtection)) return@change false
                if (!tx.put(ChargeField.SavedThreshold, priorThreshold)) return@change false
            }
            if (ChargingControlPolicy.hasNativeThreshold(limit)) {
                if (!tx.put(ChargeField.NativeThreshold, limit)) return@change false
                if (!tx.put(ChargeField.Protection, 1)) return@change false
            } else if (!tx.put(ChargeField.Protection, 0)) {
                return@change false
            }
            if (!tx.put(ChargeField.Limit, limit)) return@change false
            if (!tx.put(ChargeField.Active, 1)) return@change false
            if (quickPause) {
                if (!tx.put(ChargeField.OwnedBypass, 1)) return@change false
                if (!tx.put(ChargeField.Bypass, 1)) return@change false
            } else if (gateway.read(ChargeField.OwnedBypass) == 1) {
                if (!tx.put(ChargeField.OwnedBypass, 0)) return@change false
                if (!tx.put(ChargeField.Bypass, 0)) return@change false
                if (!tx.put(ChargeField.SavedProtection, null)) return@change false
                if (!tx.put(ChargeField.SavedThreshold, null)) return@change false
            }
            gateway.read(ChargeField.KernelThreshold) == limit
        }
    }

    fun stopQuickPause(): Boolean {
        if (gateway.read(ChargeField.OwnedBypass) != 1) return false
        val savedProtection = gateway.read(ChargeField.SavedProtection)
        val savedThreshold = gateway.read(ChargeField.SavedThreshold)
        val protection = savedProtection ?: gateway.read(ChargeField.Protection) ?: 0
        val threshold = savedThreshold ?: gateway.read(ChargeField.NativeThreshold) ?: 100
        val kernelTarget = if (protection == 1 && threshold in 20..100) threshold else 100
        return change { tx ->
            if (!tx.put(ChargeField.Active, 0)) return@change false
            if (!tx.put(ChargeField.OwnedBypass, 0)) return@change false
            if (!tx.put(ChargeField.Bypass, 0)) return@change false
            if (savedThreshold != null && !tx.put(ChargeField.NativeThreshold, savedThreshold)) {
                return@change false
            }
            if (savedProtection != null && !tx.put(ChargeField.Protection, savedProtection)) {
                return@change false
            }
            if (!tx.put(ChargeField.KernelThreshold, kernelTarget)) return@change false
            if (!tx.put(ChargeField.SavedProtection, null)) return@change false
            tx.put(ChargeField.SavedThreshold, null)
        }
    }

    private fun change(action: (Transaction) -> Boolean): Boolean {
        val transaction = Transaction(gateway)
        if (action(transaction)) return true
        transaction.rollback()
        return false
    }

    private class Transaction(private val gateway: ChargingControlGateway) {
        private val before = linkedMapOf<ChargeField, Int?>()

        fun put(field: ChargeField, value: Int?): Boolean {
            if (!before.containsKey(field)) {
                val previous = gateway.read(field)
                if (field == ChargeField.KernelThreshold && previous == null) return false
                before[field] = previous
            }
            return gateway.write(field, value)
        }

        fun rollback() {
            before.entries.toList().asReversed().forEach { (field, value) ->
                gateway.write(field, value)
            }
        }
    }
}
