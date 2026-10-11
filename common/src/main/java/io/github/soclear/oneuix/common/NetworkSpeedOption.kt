package io.github.soclear.oneuix.common

typealias NetworkSpeedLayout = NetworkSpeedOption.Layout

object NetworkSpeedOption {
    enum class Layout {
        SYSTEM,
        SPLIT_HORIZONTAL,
        SPLIT_VERTICAL,
        ACTIVE_DIRECTION,
    }

    val MARKERS_UP = listOf("↑", "▲", "△", "⇧", "⇡", "⌃", "↕", "^", "⬆")
    val MARKERS_DOWN = listOf("↓", "▼", "▽", "⇩", "⇣", "⌄", "↕", "v", "⬇")

    data class UnitOption(
        val bits: Boolean,
        val base: Int,
        val byteSuffix: String,
        val kiloPrefix: String,
        val megaPrefix: String,
    )

    val UNITS = listOf(
        UnitOption(bits = false, base = 1024, byteSuffix = "B", kiloPrefix = "K", megaPrefix = "M"),
        UnitOption(bits = false, base = 1000, byteSuffix = "B", kiloPrefix = "K", megaPrefix = "M"),
        UnitOption(bits = true, base = 1000, byteSuffix = "b", kiloPrefix = "Kb", megaPrefix = "Mb"),
        UnitOption(bits = true, base = 1024, byteSuffix = "b", kiloPrefix = "Kib", megaPrefix = "Mib"),
    )
}
