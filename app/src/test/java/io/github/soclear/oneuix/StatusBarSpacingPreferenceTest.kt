package io.github.soclear.oneuix

import io.github.soclear.oneuix.common.IgnoreUnknownKeysJson
import io.github.soclear.oneuix.common.Preference
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBarSpacingPreferenceTest {
    @Test
    fun paddingDefaultsStayDistinctFromExplicitZeroWithoutDisablingControls() {
        val original = Preference.SystemUI.StatusBar(
            modifyStatusBarLeftPadding = true,
            statusBarLeftPaddingDp = 7.5f,
            modifyStatusBarRightPadding = true,
            statusBarRightPaddingDp = 0f,
            modifyInnerStatusBarLeftPadding = true,
            innerStatusBarLeftPaddingDp = 12f,
            modifyInnerStatusBarRightPadding = true,
            innerStatusBarRightPaddingDp = 3f
        )
        val reset = original.copy(statusBarLeftPaddingDp = null, innerStatusBarRightPaddingDp = null)
        val restored = IgnoreUnknownKeysJson.decodeFromString<Preference>(
            IgnoreUnknownKeysJson.encodeToString(Preference(systemUI = Preference.SystemUI(statusBar = reset)))
        ).systemUI.statusBar
        assertNull(restored.statusBarLeftPaddingDp)
        assertNull(restored.innerStatusBarRightPaddingDp)
        assertEquals(0f, restored.statusBarRightPaddingDp)
        assertEquals(12f, restored.innerStatusBarLeftPaddingDp)
        assertTrue(restored.modifyStatusBarLeftPadding)
        assertTrue(restored.modifyStatusBarRightPadding)
        assertTrue(restored.modifyInnerStatusBarLeftPadding)
        assertTrue(restored.modifyInnerStatusBarRightPadding)
        assertNull(Preference.SystemUI.StatusBar().statusBarLeftPaddingDp)
        assertNull(Preference.SystemUI.StatusBar().statusBarRightPaddingDp)
        assertNull(Preference.SystemUI.StatusBar().innerStatusBarLeftPaddingDp)
        assertNull(Preference.SystemUI.StatusBar().innerStatusBarRightPaddingDp)
    }

    @Test
    fun existingSettingsKeepNativeGaps() {
        val preference = IgnoreUnknownKeysJson.decodeFromString<Preference>(
            """{"systemUI":{"statusBar":{"modifyStatusBarRightPadding":true,"statusBarRightPaddingDp":7.5}}}"""
        ).systemUI.statusBar
        assertFalse(preference.modifyStatusIconEndPadding)
        assertFalse(preference.modifyRightClockStartMargin)
        assertNull(preference.statusIconEndPaddingDp)
        assertNull(preference.rightClockStartMarginDp)
        assertEquals(7.5f, preference.statusBarRightPaddingDp)
    }

    @Test
    fun nativeZeroAndCustomGapsSurviveSavingIndependently() {
        for (icons in listOf(null, 0f, 8.5f)) {
            for (clock in listOf(null, 0f, 12f)) {
                val original = Preference.SystemUI.StatusBar(
                    modifyStatusIconEndPadding = true,
                    statusIconEndPaddingDp = icons,
                    modifyRightClockStartMargin = true,
                    rightClockStartMarginDp = clock
                )
                val saved = Preference(systemUI = Preference.SystemUI(statusBar = original))
                val restored = IgnoreUnknownKeysJson.decodeFromString<Preference>(
                    IgnoreUnknownKeysJson.encodeToString(saved)
                )
                assertEquals(original, restored.systemUI.statusBar)
            }
        }
    }
}
