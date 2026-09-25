package io.github.soclear.oneuix.ui.charging

import io.github.soclear.oneuix.common.ChargingControlPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ChargingControlPolicyTest {
    @Test
    fun acceptsEveryIntegerFromTwentyThroughHundred() {
        (20..100).forEach { limit ->
            assertEquals(limit, ChargingControlPolicy.requireSupportedLimit(limit))
        }
    }

    @Test
    fun rejectsUnsupportedChargeLimitsBeforeWritingSystemSettings() {
        assertThrows(IllegalArgumentException::class.java) {
            ChargingControlPolicy.requireSupportedLimit(19)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChargingControlPolicy.requireSupportedLimit(101)
        }
    }

    @Test
    fun pausesAtLimitAndResumesBelowLimit() {
        assertFalse(ChargingControlPolicy.shouldPause(57, 58, pluggedIn = true))
        assertTrue(ChargingControlPolicy.shouldPause(58, 58, pluggedIn = true))
        assertTrue(ChargingControlPolicy.shouldPause(85, 58, pluggedIn = true))
        assertFalse(ChargingControlPolicy.shouldPause(58, 58, pluggedIn = false))
        assertFalse(ChargingControlPolicy.shouldPause(19, 20, pluggedIn = true))
    }

    @Test
    fun samsungNativeProtectionCoversOnlyItsSupportedSteps() {
        assertTrue(ChargingControlPolicy.hasNativeThreshold(80))
        assertTrue(ChargingControlPolicy.hasNativeThreshold(85))
        assertTrue(ChargingControlPolicy.hasNativeThreshold(90))
        assertTrue(ChargingControlPolicy.hasNativeThreshold(95))
        assertFalse(ChargingControlPolicy.hasNativeThreshold(79))
        assertFalse(ChargingControlPolicy.hasNativeThreshold(93))
        assertFalse(ChargingControlPolicy.hasNativeThreshold(100))
    }

    @Test
    fun staleQuickPauseFlagDoesNotCountAsApplied() {
        assertFalse(ChargingControlPolicy.isQuickPauseApplied(true, true, 93, 100))
        assertTrue(ChargingControlPolicy.isQuickPauseApplied(true, true, 93, 93))
        assertFalse(ChargingControlPolicy.isQuickPauseApplied(false, true, 93, 93))
        assertFalse(ChargingControlPolicy.isQuickPauseApplied(true, false, 93, 93))
    }
}
