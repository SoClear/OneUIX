package io.github.soclear.oneuix.ui

import io.github.soclear.oneuix.ui.category.Category
import java.io.Serializable

sealed interface SettingDestination : Serializable {
    data class Detail(val category: Category) : SettingDestination
    enum class Extra : SettingDestination {
        NetworkSpeed, HideOngoingActivityMediaApps, DisableWritingToolkitApps, HideScreenshotDetectionApps, NfcCards
    }
}
