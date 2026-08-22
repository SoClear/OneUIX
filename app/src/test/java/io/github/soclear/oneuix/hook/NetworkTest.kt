package io.github.soclear.oneuix.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkTest {
    @Test
    fun displaysWhenEitherDirectionExceedsThreshold() {
        assertTrue(shouldDisplayNetworkSpeed(1025f, 0f, 1))
        assertTrue(shouldDisplayNetworkSpeed(0f, 1025f, 1))
        assertFalse(shouldDisplayNetworkSpeed(1024f, 1024f, 1))
    }
}
