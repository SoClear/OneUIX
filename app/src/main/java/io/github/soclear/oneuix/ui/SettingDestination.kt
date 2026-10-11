package io.github.soclear.oneuix.ui

import io.github.soclear.oneuix.ui.category.Category
import java.io.Serializable

sealed interface SettingDestination : Serializable {
    data class Detail(val category: Category) : SettingDestination
    data object NetworkSpeed : SettingDestination {
        private fun readResolve(): Any = NetworkSpeed
    }
}
