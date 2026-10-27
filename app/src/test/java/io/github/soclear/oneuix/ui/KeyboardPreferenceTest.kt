package io.github.soclear.oneuix.ui

import io.github.soclear.oneuix.common.IgnoreUnknownKeysJson
import io.github.soclear.oneuix.common.Preference
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardPreferenceTest {
    @Test
    fun shuangpinExperimentIsDisabledByDefault() {
        assertFalse(Preference().other.unlockKeyboardShuangpinLayout)
    }

    @Test
    fun oldPreferencesKeepExperimentDisabled() {
        val preference = IgnoreUnknownKeysJson.decodeFromString<Preference>(
            """{"other":{"setWeatherProviderCN":true}}"""
        )
        assertFalse(preference.other.unlockKeyboardShuangpinLayout)
        assertTrue(preference.other.setWeatherProviderCN)
    }

    @Test
    fun enabledExperimentSurvivesSerializationWithoutChangingOtherOptions() {
        val original = Preference()
        val enabled = original.copy(
            other = original.other.copy(unlockKeyboardShuangpinLayout = true)
        )
        val restored = Json.decodeFromString<Preference>(Json.encodeToString(enabled))
        assertTrue(restored.other.unlockKeyboardShuangpinLayout)
        assertEquals(original.other, restored.other.copy(unlockKeyboardShuangpinLayout = false))
    }
}
