package io.github.soclear.oneuix

import android.service.quicksettings.Tile
import org.junit.Assert.assertEquals
import org.junit.Test

class ChargingLimitTileStateTest {
    @Test
    fun unpluggedTileIsInactiveRatherThanUnavailable() {
        assertEquals(Tile.STATE_INACTIVE, chargingTileState(false, false, 93))
    }

    @Test
    fun appliedQuickPauseIsActive() {
        assertEquals(Tile.STATE_ACTIVE, chargingTileState(true, true, 93))
    }

    @Test
    fun invalidBatteryLevelWhilePluggedInIsUnavailable() {
        assertEquals(Tile.STATE_UNAVAILABLE, chargingTileState(false, true, null))
        assertEquals(Tile.STATE_UNAVAILABLE, chargingTileState(false, true, 19))
    }
}
