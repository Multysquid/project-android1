package de.jw.einkaufsliste.core.text

import java.util.Locale

object ItemNames {
    private val whitespace = Regex("\\s+")

    /** Normalizes an item name for comparisons ("  Milch  1,5% " -> "milch 1,5%"). */
    fun normalize(name: String): String =
        name.trim().replace(whitespace, " ").lowercase(Locale.GERMAN)
}
