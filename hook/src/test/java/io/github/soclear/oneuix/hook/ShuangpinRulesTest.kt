package io.github.soclear.oneuix.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuangpinRulesTest {
    @Test
    fun correctsOnlyTheSimplifiedChineseShuangpinDatabase() {
        assertEquals(0x0fe1, ShuangpinRules.keyboardId(225, 0x0de1, true))
        assertEquals(0x0de1, ShuangpinRules.keyboardId(225, 0x0de1, false))
        assertEquals(0x0de0, ShuangpinRules.keyboardId(224, 0x0de0, true))
        assertEquals(0x0de2, ShuangpinRules.keyboardId(226, 0x0de2, true))
    }

    @Test
    fun doesNotReplaceAlreadyCorrectOrPhonepadDatabases() {
        assertEquals(0x0fe1, ShuangpinRules.keyboardId(225, 0x0fe1, true))
        assertEquals(0x0ae1, ShuangpinRules.keyboardId(225, 0x0ae1, true))
        assertEquals(0x0de1, ShuangpinRules.keyboardId(224, 0x0de1, true))
        assertEquals(0x1de1, ShuangpinRules.keyboardId(225, 0x1de1, true))
    }

    @Test
    fun recognizesOnlyXt9InitialAndFinalSymbolRanges() {
        listOf(62000, 62026, 62032, 62065).forEach { assertTrue(ShuangpinRules.isSymbol(it)) }
        listOf(-1, 97, 61999, 62027, 62031, 62066, 0xffff).forEach {
            assertFalse(ShuangpinRules.isSymbol(it))
        }
    }

    @Test
    fun restoresUnlabelledShuangpinSymbolKeys() {
        assertTrue(ShuangpinRules.needsSymbolKey(true, false, null, intArrayOf(62004)))
        assertTrue(ShuangpinRules.needsSymbolKey(true, false, "", intArrayOf(62032, 62035)))
    }

    @Test
    fun preservesOtherLayoutsLabelledKeysAndPlaceholders() {
        val codes = intArrayOf(62004)
        assertFalse(ShuangpinRules.needsSymbolKey(false, false, null, codes))
        assertFalse(ShuangpinRules.needsSymbolKey(true, true, null, codes))
        assertFalse(ShuangpinRules.needsSymbolKey(true, false, "StrEmpty", codes))
        assertFalse(ShuangpinRules.needsSymbolKey(true, false, "a", codes))
        assertFalse(ShuangpinRules.needsSymbolKey(true, false, null, intArrayOf()))
        assertFalse(ShuangpinRules.needsSymbolKey(true, false, null, intArrayOf(-255, -257, 97)))
    }

    @Test
    fun rendersTheNativeBufferEvenWhenOriginalFormatterDroppedAllSymbols() {
        val raw = symbols(62004, 62032)
        assertEquals("Da", ShuangpinRules.renderSymbols(raw, names::get))
    }

    @Test
    fun rendersSyllablesWithoutAnInitialLikeTheOriginalEnabledBranch() {
        assertEquals("Ang", ShuangpinRules.renderSymbols(symbols(62026, 62035), names::get))
        assertEquals("DaAng", ShuangpinRules.renderSymbols(symbols(62004, 62032, 62026, 62035), names::get))
    }

    @Test
    fun preservesNormalTextAndUsesHostFallbackForUnknownPrivateSymbols() {
        assertEquals("hello", ShuangpinRules.renderSymbols("hello", names::get))
        assertEquals("", ShuangpinRules.renderSymbols("", names::get))
        val raw = symbols(62004) + '\uE000' + "'" + symbols(62032)
        assertEquals("D'a", ShuangpinRules.renderSymbols(raw, names::get) {
            if (it == '\uE000') "" else it.toString()
        })
    }

    @Test
    fun unmappedSymbolsAreNotSilentlyDroppedByThePureRule() {
        val raw = symbols(62005)
        assertEquals(raw, ShuangpinRules.renderSymbols(raw, names::get))
    }

    private fun symbols(vararg codes: Int) = codes.map(Int::toChar).joinToString("")

    private val names = mapOf(62004 to "D", 62026 to "*", 62032 to "a", 62035 to "ang")
}
