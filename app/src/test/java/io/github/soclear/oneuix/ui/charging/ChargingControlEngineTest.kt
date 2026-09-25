package io.github.soclear.oneuix.ui.charging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingControlEngineTest {
    private class FakeGateway : ChargingControlGateway {
        val values = mutableMapOf(
            ChargeField.KernelThreshold to 100,
            ChargeField.Limit to 80,
            ChargeField.Active to 0,
            ChargeField.Protection to 1,
            ChargeField.NativeThreshold to 95,
            ChargeField.OwnedBypass to 0,
            ChargeField.Bypass to 0,
        )
        var failOn: ChargeField? = null

        override fun read(field: ChargeField): Int? = values[field]

        override fun write(field: ChargeField, value: Int?): Boolean {
            if (value == null) values.remove(field) else values[field] = value
            return field != failOn
        }
    }

    @Test
    fun failedKernelWriteRestoresOldValueWithoutChangingSettings() {
        val gateway = FakeGateway().apply { failOn = ChargeField.KernelThreshold }
        val before = gateway.values.toMap()

        assertFalse(ChargingControlEngine(gateway).setLimit(93))

        assertEquals(before, gateway.values)
    }

    @Test
    fun laterSettingFailureRollsBackKernelAndProtection() {
        val gateway = FakeGateway().apply { failOn = ChargeField.Active }
        val before = gateway.values.toMap()

        assertFalse(ChargingControlEngine(gateway).setLimit(93))

        assertEquals(before, gateway.values)
    }

    @Test
    fun stoppingQuickPauseRestoresExistingSamsungProtection() {
        val gateway = FakeGateway()
        val engine = ChargingControlEngine(gateway)

        assertTrue(engine.setLimit(93, quickPause = true))
        assertEquals(0, gateway.read(ChargeField.Protection))
        assertTrue(engine.stopQuickPause())

        assertEquals(1, gateway.read(ChargeField.Protection))
        assertEquals(95, gateway.read(ChargeField.NativeThreshold))
        assertEquals(95, gateway.read(ChargeField.KernelThreshold))
        assertEquals(93, gateway.read(ChargeField.Limit))
        assertEquals(0, gateway.read(ChargeField.Active))
        assertEquals(0, gateway.read(ChargeField.Bypass))
    }

    @Test
    fun stoppingQuickPauseLeavesProtectionOffWhenItWasOff() {
        val gateway = FakeGateway().apply { values[ChargeField.Protection] = 0 }
        val engine = ChargingControlEngine(gateway)

        assertTrue(engine.setLimit(93, quickPause = true))
        assertTrue(engine.stopQuickPause())

        assertEquals(0, gateway.read(ChargeField.Protection))
        assertEquals(100, gateway.read(ChargeField.KernelThreshold))
    }
}
