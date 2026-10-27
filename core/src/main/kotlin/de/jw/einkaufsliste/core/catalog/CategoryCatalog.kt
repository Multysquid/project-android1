package de.jw.einkaufsliste.core.catalog

import de.jw.einkaufsliste.core.model.CategoryDef

/**
 * The German default categories the app is seeded with, and which OpenStreetMap
 * shop types usually sell them. Users can edit names and shop types later; the
 * [CategoryDef.slug] stays stable and is what Gemini and the barcode lookup map to.
 */
object CategoryCatalog {
    const val FALLBACK_SLUG = "sonstiges"

    private const val SUPERMARKET = "shop=supermarket"
    private const val CONVENIENCE = "shop=convenience"
    private const val KIOSK = "shop=kiosk"
    private const val CHEMIST = "shop=chemist"
    private const val DEPARTMENT = "shop=department_store"
    private const val VARIETY = "shop=variety_store"
    private const val DIY = "shop=doityourself"
    private const val HARDWARE = "shop=hardware"

    val defaults: List<CategoryDef> = listOf(
        def("obst_gemuese", "Obst & Gemüse", "🥦", SUPERMARKET, CONVENIENCE, "shop=greengrocer", "shop=farm"),
        def("brot_backwaren", "Brot & Backwaren", "🥖", "shop=bakery", SUPERMARKET, CONVENIENCE, "shop=pastry"),
        def("milch_eier", "Milchprodukte & Eier", "🥛", SUPERMARKET, CONVENIENCE, "shop=dairy", "shop=cheese", "shop=farm"),
        def("fleisch_wurst", "Fleisch & Wurst", "🥩", "shop=butcher", SUPERMARKET),
        def("fisch", "Fisch", "🐟", "shop=seafood", SUPERMARKET),
        def("tiefkuehl", "Tiefkühlkost", "🧊", SUPERMARKET, "shop=frozen_food"),
        def("vorrat", "Nudeln, Reis & Vorräte", "🍝", SUPERMARKET, CONVENIENCE),
        def("suesses_snacks", "Süßes & Snacks", "🍫", SUPERMARKET, CONVENIENCE, KIOSK, "shop=confectionery", "shop=chocolate"),
        def("getraenke", "Getränke", "🥤", "shop=beverages", SUPERMARKET, CONVENIENCE, KIOSK),
        def("alkohol", "Bier, Wein & Spirituosen", "🍷", "shop=alcohol", "shop=wine", "shop=beverages", SUPERMARKET),
        def("kaffee_tee", "Kaffee & Tee", "☕", SUPERMARKET, "shop=coffee", "shop=tea", CHEMIST),
        def("drogerie", "Drogerie & Körperpflege", "🧴", CHEMIST, SUPERMARKET, "shop=cosmetics", "shop=perfumery"),
        def("haushalt", "Haushalt & Reinigung", "🧽", CHEMIST, SUPERMARKET, "shop=household", HARDWARE, VARIETY, DEPARTMENT),
        def("baby", "Baby & Kind", "🍼", CHEMIST, "shop=baby_goods", SUPERMARKET),
        def("tier", "Tierbedarf", "🐾", "shop=pet", SUPERMARKET, CHEMIST),
        def("medikamente", "Apotheke & Gesundheit", "💊", "amenity=pharmacy", CHEMIST, "shop=medical_supply"),
        def("baumarkt", "Baumarkt & Werkzeug", "🔨", DIY, HARDWARE, "shop=trade"),
        def("garten", "Garten & Pflanzen", "🌱", "shop=garden_centre", "shop=florist", DIY),
        def("elektronik", "Elektronik", "🔌", "shop=electronics", "shop=mobile_phone", "shop=computer", DEPARTMENT),
        def("batterien_lampen", "Batterien & Leuchtmittel", "🔋", SUPERMARKET, CHEMIST, "shop=electronics", DIY, HARDWARE),
        def("schreibwaren", "Schreibwaren & Bücher", "✏️", "shop=stationery", "shop=books", CHEMIST, KIOSK, "shop=newsagent"),
        def("kleidung", "Kleidung & Schuhe", "👕", "shop=clothes", "shop=shoes", DEPARTMENT),
        def(FALLBACK_SLUG, "Sonstiges", "🛒", SUPERMARKET, DEPARTMENT, VARIETY, "shop=general"),
    ).mapIndexed { index, category -> category.copy(sortOrder = index) }

    private val bySlug: Map<String, CategoryDef> = defaults.associateBy { it.slug }

    fun bySlug(slug: String?): CategoryDef? = slug?.let { bySlug[it] }

    val slugs: Set<String> get() = bySlug.keys

    private fun def(slug: String, name: String, emoji: String, vararg tags: String) =
        CategoryDef(slug = slug, name = name, emoji = emoji, osmTags = tags.toList(), sortOrder = 0)

    /** German labels for the shop tags used above, for display ("Supermarkt"). */
    private val tagLabels: Map<String, String> = mapOf(
        SUPERMARKET to "Supermarkt",
        CONVENIENCE to "Lebensmittelladen",
        KIOSK to "Kiosk",
        CHEMIST to "Drogerie",
        DEPARTMENT to "Kaufhaus",
        VARIETY to "Sonderpostenmarkt",
        DIY to "Baumarkt",
        HARDWARE to "Eisenwaren",
        "shop=greengrocer" to "Obst- und Gemüseladen",
        "shop=farm" to "Hofladen",
        "shop=bakery" to "Bäckerei",
        "shop=pastry" to "Konditorei",
        "shop=dairy" to "Milchladen",
        "shop=cheese" to "Käseladen",
        "shop=butcher" to "Metzgerei",
        "shop=seafood" to "Fischladen",
        "shop=frozen_food" to "Tiefkühlmarkt",
        "shop=confectionery" to "Süßwarenladen",
        "shop=chocolate" to "Schokoladengeschäft",
        "shop=beverages" to "Getränkemarkt",
        "shop=alcohol" to "Spirituosenladen",
        "shop=wine" to "Weinhandlung",
        "shop=coffee" to "Kaffeegeschäft",
        "shop=tea" to "Teeladen",
        "shop=cosmetics" to "Kosmetikladen",
        "shop=perfumery" to "Parfümerie",
        "shop=household" to "Haushaltswaren",
        "shop=baby_goods" to "Babyfachmarkt",
        "shop=pet" to "Zoohandlung",
        "amenity=pharmacy" to "Apotheke",
        "shop=medical_supply" to "Sanitätshaus",
        "shop=trade" to "Baustoffhandel",
        "shop=garden_centre" to "Gartencenter",
        "shop=florist" to "Blumenladen",
        "shop=electronics" to "Elektronikmarkt",
        "shop=mobile_phone" to "Handyladen",
        "shop=computer" to "Computerladen",
        "shop=stationery" to "Schreibwarenladen",
        "shop=books" to "Buchhandlung",
        "shop=newsagent" to "Zeitschriftenladen",
        "shop=clothes" to "Bekleidungsgeschäft",
        "shop=shoes" to "Schuhgeschäft",
        "shop=general" to "Gemischtwarenladen",
    )

    /** All shop tags the app knows a label for, used to offer choices when editing a category. */
    val knownShopTags: List<String> get() = tagLabels.keys.toList()

    /** "shop=supermarket" -> "Supermarkt"; unknown tags fall back to their value ("shop=bicycle" -> "bicycle"). */
    fun shopTagLabel(tag: String): String = tagLabels[tag] ?: tag.substringAfter('=').replace('_', ' ')
}
