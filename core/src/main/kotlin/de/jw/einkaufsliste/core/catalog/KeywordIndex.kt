package de.jw.einkaufsliste.core.catalog

import java.text.Normalizer
import java.util.Locale

/**
 * Finds the most specific keyword in a typed German item name; the matching engine behind
 * [CategoryGuesser] and [AssortmentGuesser].
 *
 * Keywords match as parts of words, so compounds work ("Vollmilch", "Hackfleisch");
 * keywords of up to three letters ("Ei", "Tee") and keywords written with a leading `=`
 * ("=kohl" must not match "Holzkohle") match whole words only. The longest keyword wins;
 * on equal length the one further right wins (the head of a German compound), and of
 * keywords with the same text the one listed first.
 */
internal class KeywordIndex<T>(val keywords: List<Keyword<T>>) {
    /**
     * A keyword as joined [words] ("H-Milch" -> "hmilch") and what it stands for. Keywords
     * of up to three letters must be [wholeWord] ones, as [parse] makes them.
     */
    class Keyword<out T>(val text: String, val value: T, val wholeWord: Boolean)

    init {
        require(keywords.all { it.wholeWord || it.text.length > SHORT_KEYWORD_LENGTH }) { "short part keyword" }
    }

    private val wholeWordKeywords: Map<String, Keyword<T>> =
        keywords.filter { it.wholeWord }.groupBy { it.text }.mapValues { (_, same) -> same.first() }

    /** Part keywords by their first two letters (see [pairKey]), so [find] only tries those that can match. */
    private val partKeywords: Map<Int, List<Keyword<T>>> =
        keywords.filter { !it.wholeWord }.distinctBy { it.text }.groupBy { pairKey(it.text, 0) }

    /** The winning keyword in [words] (as returned by [KeywordIndex.words]), or null. */
    fun find(words: List<String>): Keyword<T>? {
        if (words.isEmpty()) return null
        val joined = words.joinToString("")

        var best: Keyword<T>? = null
        var bestEnd = -1
        fun offer(keyword: Keyword<T>, end: Int) {
            val current = best
            val better = current == null ||
                keyword.text.length > current.text.length ||
                (keyword.text.length == current.text.length && end > bestEnd)
            if (better) {
                best = keyword
                bestEnd = end
            }
        }

        var offset = 0
        for (word in words) {
            offset += word.length
            wholeWordKeywords[word]?.let { offer(it, offset) }
        }
        for (start in 0 until joined.length - 1) {
            val candidates = partKeywords[pairKey(joined, start)] ?: continue
            for (keyword in candidates) {
                if (joined.startsWith(keyword.text, start)) offer(keyword, start + keyword.text.length)
            }
        }
        return best
    }

    private fun pairKey(text: String, start: Int): Int = (text[start].code shl 16) or text[start + 1].code

    companion object {
        private const val SHORT_KEYWORD_LENGTH = 3

        private val combiningMarks = Regex("\\p{Mn}+")
        private val nonLetters = Regex("[^a-z]+")
        private val quantity = Regex(
            "\\d+(?:[.,]\\d+)?\\s*(?:kg|g|gr|gramm|mg|l|ltr|liter|ml|cl|dl|stk|st|stueck|" +
                "pck|pkg|pack|packung|packungen|dose|dosen|flasche|flaschen|becher|beutel|er|x|%)?(?![a-z])",
        )

        /** Lowercase words without umlauts, accents, digits, units and punctuation ("1,5 l H-Milch" -> [h, milch]). */
        fun words(text: String): List<String> {
            var s = text.lowercase(Locale.GERMAN)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss")
            s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(combiningMarks, "")
            s = quantity.replace(s, " ")
            return nonLetters.split(s).filter { it.isNotEmpty() }
        }

        /**
         * Parses keyword lists, comma- or line-separated and written as typed (umlauts are
         * normalized), each for one value. A leading `=` makes a keyword match whole words only.
         */
        fun <T> parse(dictionary: List<Pair<T, String>>): List<Keyword<T>> =
            dictionary.flatMap { (value, list) ->
                list.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { raw ->
                    val text = words(raw.removePrefix("=")).joinToString("")
                    if (text.isEmpty()) null
                    else Keyword(text, value, raw.startsWith("=") || text.length <= SHORT_KEYWORD_LENGTH)
                }
            }
    }
}
