package de.jw.einkaufsliste.core.catalog

/**
 * A kind of product a shop sells (its "Sortiment"). Matching works on these: an item is
 * found in every shop whose assortment contains the item's assortment (see [StoreCatalog]
 * and [AssortmentGuesser]). They are finer than the list's Unterlisten, which only group
 * the list, and are fixed: Gemini and the cache store them by [slug].
 */
enum class Assortment(val label: String) {
    OBST_GEMUESE("Obst & Gemüse"),
    BROT_BACKWAREN("Brot & Backwaren"),
    MILCH_EIER("Milchprodukte & Eier"),
    FLEISCH_WURST("Fleisch & Wurst"),
    FISCH("Fisch"),
    TIEFKUEHL("Tiefkühlkost"),

    /** Dry and canned food: pasta, rice, flour, oil, spices, sauces, cereals, spreads. */
    VORRAT("Vorräte"),
    SUESSES_SNACKS("Süßes & Snacks"),

    /** Non-alcoholic drinks, water, juice, soft drinks. */
    GETRAENKE("Getränke"),
    ALKOHOL("Bier, Wein & Spirituosen"),
    KAFFEE_TEE("Kaffee & Tee"),

    /** Shampoo, shower gel, toothpaste, deodorant, razors, cotton pads, hygiene products. */
    KOERPERPFLEGE("Körperpflege"),

    /** Make-up, nail polish, perfume. */
    KOSMETIK("Kosmetik & Parfum"),

    /** Detergents, cleaning agents, dishwasher tabs, toilet paper, kitchen roll, bin bags, foil. */
    HAUSHALT_REINIGUNG("Putzen & Waschen"),
    BABY("Baby"),
    TIER("Tierbedarf"),

    /** Health products sold outside pharmacies: plasters, vitamins, condoms, thermometers. */
    GESUNDHEIT("Gesundheit"),

    /** Medicines that in Germany are sold in pharmacies only (also non-prescription ones). */
    MEDIKAMENTE("Medikamente"),

    /** Pots, pans, dishes, cutlery, kitchen tools, storage boxes. */
    HAUSHALTSWAREN("Küche & Haushaltswaren"),

    /** Candles, picture frames, vases, cushions, decoration. */
    DEKO_WOHNEN("Deko & Wohnen"),
    MOEBEL("Möbel"),

    /** Bed linen, towels, curtains, blankets. */
    HEIMTEXTIL("Heimtextilien"),

    /** Tools, screws, glue, paint, building and repair material, extension leads, sockets, timers. */
    WERKZEUG("Werkzeug & Baumarkt"),

    /** Plants, seeds, potting soil, garden tools, barbecue. */
    GARTEN("Garten & Grill"),

    /** Batteries, button cells, light bulbs, fairy lights, simple cables (extension leads are [WERKZEUG]). */
    ELEKTRO_KLEINTEILE("Batterien, Lampen & Kabel"),

    /** Devices and accessories: headphones, chargers, phone cases, USB sticks, printer ink, TVs, computers. */
    ELEKTRONIK("Elektronik & Zubehör"),

    /** Kettle, vacuum cleaner, hair dryer, coffee machine, washing machine. */
    HAUSHALTSGERAETE("Haushaltsgeräte"),

    /** Pens, paper, notebooks, envelopes, office supplies. */
    SCHREIBWAREN("Schreibwaren & Büro"),
    BUECHER("Bücher"),

    /** Newspapers, magazines, cigarettes, tobacco, lottery, phone credit. */
    ZEITSCHRIFTEN_TABAK("Zeitschriften & Tabak"),
    KLEIDUNG("Kleidung"),
    SCHUHE("Schuhe"),
    SPIELZEUG("Spielzeug"),
    SPORT("Sport & Freizeit"),
    FAHRRAD("Fahrrad"),
    AUTO("Auto"),

    /** Gift wrap, greeting cards, balloons, party supplies, small gifts. */
    PARTY_GESCHENK("Party & Geschenke"),

    /** Cut flowers, bouquets and potted flowers such as orchids. */
    BLUMEN("Blumen"),
    ;

    /** Stable lowercase id ("obst_gemuese"), used by Gemini and the cache. */
    val slug: String get() = name.lowercase()

    companion object {
        private val bySlug: Map<String, Assortment> = entries.associateBy { it.slug }

        fun bySlug(slug: String?): Assortment? = slug?.let { bySlug[it.trim().lowercase()] }

        /** Parses a comma-separated list of slugs; unknown ones are skipped. */
        fun parseSlugs(text: String?): Set<Assortment> =
            text.orEmpty().split(',').mapNotNullTo(LinkedHashSet()) { bySlug(it) }

        /** The inverse of [parseSlugs]. */
        fun toSlugs(assortments: Collection<Assortment>): String = assortments.joinToString(",") { it.slug }
    }
}
