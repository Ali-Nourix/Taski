package io.github.alinourix.taski.core.domain.model

/**
 * A tag or project colour: one of the palette hues, stored by name so every
 * theme renders "blue" as its own blue, or a custom `#rrggbb`.
 */
sealed interface ColorToken {
    val code: String

    enum class Palette(override val code: String) : ColorToken {
        Red("red"), Orange("orange"), Yellow("yellow"), Green("green"), Cyan("cyan"),
        Blue("blue"), Purple("purple"), Pink("pink"), Gray("gray"),
    }

    data class Custom(val rgb: Int) : ColorToken {
        override val code: String get() = "#%06x".format(rgb and 0xFFFFFF)
    }

    companion object {
        private val HEX = Regex("#[0-9a-fA-F]{6}")

        fun fromCode(code: String?): ColorToken = when {
            code == null -> Palette.Gray
            HEX.matches(code) -> Custom(code.substring(1).toInt(16))
            else -> Palette.entries.firstOrNull { it.code == code } ?: Palette.Gray
        }

        /** The next hue for a new tag, so a fresh list is not all one colour. */
        fun next(existing: Collection<ColorToken>): ColorToken {
            val hues = Palette.entries - Palette.Gray
            return hues.firstOrNull { it !in existing } ?: hues[existing.size % hues.size]
        }
    }
}
