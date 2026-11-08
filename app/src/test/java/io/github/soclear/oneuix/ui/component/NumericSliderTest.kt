package io.github.soclear.oneuix.ui.component

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumericSliderTest {
    @Test
    fun exactPaddingAcceptsBothLocaleDecimalAndDecimalPoint() {
        assertEquals(4f, parseSliderInput("4", 0f..100f, Locale.US))
        assertEquals(4f, parseSliderInput("4.0", 0f..100f, Locale.US))
        assertEquals(4.1f, parseSliderInput("4,1", 0f..100f, Locale.FRANCE))
        assertEquals(4.1f, parseSliderInput("4.1", 0f..100f, Locale.FRANCE))
        assertEquals(0f, parseSliderInput("0", 0f..100f, Locale.US))
        assertEquals(100f, parseSliderInput("100", 0f..100f, Locale.US))
    }

    @Test
    fun invalidAndOutOfRangeEditsCannotBeCommitted() {
        listOf("", ".", "-", "NaN", "Infinity", "1e1", "1,000", "4dp", "-0.1", "100.01")
            .forEach { assertNull(it, parseSliderInput(it, 0f..100f, Locale.US)) }
    }

    @Test
    fun dragAndNumericEntryUseDisplayPrecision() {
        assertEquals(4f, normalizeSliderValue(4.04f, 0f..100f, 1))
        assertEquals(4.1f, normalizeSliderValue(4.05f, 0f..100f, 1))
        assertEquals(1.23f, normalizeSliderValue(1.234f, 0.5f..2f, 2))
        assertEquals(1.24f, normalizeSliderValue(1.235f, 0.5f..2f, 2))
    }

    @Test
    fun integerAndNetworkGapSettingsRetainTheirExistingSteps() {
        assertEquals(5f, normalizeSliderValue(4.8f, 4f..20f, 0, steps = 15))
        assertEquals(20f, normalizeSliderValue(20f, 4f..20f, 0, steps = 15))
        assertEquals(0f, normalizeSliderValue(0f, 0f..24f, 0, steps = 23))
        assertEquals(1.2f, normalizeSliderValue(1.24f, 0f..3f, 2, steps = 29))
        assertEquals(3f, normalizeSliderValue(3f, 0f..3f, 2, steps = 29))
    }

    @Test
    fun negativeNetworkSpacingRetainsItsSignedRangeAndIntegerSteps() {
        val range = -8f..16f
        assertEquals(-8f, parseSliderInput("-8", range, Locale.US))
        assertEquals(-3.5f, parseSliderInput("-3,5", range, Locale.FRANCE))
        assertEquals(-4f, normalizeSliderValue(-4.2f, range, 0, steps = 23))
        assertEquals(-8f, normalizeSliderValue(-8f, range, 0, steps = 23))
        assertEquals(0f, normalizeSliderValue(0f, range, 0, steps = 23))
        assertEquals(16f, normalizeSliderValue(16f, range, 0, steps = 23))
        assertNull(parseSliderInput("-9", range, Locale.US))
        assertNull(parseSliderInput("17", range, Locale.US))
    }
}
