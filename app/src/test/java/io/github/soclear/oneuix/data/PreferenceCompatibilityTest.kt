package io.github.soclear.oneuix.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceCompatibilityTest {
    @Test
    fun oldPreferenceWithoutInteractionUsesSafeDefaults() {
        val oldJson = """{"android":{"allowAllRotation":true}}"""

        val preference = IgnoreUnknownKeysJson.decodeFromString<Preference>(oldJson)

        assertTrue(preference.android.allowAllRotation)
        assertTrue(preference.hiddenMainMenuCategories.isEmpty())
        assertFalse(preference.interaction.wakeOnNotification)
        assertFalse(preference.interaction.notificationDirectOpenAfterAuth)
        assertFalse(preference.interaction.homeSwipeDownSearch)
        assertTrue(preference.interaction.keepOriginalSwipeUpSearch)
        assertFalse(preference.interaction.customizeLockscreenSwipeDistance)
        assertFalse(preference.interaction.autoStartEnabled)
        assertTrue(preference.interaction.autoStartPackages.isEmpty())
        assertEquals(8, preference.interaction.wakeDurationSeconds)
        assertEquals(96f, preference.interaction.homeSwipeDownThresholdDp)
        assertEquals(1f, preference.interaction.lockscreenSwipeDistanceScale)
    }

    @Test
    fun hiddenMainMenuCategoriesRoundTripWithoutChangingFeatureSettings() {
        val original = Preference(
            hiddenMainMenuCategories = setOf("Camera", "Weather"),
            interaction = Preference.Interaction(homeSwipeDownSearch = true),
        )

        val restored = IgnoreUnknownKeysJson.decodeFromString<Preference>(
            IgnoreUnknownKeysJson.encodeToString(Preference.serializer(), original)
        )

        assertEquals(setOf("Camera", "Weather"), restored.hiddenMainMenuCategories)
        assertTrue(restored.interaction.homeSwipeDownSearch)
    }

    @Test
    fun autoStartSelectionRoundTrips() {
        val original = Preference(
            interaction = Preference.Interaction(
                autoStartEnabled = true,
                autoStartPackages = setOf("com.example.alpha", "com.example.beta"),
            )
        )

        val restored = IgnoreUnknownKeysJson.decodeFromString<Preference>(
            IgnoreUnknownKeysJson.encodeToString(Preference.serializer(), original)
        )

        assertTrue(restored.interaction.autoStartEnabled)
        assertEquals(
            setOf("com.example.alpha", "com.example.beta"),
            restored.interaction.autoStartPackages,
        )
    }
}
