package de.jw.einkaufsliste.core.products

import de.jw.einkaufsliste.core.catalog.CategoryCatalog
import de.jw.einkaufsliste.core.catalog.CategoryGuesser
import de.jw.einkaufsliste.core.model.ProductInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.util.Locale

/** Barcode lookups in Open Food Facts and its sister databases. */
object OpenFactsApi {
    /** Hosts tried in order: food, other products, cosmetics, pet food. */
    val HOSTS: List<String> = listOf(
        "https://world.openfoodfacts.org",
        "https://world.openproductsfacts.org",
        "https://world.openbeautyfacts.org",
        "https://world.openpetfoodfacts.org",
    )

    const val SOURCE_FOOD = "openfoodfacts"
    const val SOURCE_PRODUCTS = "openproductsfacts"
    const val SOURCE_BEAUTY = "openbeautyfacts"
    const val SOURCE_PET_FOOD = "openpetfoodfacts"

    private const val FIELDS =
        "product_name,product_name_de,generic_name_de,generic_name,brands,quantity,categories_tags"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Product API v2 URL asking only for the fields we need, German first. */
    fun productUrl(host: String, barcode: String): String =
        "${host.trim().trimEnd('/')}/api/v2/product/${barcode.trim()}.json?fields=$FIELDS&lc=de"

    /**
     * Short source name for a host, stored as [ProductInfo.source]:
     * `https://world.openfoodfacts.org` -> `openfoodfacts`. Other hosts give their second-level domain label.
     */
    fun sourceForHost(host: String): String {
        val hostname = host.trim().substringAfter("://").substringBefore('/').substringBefore(':')
            .lowercase(Locale.ROOT)
        val labels = hostname.split('.').filter { it.isNotEmpty() }
        return labels.firstOrNull { it.endsWith("facts") }
            ?: labels.getOrNull(labels.size - 2)
            ?: hostname
    }

    /** Parses a product API v2 answer; null when the product is unknown or has no usable name. */
    fun parse(json: String, barcode: String, source: String): ProductInfo? {
        val root = runCatching { this.json.parseToJsonElement(json) }.getOrNull() as? JsonObject ?: return null
        if (!isFound(root["status"])) return null
        val product = root["product"] as? JsonObject ?: return null
        val name = NAME_FIELDS.firstNotNullOfOrNull { product.text(it) } ?: return null
        val brand = product.text("brands")?.split(',')?.firstNotNullOfOrNull { it.trim().takeIf(String::isNotEmpty) }
        val tags = categoriesTags(product["categories_tags"])
        return ProductInfo(
            barcode = barcode,
            name = name,
            brand = brand,
            quantity = product.text("quantity"),
            categorySlug = categorySlugFor(tags, name, source),
            source = source,
        )
    }

    /**
     * Maps Open*Facts `categories_tags` (and the name as a hint) to a [CategoryCatalog] slug.
     *
     * Pet food, baby food and frozen food win when any tag says so. Otherwise the most specific tag (OFF lists
     * them general to specific, so the last one) that matches a rule decides, then generic drink tags. When no tag
     * fits, the name goes through [CategoryGuesser] (food and product databases only), then the source decides:
     * cosmetics -> `drogerie`, pet food -> `tier`, food -> `vorrat`, anything else -> `sonstiges`.
     * Products from the cosmetics database are never put into a food category, products from the pet food
     * database always land in `tier`.
     */
    fun categorySlugFor(categoriesTags: List<String>, productName: String?, source: String): String {
        if (source == SOURCE_PET_FOOD) return TIER
        val tags = categoriesTags.mapNotNull(::normalizeTag).filter { it.value !in GENERIC_TAGS }
        for (rule in GLOBAL_RULES) {
            if (tags.any { rule.matches(it) }) return rule.slug
        }
        val rules = if (source == SOURCE_BEAUTY) NON_FOOD_RULES else SPECIFIC_RULES
        for (tag in tags.asReversed()) {
            EXACT[tag.value]?.let { return it }
            rules.firstOrNull { it.matches(tag) }?.let { return it.slug }
        }
        if (source != SOURCE_BEAUTY) {
            for (tag in tags.asReversed()) {
                if (WEAK_DRINK_RULE.matches(tag)) return WEAK_DRINK_RULE.slug
            }
            guessFromName(productName)?.let { return it }
        }
        return when (source) {
            SOURCE_BEAUTY -> "drogerie"
            SOURCE_FOOD -> "vorrat"
            else -> CategoryCatalog.FALLBACK_SLUG
        }
    }

    /** EAN-13, EAN-8, UPC-A (12) and UPC-E (8) with a valid check digit. */
    fun isValidBarcode(code: String): Boolean {
        if (code.isEmpty() || code.any { it !in '0'..'9' }) return false
        return when (code.length) {
            12, 13 -> hasValidCheckDigit(code)
            8 -> hasValidCheckDigit(code) || (code[0] in '0'..'1' && hasValidCheckDigit(expandUpcE(code)))
            else -> false
        }
    }

    /** Expands an 8-digit UPC-E code (number system, six digits, check digit) to the 12-digit UPC-A form. */
    fun expandUpcE(code: String): String {
        require(code.length == 8 && code.all { it in '0'..'9' }) { "UPC-E needs 8 digits" }
        val ns = code[0]
        val d = code.substring(1, 7)
        val check = code[7]
        val body = when (d[5]) {
            '0', '1', '2' -> "${d[0]}${d[1]}${d[5]}0000${d[2]}${d[3]}${d[4]}"
            '3' -> "${d[0]}${d[1]}${d[2]}00000${d[3]}${d[4]}"
            '4' -> "${d[0]}${d[1]}${d[2]}${d[3]}00000${d[4]}"
            else -> "${d[0]}${d[1]}${d[2]}${d[3]}${d[4]}0000${d[5]}"
        }
        return "$ns$body$check"
    }

    /** GS1 mod-10: weights 3, 1, 3, ... from the digit left of the check digit. */
    private fun hasValidCheckDigit(code: String): Boolean {
        var sum = 0
        for (i in 0 until code.length - 1) {
            val digit = code[code.length - 2 - i] - '0'
            sum += if (i % 2 == 0) digit * 3 else digit
        }
        return (10 - sum % 10) % 10 == code.last() - '0'
    }

    private val NAME_FIELDS = listOf("product_name_de", "product_name", "generic_name_de", "generic_name")

    private fun isFound(status: JsonElement?): Boolean {
        val primitive = status as? JsonPrimitive ?: return false
        if (primitive is JsonNull) return false
        if (primitive.doubleOrNull == 1.0) return true
        return primitive.content.trim().lowercase(Locale.ROOT) in FOUND_STATUSES
    }

    /** `status` values of a found product (API v2 uses 1, v3 the words). */
    private val FOUND_STATUSES = setOf("1", "success", "success_with_warnings")

    private fun categoriesTags(element: JsonElement?): List<String> = when (element) {
        is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }
        is JsonNull, null -> emptyList()
        is JsonPrimitive -> element.content.split(',')
        else -> emptyList()
    }

    /** Trimmed text with whitespace runs collapsed; null when missing, not text or blank. */
    private fun JsonObject.text(key: String): String? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.content.trim().replace(WHITESPACE, " ").takeIf { it.isNotEmpty() }
    }

    private val WHITESPACE = Regex("\\s+")

    private fun guessFromName(name: String?): String? {
        if (name.isNullOrBlank()) return null
        // Only a hint: a failing guesser must never break a barcode lookup.
        val slug = runCatching { CategoryGuesser.guess(name) }.getOrNull()
        return slug?.takeIf { CategoryCatalog.bySlug(it) != null }
    }

    // --- Category rules ---------------------------------------------------------------------------

    /** A category tag without language prefix, lowercased, umlauts spelled out, split into `-` words. */
    private class Tag(val value: String, val words: Set<String>)

    private fun normalizeTag(raw: String): Tag? {
        val trimmed = raw.trim().lowercase(Locale.ROOT)
        val withoutLang = if (trimmed.length >= 3 && trimmed[2] == ':') trimmed.substring(3) else trimmed
        val value = withoutLang
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            .replace(' ', '-').replace('_', '-')
            .trim('-')
        if (value.isEmpty()) return null
        return Tag(value, value.split('-').filter { it.isNotEmpty() }.toSet())
    }

    /** Matches a tag that has one of [words] (or contains one of the `-` joined [phrases]) and none of [excluded]. */
    private class Rule(val slug: String, words: String, excluded: String = "", phrases: List<String> = emptyList()) {
        private val words = words.split(' ').filter { it.isNotEmpty() }.toSet()
        private val excluded = excluded.split(' ').filter { it.isNotEmpty() }.toSet()
        private val phrases = phrases.map { "-$it-" }

        fun matches(tag: Tag): Boolean {
            if (tag.words.any { it in excluded }) return false
            if (tag.words.any { it in words }) return true
            val padded = "-${tag.value}-"
            return phrases.any { padded.contains(it) }
        }
    }

    private const val TIER = "tier"

    /** Tags too broad to say anything. */
    private val GENERIC_TAGS = setOf(
        "plant-based-foods-and-beverages",
        "plant-based-foods",
        "cereals-and-potatoes",
        "fruits-and-vegetables-based-foods",
        "foods",
        "groceries",
    )

    /** Checked on all tags before anything else: these decide the shelf no matter what else the product is. */
    private val GLOBAL_RULES = listOf(
        Rule(
            TIER,
            "pet pets cat cats dog dogs petfood catfood dogfood tierfutter tiernahrung katzenfutter hundefutter",
            "hot corn chili tongues",
        ),
        Rule(
            "baby",
            "baby babies infant infants toddler toddlers diapers nappies babynahrung saeuglingsnahrung windeln",
            "carrots spinach potatoes corn leaves salads ribs back tomatoes onions beets octopus squid clams shrimps",
        ),
        Rule(
            "tiefkuehl",
            "frozen sorbets tiefkuehl tiefkuehlkost speiseeis",
            phrases = listOf("ice-cream", "ice-creams", "ice-pops", "ice-lollies", "ice-lollipops"),
        ),
    )

    private val NON_FOOD_RULES = listOf(
        Rule(
            "drogerie",
            "shampoos shampoo conditioners soaps soap toothpastes toothpaste toothbrushes deodorants deodorant " +
                "cosmetics lotions sunscreens razors shaving mouthwashes tampons sanitary hygiene makeup " +
                "showers shower lipsticks perfumes",
        ),
        Rule(
            "haushalt",
            "detergents detergent dishwashing dishwasher laundry cleaners cleaning softeners household sponges",
        ),
        Rule("batterien_lampen", "batteries battery bulbs lamps batterien leuchtmittel"),
    )

    /** Most specific category for one tag, in priority order. */
    private val FOOD_RULES = listOf(
        Rule(
            "alkohol",
            "alcoholic beers beer wines wine spirits liqueurs liqueur liquors whiskies whiskeys whisky vodkas " +
                "rums gins brandies cognacs ciders champagnes proseccos cavas meads sakes aperitifs schnapps " +
                "lagers ales stouts pilsners bier biere wein weine sekt spirituosen schnaps likoer likoere",
            "non vinegar vinegars gums gum sauces sauce chocolates chocolate candies cakes jellies jelly cooking",
        ),
        Rule(
            "getraenke",
            "juices juice nectars smoothies waters sodas colas lemonades softdrinks energy isotonic " +
                "saefte saft wasser limonaden limonade schorlen",
            "bars jelly jellies candies syrups",
            phrases = listOf("iced-tea", "iced-teas", "ice-tea", "ice-teas", "iced-coffee", "iced-coffees"),
        ),
        Rule(
            "kaffee_tee",
            "coffees coffee teas tea infusions espressos cappuccinos kaffee tee tees",
            "iced ice drinks beverages biscuits cakes creamers liqueurs",
        ),
        Rule(
            "milch_eier",
            "dairies dairy milks milk cheeses cheese yogurts yoghurts yogurt eggs butters butter creams cream " +
                "quarks quark kefirs kefir buttermilks buttermilk margarines curds skyrs skyr fromages " +
                "milch kaese joghurt joghurts eier sahne",
            "chocolate chocolates cocoa ice biscuits cookies cakes crackers noodles pastas pasta candies " +
                "confectioneries sauces sauce peanut peanuts nut nuts coconut",
        ),
        Rule(
            "brot_backwaren",
            "breads bread pastries viennoiseries buns baguettes croissants brioches rusks toasts brot brote " +
                "broetchen backwaren",
            "spreads flours flour mixes crumbs",
        ),
        Rule(
            "fleisch_wurst",
            "meats meat sausages sausage hams ham poultries poultry chickens chicken beef pork turkeys salamis " +
                "salami bacons bacon fleisch wurst wuerste schinken aufschnitt gefluegel",
            "analogues alternatives substitutes vegan vegetarian stocks broths soups sauces flavoured",
        ),
        Rule(
            "fisch",
            "fishes fish seafood seafoods salmons tunas shrimps prawns herrings mackerels sardines mussels " +
                "crustaceans molluscs fisch fische lachs thunfisch garnelen",
            "oils sauces stocks",
        ),
        Rule(
            "obst_gemuese",
            "fruits fruit vegetables vegetable apples bananas potatoes tomatoes berries citrus lettuces " +
                "mushrooms onions carrots avocados obst gemuese kartoffeln",
            "juices juice nectars dried snacks chips crisps jams jam compotes canned preserves pickled pickles " +
                "sauces soups spreads bars based oils fats nuts candied sweetened beverages drinks fillings " +
                "pies tarts flours pastas chocolate covered yogurts purees",
        ),
        Rule(
            "suesses_snacks",
            "chocolates chocolate candies candy confectioneries confectionery biscuits cookies snacks chips " +
                "crisps gummies gummy lollipops bonbons pralines marshmallows wafers bars popcorn pretzels " +
                "crackers sweets gums suessigkeiten suesswaren schokolade kekse",
            "spreads powders baking drinks beverages",
        ),
        Rule(
            "vorrat",
            "pastas pasta rices rice cereals mueslis flakes canned sauces sauce condiments spreads jams jam " +
                "marmalades honeys honey flours flour sugars sugar oils vinegars spices salts soups soup broths " +
                "stocks bouillons noodles legumes pulses lentils chickpeas baking yeasts mustards ketchups " +
                "mayonnaises dressings pickles olives semolinas couscous sweeteners syrups ketchup mustard compotes " +
                "nudeln reis mehl zucker oel essig gewuerze konserven suppen saucen",
            "drinks beverages milks",
        ),
    )

    private val SPECIFIC_RULES = FOOD_RULES + NON_FOOD_RULES

    /** Whole tags whose words would point the wrong way. */
    private val EXACT = mapOf(
        "chocolate-milks" to "milch_eier",
        "flavoured-milks" to "milch_eier",
        "milk-chocolates" to "suesses_snacks",
        "peanut-butters" to "vorrat",
        "nut-butters" to "vorrat",
        "almond-butters" to "vorrat",
        "coconut-milks" to "vorrat",
        "coconut-creams" to "vorrat",
        "tea-based-beverages" to "getraenke",
        "coffee-drinks" to "getraenke",
        "non-alcoholic-beverages" to "getraenke",
        "non-alcoholic-beers" to "alkohol",
        "alcohol-free-beers" to "alkohol",
        "non-alcoholic-wines" to "alkohol",
        "cocoa-powders" to "kaffee_tee",
        "chocolate-powders" to "kaffee_tee",
        "cocoa-and-chocolate-powders" to "kaffee_tee",
        "plant-based-milk-alternatives" to "milch_eier",
        "plant-milks" to "milch_eier",
        "dairy-substitutes" to "milch_eier",
        "milk-substitutes" to "milch_eier",
    )

    /** Generic drink tags, only used when no tag matched a more specific rule. */
    private val WEAK_DRINK_RULE = Rule("getraenke", "beverages drinks getraenke", "foods powders")
}
