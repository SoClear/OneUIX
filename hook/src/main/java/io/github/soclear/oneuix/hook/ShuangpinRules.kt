package io.github.soclear.oneuix.hook

/** Pure rules copied from the enabled XT9 Shuangpin branches in Samsung Keyboard 5.9.40.48. */
internal object ShuangpinRules {
    fun isSymbol(code: Int): Boolean = code in 62000..62026 || code in 62032..62065

    fun keyboardId(language: Int, original: Int, shuangpin: Boolean): Int =
        if (shuangpin && language == 225 && original == (225 or 0x0d00)) {
            language or 0x0f00
        } else original

    fun needsSymbolKey(
        shuangpin: Boolean,
        phonepad: Boolean,
        label: CharSequence?,
        codes: IntArray,
    ): Boolean = shuangpin && !phonepad && label.isNullOrEmpty() && codes.any(::isSymbol)

    fun renderSymbols(
        raw: String,
        lookup: (Int) -> String?,
        fallback: (Char) -> String = { it.toString() },
    ): String = buildString {
        raw.forEachIndexed { index, char ->
            val name = if (isSymbol(char.code)) lookup(char.code) else null
            if (name == null) {
                append(fallback(char))
            } else if (index > 0 && raw[index - 1].code == 62026) {
                // XT9 uses '*' before a final to mark a syllable without an initial.
                if (isNotEmpty()) deleteCharAt(lastIndex)
                append(name.replaceFirstChar { it.uppercaseChar() })
            } else {
                append(name)
            }
        }
    }
}
