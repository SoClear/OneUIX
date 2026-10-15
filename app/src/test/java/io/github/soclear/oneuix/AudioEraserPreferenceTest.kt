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
        assertTrue(old.systemUI.qs.hideQsBarDataUsage)
    }

    @Test fun backupRoundTripKeepsTileOptionAndOtherSettings() {
        val preference = Preference(systemUI = Preference.SystemUI(
            qs = Preference.SystemUI.QS(audioEraserQsTile = true, qsClockTextSize = 42f)
        ))
        val restored = IgnoreUnknownKeysJson.decodeFromString<Preference>(
            IgnoreUnknownKeysJson.encodeToString(preference)
        )
        assertTrue(restored.systemUI.qs.audioEraserQsTile)
        assertEquals(42f, restored.systemUI.qs.qsClockTextSize)
    }
}
