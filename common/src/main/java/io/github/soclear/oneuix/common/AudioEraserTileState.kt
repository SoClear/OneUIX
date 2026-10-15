package io.github.soclear.oneuix.common

/** Samsung mode values are Off=0, On=1 and Dim=2. These states match QS Tile's API. */
object AudioEraserTileState {
    const val UNAVAILABLE = 0
    const val INACTIVE = 1
    const val ACTIVE = 2

    fun resolve(enabled: Boolean, packageName: String?, mode: Int): Int = when {
        !enabled || packageName.isNullOrBlank() || mode !in 0..1 -> UNAVAILABLE
        mode == 1 -> ACTIVE
        else -> INACTIVE
    }
}
