package io.github.soclear.oneuix

import io.github.soclear.oneuix.common.IgnoreUnknownKeysJson
import io.github.soclear.oneuix.common.Preference
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEraserPreferenceTest {
    @Test fun existingConfigurationLeavesSamsungBannerEnabled() {
        val old = IgnoreUnknownKeysJson.decodeFromString<Preference>(
            """{"systemUI":{"qs":{"hideQsBarDataUsage":true}}}"""
        )
        assertFalse(old.systemUI.qs.audioEraserQsTile)
        assertFalse(old.systemUI.qs.hideAudioEraserBanner)
        assertFalse(old.systemUI.qs.audioEraserQsTileEnabled)
        assertTrue(old.systemUI.qs.hideQsBarDataUsage)
    }

    @Test fun previousTileOptionKeepsBannerHiddenAfterUpgrade() {
        val old = IgnoreUnknownKeysJson.decodeFromString<Preference>(
            """{"systemUI":{"qs":{"audioEraserQsTile":true}}}"""
        )
        assertTrue(old.systemUI.qs.audioEraserQsTile)
        assertTrue(old.systemUI.qs.hideAudioEraserBanner)
        assertTrue(old.systemUI.qs.audioEraserQsTileEnabled)
    }

    @Test fun bannerOnlyConfigurationDoesNotEnableTile() {
        val preference = IgnoreUnknownKeysJson.decodeFromString<Preference>(
            """{"systemUI":{"qs":{"hideAudioEraserBanner":true}}}"""
        )
        assertTrue(preference.systemUI.qs.hideAudioEraserBanner)
        assertFalse(preference.systemUI.qs.audioEraserQsTile)
        assertFalse(preference.systemUI.qs.audioEraserQsTileEnabled)
    }

    @Test fun disabledParentOverridesRememberedTileChoice() {
        val preference = IgnoreUnknownKeysJson.decodeFromString<Preference>(
            """{"systemUI":{"qs":{"audioEraserQsTile":true,"hideAudioEraserBanner":false}}}"""
        )
        val qs = preference.systemUI.qs
        assertTrue(qs.audioEraserQsTile)
        assertFalse(qs.hideAudioEraserBanner)
        assertFalse(qs.audioEraserQsTileEnabled)
        assertTrue(qs.copy(hideAudioEraserBanner = true).audioEraserQsTileEnabled)
    }

    @Test fun backupRoundTripPreservesEveryParentAndChildCombination() {
        for (hideBanner in listOf(false, true)) {
            for (addTile in listOf(false, true)) {
                val preference = Preference(systemUI = Preference.SystemUI(
                    qs = Preference.SystemUI.QS(
                        hideAudioEraserBanner = hideBanner,
                        audioEraserQsTile = addTile,
                    )
                ))
                val restored = IgnoreUnknownKeysJson.decodeFromString<Preference>(
                    IgnoreUnknownKeysJson.encodeToString(preference)
                )
                assertEquals(preference, restored)
                assertEquals(hideBanner && addTile, restored.systemUI.qs.audioEraserQsTileEnabled)
            }
        }
    }

    @Test fun backupRoundTripKeepsTileOptionAndOtherSettings() {
        val preference = Preference(systemUI = Preference.SystemUI(
            qs = Preference.SystemUI.QS(audioEraserQsTile = true, qsClockTextSize = 42f)
        ))
        val restored = IgnoreUnknownKeysJson.decodeFromString<Preference>(
            IgnoreUnknownKeysJson.encodeToString(preference)
        )
        assertTrue(restored.systemUI.qs.audioEraserQsTile)
        assertTrue(restored.systemUI.qs.hideAudioEraserBanner)
        assertTrue(restored.systemUI.qs.audioEraserQsTileEnabled)
        assertEquals(42f, restored.systemUI.qs.qsClockTextSize)
    }
}
