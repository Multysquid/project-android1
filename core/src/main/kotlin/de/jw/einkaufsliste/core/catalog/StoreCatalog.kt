package de.jw.einkaufsliste.core.catalog

import de.jw.einkaufsliste.core.catalog.Assortment.ALKOHOL
import de.jw.einkaufsliste.core.catalog.Assortment.AUTO
import de.jw.einkaufsliste.core.catalog.Assortment.BABY
import de.jw.einkaufsliste.core.catalog.Assortment.BLUMEN
import de.jw.einkaufsliste.core.catalog.Assortment.BROT_BACKWAREN
import de.jw.einkaufsliste.core.catalog.Assortment.BUECHER
import de.jw.einkaufsliste.core.catalog.Assortment.DEKO_WOHNEN
import de.jw.einkaufsliste.core.catalog.Assortment.ELEKTRONIK
import de.jw.einkaufsliste.core.catalog.Assortment.ELEKTRO_KLEINTEILE
import de.jw.einkaufsliste.core.catalog.Assortment.FAHRRAD
import de.jw.einkaufsliste.core.catalog.Assortment.FISCH
import de.jw.einkaufsliste.core.catalog.Assortment.FLEISCH_WURST
import de.jw.einkaufsliste.core.catalog.Assortment.GARTEN
import de.jw.einkaufsliste.core.catalog.Assortment.GESUNDHEIT
import de.jw.einkaufsliste.core.catalog.Assortment.GETRAENKE
import de.jw.einkaufsliste.core.catalog.Assortment.HAUSHALTSGERAETE
import de.jw.einkaufsliste.core.catalog.Assortment.HAUSHALTSWAREN
import de.jw.einkaufsliste.core.catalog.Assortment.HAUSHALT_REINIGUNG
import de.jw.einkaufsliste.core.catalog.Assortment.HEIMTEXTIL
import de.jw.einkaufsliste.core.catalog.Assortment.KAFFEE_TEE
import de.jw.einkaufsliste.core.catalog.Assortment.KLEIDUNG
import de.jw.einkaufsliste.core.catalog.Assortment.KOERPERPFLEGE
import de.jw.einkaufsliste.core.catalog.Assortment.KOSMETIK
import de.jw.einkaufsliste.core.catalog.Assortment.MEDIKAMENTE
import de.jw.einkaufsliste.core.catalog.Assortment.MILCH_EIER
import de.jw.einkaufsliste.core.catalog.Assortment.MOEBEL
import de.jw.einkaufsliste.core.catalog.Assortment.OBST_GEMUESE
import de.jw.einkaufsliste.core.catalog.Assortment.PARTY_GESCHENK
import de.jw.einkaufsliste.core.catalog.Assortment.SCHREIBWAREN
import de.jw.einkaufsliste.core.catalog.Assortment.SCHUHE
import de.jw.einkaufsliste.core.catalog.Assortment.SPIELZEUG
import de.jw.einkaufsliste.core.catalog.Assortment.SPORT
import de.jw.einkaufsliste.core.catalog.Assortment.SUESSES_SNACKS
import de.jw.einkaufsliste.core.catalog.Assortment.TIEFKUEHL
import de.jw.einkaufsliste.core.catalog.Assortment.TIER
import de.jw.einkaufsliste.core.catalog.Assortment.VORRAT
import de.jw.einkaufsliste.core.catalog.Assortment.WERKZEUG
import de.jw.einkaufsliste.core.catalog.Assortment.ZEITSCHRIFTEN_TABAK
import de.jw.einkaufsliste.core.model.ShopInfo
import java.text.Normalizer
import java.util.Locale

/**
 * A store chain that is common in Germany, with what it sells.
 *
 * [patterns] are matched as whole words against the shop's OSM `brand`, then its `name`,
 * both normalized like [StoreCatalog.normalize] ("dm" matches "dm-drogerie markt" but not
 * "Admiral"). A match counts only when the shop's tag is in [shopTags], so a bakery named
 * "Müller" is not the drugstore chain; an empty [shopTags] accepts any tag.
 *
 * [broad] marks stores that carry a bit of everything (hypermarkets, department and
 * variety stores); items the app cannot classify are looked for there.
 */
data class StoreChain(
    val id: String,
    val name: String,
    val patterns: List<String>,
    val shopTags: Set<String>,
    val assortments: Set<Assortment>,
    val broad: Boolean = false,
)

/** Which shops sell what: popular chains by name, every other shop by its OSM shop type. */
object StoreCatalog {
    // What a kind of store reliably sells all year (no weekly specials). Medicines are
    // pharmacy-only in Germany, so only amenity=pharmacy sells MEDIKAMENTE.

    private val FOOD = setOf(
        OBST_GEMUESE, BROT_BACKWAREN, MILCH_EIER, FLEISCH_WURST, FISCH, TIEFKUEHL,
        VORRAT, SUESSES_SNACKS, GETRAENKE, ALKOHOL, KAFFEE_TEE,
    )

    /** All food plus the everyday shelf: hygiene, cleaning, baby, pet food, batteries, flowers, cigarettes. */
    private val DISCOUNTER = FOOD + setOf(
        KOERPERPFLEGE, HAUSHALT_REINIGUNG, BABY, TIER, ELEKTRO_KLEINTEILE, ZEITSCHRIFTEN_TABAK, BLUMEN,
    )

    /** Full-range supermarkets also keep plasters and vitamins, stationery, greeting cards and kitchen gadgets. */
    private val SUPERMARKET = DISCOUNTER + setOf(GESUNDHEIT, SCHREIBWAREN, PARTY_GESCHENK, HAUSHALTSWAREN)

    /** Hypermarkets (SB-Warenhäuser) add a non-food department. */
    private val HYPERMARKET = SUPERMARKET + setOf(
        KOSMETIK, DEKO_WOHNEN, HEIMTEXTIL, WERKZEUG, GARTEN, ELEKTRONIK, HAUSHALTSGERAETE,
        BUECHER, KLEIDUNG, SPIELZEUG, SPORT, AUTO,
    )
    private val CONVENIENCE = setOf(
        OBST_GEMUESE, BROT_BACKWAREN, MILCH_EIER, FLEISCH_WURST, TIEFKUEHL, VORRAT, SUESSES_SNACKS,
        GETRAENKE, ALKOHOL, KAFFEE_TEE, KOERPERPFLEGE, HAUSHALT_REINIGUNG, ELEKTRO_KLEINTEILE, ZEITSCHRIFTEN_TABAK,
    )
    private val GENERAL = setOf(
        OBST_GEMUESE, BROT_BACKWAREN, MILCH_EIER, VORRAT, SUESSES_SNACKS, GETRAENKE, ALKOHOL, KAFFEE_TEE,
        KOERPERPFLEGE, HAUSHALT_REINIGUNG, HAUSHALTSWAREN, ELEKTRO_KLEINTEILE, SCHREIBWAREN, ZEITSCHRIFTEN_TABAK,
    )
    private val ORGANIC = FOOD + setOf(KOERPERPFLEGE, KOSMETIK, HAUSHALT_REINIGUNG, BABY)
    private val HEALTH_FOOD = setOf(VORRAT, SUESSES_SNACKS, GETRAENKE, KAFFEE_TEE, KOERPERPFLEGE, KOSMETIK, GESUNDHEIT)
    private val INTERNATIONAL_FOOD = FOOD + setOf(KOERPERPFLEGE, HAUSHALT_REINIGUNG)
    private val KIOSK = setOf(SUESSES_SNACKS, GETRAENKE, ALKOHOL, ZEITSCHRIFTEN_TABAK)
    private val PRESS = setOf(ZEITSCHRIFTEN_TABAK, BUECHER, SUESSES_SNACKS, GETRAENKE)
    private val BEVERAGES = setOf(GETRAENKE, ALKOHOL)

    /** dm, Rossmann: hygiene, cosmetics, cleaning, baby, health, an organic food shelf and small household goods. */
    private val DRUGSTORE = setOf(
        KOERPERPFLEGE, KOSMETIK, HAUSHALT_REINIGUNG, BABY, TIER, GESUNDHEIT, VORRAT, SUESSES_SNACKS,
        GETRAENKE, KAFFEE_TEE, HAUSHALTSWAREN, ELEKTRO_KLEINTEILE, SCHREIBWAREN, PARTY_GESCHENK,
    )
    private val PHARMACY = setOf(MEDIKAMENTE, GESUNDHEIT, KOERPERPFLEGE, BABY)
    private val DIY = setOf(
        WERKZEUG, GARTEN, ELEKTRO_KLEINTEILE, HAUSHALT_REINIGUNG, HAUSHALTSWAREN, DEKO_WOHNEN, MOEBEL, AUTO,
    )
    private val GARDEN_CENTRE = setOf(GARTEN, BLUMEN, DEKO_WOHNEN)
    private val ELECTRONICS = setOf(ELEKTRONIK, ELEKTRO_KLEINTEILE, HAUSHALTSGERAETE)

    /** Variety and discount stores (Sonderposten, Ein-Euro-Läden). */
    private val VARIETY = setOf(
        HAUSHALT_REINIGUNG, KOERPERPFLEGE, SUESSES_SNACKS, HAUSHALTSWAREN, DEKO_WOHNEN, HEIMTEXTIL, WERKZEUG,
        GARTEN, ELEKTRO_KLEINTEILE, SCHREIBWAREN, SPIELZEUG, PARTY_GESCHENK, TIER,
    )
    private val DEPARTMENT = setOf(
        KOERPERPFLEGE, KOSMETIK, HAUSHALTSWAREN, DEKO_WOHNEN, HEIMTEXTIL, ELEKTRO_KLEINTEILE, ELEKTRONIK,
        HAUSHALTSGERAETE, SCHREIBWAREN, BUECHER, KLEIDUNG, SCHUHE, SPIELZEUG, SPORT, PARTY_GESCHENK,
    )
    private val FURNITURE = setOf(MOEBEL, DEKO_WOHNEN, HEIMTEXTIL, HAUSHALTSWAREN, ELEKTRO_KLEINTEILE)
    private val HOME_DECO = setOf(DEKO_WOHNEN, HAUSHALTSWAREN, HEIMTEXTIL, PARTY_GESCHENK)
    private val GIFTS = setOf(PARTY_GESCHENK, DEKO_WOHNEN, HAUSHALTSWAREN, SCHREIBWAREN, SPIELZEUG)
    private val FASHION = setOf(KLEIDUNG, SCHUHE)
    private val FASHION_AND_HOME = setOf(KLEIDUNG, HEIMTEXTIL, DEKO_WOHNEN, HAUSHALTSWAREN)
    private val BOOKSHOP = setOf(BUECHER, SCHREIBWAREN, PARTY_GESCHENK, SPIELZEUG)
    private val SPORTS = setOf(SPORT, KLEIDUNG, SCHUHE)
    private val TOYS = setOf(SPIELZEUG, BABY, PARTY_GESCHENK)
    private val BABY_STORE = setOf(BABY, SPIELZEUG, KLEIDUNG, MOEBEL, HEIMTEXTIL)

    // OSM tags a chain's shops carry. Chains with short or common names get narrow sets.

    private val GROCERY_SHOPS = shops("supermarket", "convenience", "hypermarket")
    private val SUPERMARKET_SHOPS = shops("supermarket", "hypermarket")
    private val HYPERMARKET_SHOPS = shops("supermarket", "hypermarket", "department_store")
    private val ORGANIC_SHOPS = shops("supermarket", "organic", "convenience", "health_food")
    private val DRUGSTORE_SHOPS = shops("chemist", "cosmetics")
    private val MUELLER_SHOPS = shops("chemist", "cosmetics", "perfumery", "department_store", "variety_store")
    private val PERFUMERY_SHOPS = shops("perfumery", "cosmetics", "chemist")
    private val DIY_SHOPS = shops("doityourself", "hardware", "trade", "garden_centre")
    private val RAIFFEISEN_SHOPS = shops("doityourself", "agrarian", "garden_centre")
    private val GARDEN_SHOPS = shops("garden_centre", "florist", "doityourself")
    private val ELECTRONICS_SHOPS = shops("electronics", "computer", "hifi", "appliance", "mobile_phone", "electrical")
    private val VARIETY_SHOPS = shops("variety_store", "department_store", "general", "houseware", "gift")
    private val GIFT_SHOPS = shops("gift", "variety_store", "interior_decoration", "houseware", "stationery")
    private val FURNITURE_SHOPS = shops("furniture", "interior_decoration", "bed", "houseware", "department_store")
    private val COFFEE_SHOPS = shops("coffee", "variety_store", "department_store", "houseware", "gift")
    private val PET_SHOPS = shops("pet")
    private val BOOK_SHOPS = shops("books", "stationery", "newsagent")
    private val STATIONERY_SHOPS = shops("stationery", "books", "gift")
    private val DEPARTMENT_SHOPS = shops("department_store", "variety_store", "clothes")
    private val CLOTHES_SHOPS = shops("clothes", "boutique", "fashion", "department_store", "variety_store")
    private val SHOE_SHOPS = shops("shoes", "clothes")
    private val TOY_SHOPS = shops("toys", "baby_goods")
    private val SPORT_SHOPS = shops("sports", "outdoor", "clothes", "shoes", "bicycle")
    private val BABY_SHOPS = shops("baby_goods", "toys", "clothes")
    private val BEVERAGE_SHOPS = shops("beverages", "alcohol", "wine")
    private val CAR_SHOPS = shops("car_parts", "car_repair", "tyres", "car")
    private val PRESS_SHOPS = shops("kiosk", "newsagent", "books", "convenience")

    /**
     * Popular chains, most specific first: the first chain that matches a shop wins.
     * Otherwise grouped by kind, the most common first, which is the order of [exampleChains].
     */
    val chains: List<StoreChain> = listOf(
        // Discounters, supermarkets and drugstores. Netto Marken-Discount and the Danish Netto
        // (Stavenhagen) both say "Netto" on many signs and sell the same, so they are one chain.
        chain("aldi", "Aldi", GROCERY_SHOPS, DISCOUNTER, "aldi"),
        chain("lidl", "Lidl", GROCERY_SHOPS, DISCOUNTER, "lidl"),
        chain(
            "rewe_getraenkemarkt", "Rewe Getränkemarkt", BEVERAGE_SHOPS, BEVERAGES,
            "rewe getraenkemarkt", "rewe getraenke",
        ),
        chain("rewe", "Rewe", GROCERY_SHOPS, SUPERMARKET, "rewe"),
        chain(
            "e_center", "E center", HYPERMARKET_SHOPS, HYPERMARKET,
            "e center", "ecenter", "edeka center", broad = true,
        ),
        chain(
            "edeka", "Edeka", GROCERY_SHOPS, SUPERMARKET,
            "edeka", "e aktiv markt", "e neukauf", "nah gut", "nah und gut",
        ),
        chain("dm", "dm", DRUGSTORE_SHOPS, DRUGSTORE, "dm"),
        chain("rossmann", "Rossmann", DRUGSTORE_SHOPS, DRUGSTORE, "rossmann"),
        chain("netto", "Netto", GROCERY_SHOPS, DISCOUNTER, "netto"),
        chain("penny", "Penny", GROCERY_SHOPS, DISCOUNTER, "penny"),
        chain(
            "mueller", "Müller", MUELLER_SHOPS, DRUGSTORE + setOf(SPIELZEUG, BUECHER, ELEKTRONIK, DEKO_WOHNEN),
            "mueller",
        ),
        chain("douglas", "Douglas", PERFUMERY_SHOPS, setOf(KOSMETIK), "douglas"),
        chain("norma", "Norma", SUPERMARKET_SHOPS, DISCOUNTER, "norma"),
        chain("tegut", "tegut", GROCERY_SHOPS, SUPERMARKET, "tegut"),
        chain("hit", "Hit", SUPERMARKET_SHOPS, SUPERMARKET, "hit"),
        chain("combi", "Combi", SUPERMARKET_SHOPS, SUPERMARKET, "combi"),
        chain("markant", "Markant", SUPERMARKET_SHOPS, SUPERMARKET, "markant", "buenting"),
        chain("wasgau", "Wasgau", GROCERY_SHOPS, SUPERMARKET, "wasgau"),
        chain("feneberg", "Feneberg", GROCERY_SHOPS, SUPERMARKET, "feneberg"),
        chain("nahkauf", "Nahkauf", GROCERY_SHOPS, SUPERMARKET, "nahkauf"),
        chain("konsum", "Konsum", GROCERY_SHOPS, SUPERMARKET, "konsum"),
        chain("mix_markt", "Mix Markt", GROCERY_SHOPS, INTERNATIONAL_FOOD, "mix markt", "mixmarkt"),
        chain("alnatura", "Alnatura", ORGANIC_SHOPS, ORGANIC, "alnatura"),
        chain("denns", "denn's Biomarkt", ORGANIC_SHOPS, ORGANIC, "denns"),
        chain("bio_company", "Bio Company", ORGANIC_SHOPS, ORGANIC, "bio company"),
        chain("basic", "Basic", shops("supermarket", "organic"), ORGANIC, "basic"),
        chain("vollcorner", "Vollcorner", ORGANIC_SHOPS, ORGANIC, "vollcorner"),
        chain("budni", "Budni", DRUGSTORE_SHOPS, DRUGSTORE, "budni", "budnikowsky"),

        // Home and furniture
        chain(
            "ikea", "IKEA", FURNITURE_SHOPS,
            FURNITURE + setOf(SPIELZEUG, PARTY_GESCHENK, VORRAT, SUESSES_SNACKS, TIEFKUEHL),
            "ikea",
        ),
        chain("jysk", "JYSK", FURNITURE_SHOPS, FURNITURE, "jysk", "daenisches bettenlager"),
        chain("xxxlutz", "XXXLutz", FURNITURE_SHOPS, FURNITURE, "xxxlutz", "xxx lutz"),
        chain("hoeffner", "Höffner", FURNITURE_SHOPS, FURNITURE, "hoeffner"),
        chain("poco", "Poco", FURNITURE_SHOPS, FURNITURE, "poco"),
        chain("roller", "Roller", FURNITURE_SHOPS, FURNITURE, "roller"),
        chain("porta", "porta", FURNITURE_SHOPS, FURNITURE, "porta"),
        chain("moebel_kraft", "Möbel Kraft", FURNITURE_SHOPS, FURNITURE, "moebel kraft"),
        chain("sconto", "Sconto", FURNITURE_SHOPS, FURNITURE, "sconto"),
        chain("moemax", "mömax", FURNITURE_SHOPS, FURNITURE, "moemax"),
        chain("depot", "Depot", GIFT_SHOPS + FURNITURE_SHOPS, HOME_DECO + MOEBEL, "depot"),
        chain("butlers", "Butlers", GIFT_SHOPS + FURNITURE_SHOPS, HOME_DECO, "butlers"),
        chain(
            "zara_home", "Zara Home", FURNITURE_SHOPS + CLOTHES_SHOPS, setOf(HEIMTEXTIL, DEKO_WOHNEN, HAUSHALTSWAREN),
            "zara home",
        ),

        // Car parts, DIY and garden
        chain("atu", "A.T.U", CAR_SHOPS, setOf(AUTO), "a t u", "atu"),
        chain("obi", "OBI", DIY_SHOPS, DIY, "obi"),
        chain("bauhaus", "Bauhaus", DIY_SHOPS, DIY, "bauhaus"),
        chain("hornbach", "Hornbach", DIY_SHOPS, DIY, "hornbach"),
        chain("toom", "toom", DIY_SHOPS, DIY, "toom"),
        chain("hagebaumarkt", "hagebaumarkt", DIY_SHOPS, DIY, "hagebaumarkt", "hagebau"),
        chain("hellweg", "Hellweg", DIY_SHOPS, DIY, "hellweg"),
        chain("globus_baumarkt", "Globus Baumarkt", DIY_SHOPS, DIY, "globus baumarkt", "globus"),
        chain("b1", "B1 Discount Baumarkt", DIY_SHOPS, DIY, "b1"),
        chain("herkules", "Herkules", DIY_SHOPS, DIY, "herkules"),
        chain(
            "raiffeisen", "Raiffeisen-Markt", RAIFFEISEN_SHOPS, setOf(GARTEN, WERKZEUG, TIER, ELEKTRO_KLEINTEILE),
            "raiffeisen",
        ),
        chain("dehner", "Dehner", GARDEN_SHOPS, GARDEN_CENTRE + setOf(TIER, MOEBEL), "dehner"),
        chain("pflanzen_koelle", "Pflanzen-Kölle", GARDEN_SHOPS, GARDEN_CENTRE + MOEBEL, "pflanzen koelle"),
        chain(
            "blume_2000", "Blume 2000", shops("florist", "garden_centre"), setOf(BLUMEN, GARTEN, PARTY_GESCHENK),
            "blume 2000", "blume2000",
        ),

        // Hypermarkets and department stores (after Globus Baumarkt, which "globus" also names;
        // Galeria took over Karstadt and Kaufhof)
        chain("kaufland", "Kaufland", HYPERMARKET_SHOPS, HYPERMARKET, "kaufland", broad = true),
        chain("globus", "Globus", HYPERMARKET_SHOPS, HYPERMARKET + SCHUHE, "globus", broad = true),
        chain("marktkauf", "Marktkauf", HYPERMARKET_SHOPS, HYPERMARKET, "marktkauf", broad = true),
        chain("famila", "Famila", HYPERMARKET_SHOPS, HYPERMARKET, "famila", broad = true),
        chain("v_markt", "V-Markt", HYPERMARKET_SHOPS, HYPERMARKET, "v markt", "vmarkt", broad = true),
        chain("galeria", "Galeria", DEPARTMENT_SHOPS, DEPARTMENT, "galeria", "karstadt", "kaufhof", broad = true),

        // Electronics
        chain("mediamarkt", "MediaMarkt", ELECTRONICS_SHOPS, ELECTRONICS, "mediamarkt", "media markt"),
        chain("saturn", "Saturn", ELECTRONICS_SHOPS, ELECTRONICS, "saturn"),
        chain("expert", "Expert", ELECTRONICS_SHOPS, ELECTRONICS, "expert"),
        chain("euronics", "Euronics", ELECTRONICS_SHOPS, ELECTRONICS, "euronics"),
        chain("medimax", "Medimax", ELECTRONICS_SHOPS, ELECTRONICS, "medimax"),
        chain("conrad", "Conrad", ELECTRONICS_SHOPS, setOf(ELEKTRONIK, ELEKTRO_KLEINTEILE, WERKZEUG), "conrad"),
        chain("cyberport", "Cyberport", ELECTRONICS_SHOPS, setOf(ELEKTRONIK, ELEKTRO_KLEINTEILE), "cyberport"),

        // Toys, books and stationery (Thalia runs the Mayersche shops)
        chain("smyths", "Smyths Toys", TOY_SHOPS, TOYS + ELEKTRONIK, "smyths"),
        chain("rofu", "Rofu Kinderland", TOY_SHOPS, TOYS, "rofu"),
        chain("thalia", "Thalia", BOOK_SHOPS, BOOKSHOP, "thalia", "mayersche"),
        chain("hugendubel", "Hugendubel", BOOK_SHOPS, BOOKSHOP, "hugendubel"),
        chain("weltbild", "Weltbild", BOOK_SHOPS, BOOKSHOP + DEKO_WOHNEN, "weltbild"),
        chain("mcpaper", "McPaper", STATIONERY_SHOPS, setOf(SCHREIBWAREN, PARTY_GESCHENK), "mcpaper", "mc paper"),

        // Shoes, clothing, sport and baby
        chain("deichmann", "Deichmann", SHOE_SHOPS, setOf(SCHUHE), "deichmann"),
        chain("siemes", "Siemes Schuhcenter", SHOE_SHOPS, setOf(SCHUHE), "siemes"),
        chain("reno", "Reno", SHOE_SHOPS, setOf(SCHUHE), "reno"),
        chain("goertz", "Görtz", SHOE_SHOPS, setOf(SCHUHE), "goertz"),
        chain("hm", "H&M", CLOTHES_SHOPS, FASHION, "h m"),
        chain("c_a", "C&A", CLOTHES_SHOPS, setOf(KLEIDUNG), "c a"),
        chain("primark", "Primark", CLOTHES_SHOPS, FASHION + setOf(KOSMETIK, HEIMTEXTIL, DEKO_WOHNEN), "primark"),
        chain("zara", "Zara", CLOTHES_SHOPS, FASHION, "zara"),
        chain("takko", "Takko", CLOTHES_SHOPS, FASHION, "takko"),
        chain(
            "ernstings_family", "Ernsting's family", CLOTHES_SHOPS, FASHION_AND_HOME,
            "ernstings family", "ernstings",
        ),
        chain(
            "kik", "KiK", CLOTHES_SHOPS,
            FASHION_AND_HOME + setOf(SCHUHE, SPIELZEUG, SCHREIBWAREN, PARTY_GESCHENK, HAUSHALT_REINIGUNG),
            "kik",
        ),
        chain("nkd", "NKD", CLOTHES_SHOPS, FASHION_AND_HOME + SPIELZEUG, "nkd"),
        chain("decathlon", "Decathlon", SPORT_SHOPS, SPORTS + FAHRRAD, "decathlon"),
        chain("intersport", "Intersport", SPORT_SHOPS, SPORTS, "intersport"),
        chain("sportscheck", "SportScheck", SPORT_SHOPS, SPORTS, "sportscheck", "sport scheck"),
        chain("babyone", "BabyOne", BABY_SHOPS, BABY_STORE, "babyone", "baby one"),
        chain("baby_walz", "Baby-Walz", BABY_SHOPS, BABY_STORE, "baby walz", "babywalz"),

        // Variety, discount and gift stores
        chain(
            "action", "Action", VARIETY_SHOPS, VARIETY + setOf(KOSMETIK, ELEKTRONIK, SPORT, AUTO, GETRAENKE),
            "action", broad = true,
        ),
        chain("tedi", "TEDi", VARIETY_SHOPS, VARIETY + KOSMETIK, "tedi", broad = true),
        chain(
            "woolworth", "Woolworth", VARIETY_SHOPS, VARIETY + setOf(KOSMETIK, KLEIDUNG, ELEKTRONIK, SPORT, GETRAENKE),
            "woolworth", "woolworths", broad = true,
        ),
        chain(
            "thomas_philipps", "Thomas Philipps", VARIETY_SHOPS,
            VARIETY + setOf(VORRAT, GETRAENKE, ALKOHOL, KAFFEE_TEE, MOEBEL, AUTO),
            "thomas philipps", broad = true,
        ),
        chain(
            "sonderpreis_baumarkt", "Sonderpreis Baumarkt", DIY_SHOPS + VARIETY_SHOPS, DIY + VARIETY + GETRAENKE,
            "sonderpreis baumarkt", "sonderpreis", broad = true,
        ),
        chain("maec_geiz", "Mäc-Geiz", VARIETY_SHOPS, VARIETY + KOSMETIK, "maec geiz", "mac geiz", broad = true),
        chain(
            "pfennigpfeiffer", "Pfennigpfeiffer", VARIETY_SHOPS, VARIETY,
            "pfennigpfeiffer", "pfennig pfeiffer", broad = true,
        ),
        chain("euroshop", "Euroshop", VARIETY_SHOPS, VARIETY, "euroshop", "euro shop", broad = true),
        chain(
            "tiger", "Flying Tiger", GIFT_SHOPS, GIFTS + setOf(ELEKTRO_KLEINTEILE, SUESSES_SNACKS),
            "flying tiger", "tiger",
        ),
        chain("nanu_nana", "Nanu-Nana", GIFT_SHOPS, GIFTS, "nanu nana"),

        // Coffee, pet supplies, beverages, press
        chain("tchibo", "Tchibo", COFFEE_SHOPS, setOf(KAFFEE_TEE, SUESSES_SNACKS, HAUSHALTSGERAETE), "tchibo"),
        chain("fressnapf", "Fressnapf", PET_SHOPS, setOf(TIER), "fressnapf", "maxi zoo"),
        chain("futterhaus", "Das Futterhaus", PET_SHOPS, setOf(TIER), "futterhaus"),
        chain("zoo_co", "Zoo & Co.", PET_SHOPS, setOf(TIER), "zoo co"),
        chain("getraenke_hoffmann", "Getränke Hoffmann", BEVERAGE_SHOPS, BEVERAGES, "getraenke hoffmann"),
        chain("trinkgut", "Trinkgut", BEVERAGE_SHOPS, BEVERAGES, "trinkgut"),
        chain("fristo", "Fristo", BEVERAGE_SHOPS, BEVERAGES, "fristo"),
        chain("orterer", "Orterer", BEVERAGE_SHOPS, BEVERAGES, "orterer"),
        chain("getraenkeland", "Getränkeland", BEVERAGE_SHOPS, BEVERAGES, "getraenkeland"),
        chain("presse_buch", "Presse + Buch", PRESS_SHOPS, PRESS + SCHREIBWAREN, "presse buch"),
        chain("k_kiosk", "k kiosk", PRESS_SHOPS, PRESS, "k kiosk"),
        chain("relay", "Relay", PRESS_SHOPS, PRESS, "relay"),
    )

    /** Assortments of shops that are not a known chain, by OSM tag (`shop=bakery`). */
    val byShopTag: Map<String, Set<Assortment>> = mapOf(
        "shop=supermarket" to SUPERMARKET,
        "shop=hypermarket" to HYPERMARKET,
        "shop=convenience" to CONVENIENCE,
        "shop=general" to GENERAL,
        "shop=kiosk" to KIOSK,
        "shop=newsagent" to setOf(ZEITSCHRIFTEN_TABAK, SUESSES_SNACKS, SCHREIBWAREN),
        "shop=tobacco" to setOf(ZEITSCHRIFTEN_TABAK),
        "shop=lottery" to setOf(ZEITSCHRIFTEN_TABAK),
        "shop=bakery" to setOf(BROT_BACKWAREN),
        "shop=pastry" to setOf(BROT_BACKWAREN, SUESSES_SNACKS),
        "shop=butcher" to setOf(FLEISCH_WURST),
        "shop=seafood" to setOf(FISCH),
        "shop=greengrocer" to setOf(OBST_GEMUESE),
        "shop=farm" to setOf(OBST_GEMUESE, MILCH_EIER),
        "shop=dairy" to setOf(MILCH_EIER),
        "shop=cheese" to setOf(MILCH_EIER),
        "shop=deli" to setOf(FLEISCH_WURST, MILCH_EIER, VORRAT, ALKOHOL),
        "shop=frozen_food" to setOf(TIEFKUEHL),
        "shop=confectionery" to setOf(SUESSES_SNACKS),
        "shop=chocolate" to setOf(SUESSES_SNACKS),
        "shop=spices" to setOf(VORRAT),
        "shop=coffee" to setOf(KAFFEE_TEE),
        "shop=tea" to setOf(KAFFEE_TEE),
        "shop=beverages" to BEVERAGES,
        "shop=alcohol" to setOf(ALKOHOL),
        "shop=wine" to setOf(ALKOHOL),
        "shop=organic" to ORGANIC,
        "shop=health_food" to HEALTH_FOOD,
        "amenity=pharmacy" to PHARMACY,
        "shop=chemist" to DRUGSTORE,
        "shop=medical_supply" to setOf(GESUNDHEIT),
        "shop=nutrition_supplements" to setOf(GESUNDHEIT),
        "shop=cosmetics" to setOf(KOSMETIK, KOERPERPFLEGE),
        "shop=perfumery" to setOf(KOSMETIK),
        "shop=doityourself" to DIY,
        "shop=hardware" to setOf(WERKZEUG, ELEKTRO_KLEINTEILE, GARTEN, HAUSHALTSWAREN),
        "shop=trade" to setOf(WERKZEUG, GARTEN),
        "shop=paint" to setOf(WERKZEUG),
        "shop=agrarian" to setOf(GARTEN, TIER, WERKZEUG),
        "shop=garden_centre" to GARDEN_CENTRE,
        "shop=florist" to setOf(BLUMEN),
        "shop=electronics" to ELECTRONICS,
        "shop=mobile_phone" to setOf(ELEKTRONIK),
        "shop=computer" to setOf(ELEKTRONIK, ELEKTRO_KLEINTEILE),
        "shop=hifi" to setOf(ELEKTRONIK),
        "shop=video_games" to setOf(ELEKTRONIK),
        "shop=appliance" to setOf(HAUSHALTSGERAETE),
        "shop=electrical" to setOf(ELEKTRO_KLEINTEILE),
        "shop=lighting" to setOf(ELEKTRO_KLEINTEILE),
        "shop=stationery" to setOf(SCHREIBWAREN, PARTY_GESCHENK),
        "shop=books" to setOf(BUECHER),
        "shop=gift" to setOf(PARTY_GESCHENK, DEKO_WOHNEN),
        "shop=party" to setOf(PARTY_GESCHENK),
        "shop=craft" to setOf(SCHREIBWAREN, PARTY_GESCHENK),
        "shop=toys" to setOf(SPIELZEUG),
        "shop=games" to setOf(SPIELZEUG),
        "shop=model" to setOf(SPIELZEUG),
        "shop=sports" to setOf(SPORT),
        "shop=outdoor" to setOf(SPORT),
        "shop=fishing" to setOf(SPORT),
        "shop=bicycle" to setOf(FAHRRAD),
        "shop=car_parts" to setOf(AUTO),
        "shop=tyres" to setOf(AUTO),
        "shop=clothes" to setOf(KLEIDUNG),
        "shop=boutique" to setOf(KLEIDUNG),
        "shop=shoes" to setOf(SCHUHE),
        "shop=baby_goods" to setOf(BABY),
        "shop=pet" to setOf(TIER),
        "shop=furniture" to setOf(MOEBEL),
        "shop=bed" to setOf(MOEBEL, HEIMTEXTIL),
        "shop=interior_decoration" to setOf(DEKO_WOHNEN),
        "shop=candles" to setOf(DEKO_WOHNEN),
        "shop=curtain" to setOf(HEIMTEXTIL),
        "shop=fabric" to setOf(HEIMTEXTIL),
        "shop=houseware" to setOf(HAUSHALTSWAREN),
        "shop=household" to setOf(HAUSHALTSWAREN, HAUSHALT_REINIGUNG),
        "shop=department_store" to DEPARTMENT,
        "shop=variety_store" to VARIETY,
    )

    /** OSM tags of shops that carry a bit of everything (see [StoreChain.broad]). */
    val broadShopTags: Set<String> = setOf(
        "shop=department_store",
        "shop=variety_store",
        "shop=general",
        "shop=hypermarket",
    )

    /** German labels for the tags in [byShopTag] that [CategoryCatalog.shopTagLabel] does not know. */
    private val tagLabels: Map<String, String> = mapOf(
        "shop=hypermarket" to "Verbrauchermarkt",
        "shop=tobacco" to "Tabakladen",
        "shop=lottery" to "Lottoannahme",
        "shop=deli" to "Feinkostladen",
        "shop=spices" to "Gewürzladen",
        "shop=organic" to "Bioladen",
        "shop=health_food" to "Reformhaus",
        "shop=nutrition_supplements" to "Nahrungsergänzungsmittel",
        "shop=paint" to "Farbenfachhandel",
        "shop=agrarian" to "Landhandel",
        "shop=hifi" to "HiFi-Geschäft",
        "shop=video_games" to "Videospieleladen",
        "shop=appliance" to "Hausgerätefachhandel",
        "shop=electrical" to "Elektrofachgeschäft",
        "shop=lighting" to "Leuchtengeschäft",
        "shop=gift" to "Geschenkeladen",
        "shop=party" to "Partyladen",
        "shop=craft" to "Bastelladen",
        "shop=toys" to "Spielwarenladen",
        "shop=games" to "Spieleladen",
        "shop=model" to "Modellbauladen",
        "shop=sports" to "Sportgeschäft",
        "shop=outdoor" to "Outdoorladen",
        "shop=fishing" to "Angelladen",
        "shop=bicycle" to "Fahrradladen",
        "shop=car_parts" to "Autoteilehandel",
        "shop=tyres" to "Reifenhandel",
        "shop=boutique" to "Boutique",
        "shop=furniture" to "Möbelhaus",
        "shop=bed" to "Bettenfachgeschäft",
        "shop=interior_decoration" to "Einrichtungsgeschäft",
        "shop=candles" to "Kerzenladen",
        "shop=curtain" to "Gardinengeschäft",
        "shop=fabric" to "Stoffladen",
        "shop=houseware" to "Haushaltswaren",
    )

    private val apostrophes = Regex("['’‘`´ʼ]")
    private val combiningMarks = Regex("\\p{Mn}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")

    /** A chain pattern split into words, with the position of its chain in [chains]. */
    private class ChainPattern(val words: List<String>, val chainIndex: Int)

    private val patternsByFirstWord: Map<String, List<ChainPattern>> = chains
        .flatMapIndexed { index, chain -> chain.patterns.map { ChainPattern(normalize(it).split(' '), index) } }
        .filter { it.words.first().isNotEmpty() }
        .groupBy { it.words.first() }

    /** Lowercase words separated by single spaces, umlauts as ae/oe/ue/ss, other characters dropped. */
    fun normalize(text: String): String {
        val lower = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase(Locale.GERMAN)
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .replace(apostrophes, "")
            .replace(separators, " ")
            .trim()
    }

    /** The chain [shop] belongs to, or null. */
    fun chainFor(shop: ShopInfo): StoreChain? {
        val tag = shop.tag.trim()
        return chainIn(shop.brand, tag) ?: chainIn(shop.name, tag)
    }

    /** What [shop] sells: its chain's assortments, else those of its shop type, else nothing. */
    fun assortmentsOf(shop: ShopInfo): Set<Assortment> =
        chainFor(shop)?.assortments ?: byShopTag[shop.tag.trim()].orEmpty()

    /** True when [shop] carries a bit of everything (chain marked [StoreChain.broad] or a broad shop type). */
    fun isBroad(shop: ShopInfo): Boolean = chainFor(shop)?.broad ?: (shop.tag.trim() in broadShopTags)

    /**
     * Up to [limit] names of popular chains that sell any of [assortments], for "z. B. bei Lidl, dm".
     * Chains come in list order, broad ones after the others ("Schrauben: z. B. bei OBI" helps
     * more than "bei Kaufland"), and at first at most [PER_SHOP_TYPE] other chains of one main shop
     * type, so "Seife" names drugstores too, not four supermarkets. No [assortments] gives the
     * broad chains.
     */
    fun exampleChains(assortments: Set<Assortment>, limit: Int = 4): List<String> {
        val selling = if (assortments.isEmpty()) {
            chains.filter { it.broad }
        } else {
            chains.filter { chain -> chain.assortments.any { it in assortments } }.sortedBy { it.broad }
        }
        val perType = HashMap<String?, Int>()
        val first = selling.filter { chain ->
            if (chain.broad) return@filter false
            val type = chain.shopTags.firstOrNull()
            val count = perType[type] ?: 0
            perType[type] = count + 1
            count < PER_SHOP_TYPE
        }
        return (first + selling).map { it.name }.distinct().take(limit)
    }

    /** See [exampleChains]. */
    private const val PER_SHOP_TYPE = 2

    /**
     * Up to [limit] German names of shop types ("Apotheke", "Baumarkt") that sell any of
     * [assortments]: where people usually buy them, then specialists (types that sell fewer
     * kinds of things first, ties in the order of [byShopTag]). No [assortments] gives the
     * broad shop types.
     */
    fun shopTypeLabels(assortments: Set<Assortment>, limit: Int = 3): List<String> {
        val tags = if (assortments.isEmpty()) {
            broadShopTags.filter { it in byShopTag }
        } else {
            assortments.map(::usualShopTag) + byShopTag.entries
                .filter { (_, sells) -> sells.any { it in assortments } }
                .sortedBy { (_, sells) -> sells.size }
                .map { it.key }
        }
        return tags.map { tagLabels[it] ?: CategoryCatalog.shopTagLabel(it) }.distinct().take(limit)
    }

    /** The shop type people usually buy [assortment] in; [byShopTag] has it selling [assortment]. */
    private fun usualShopTag(assortment: Assortment): String = when (assortment) {
        OBST_GEMUESE -> "shop=greengrocer"
        BROT_BACKWAREN -> "shop=bakery"
        FLEISCH_WURST -> "shop=butcher"
        FISCH -> "shop=seafood"
        MILCH_EIER, TIEFKUEHL, VORRAT, SUESSES_SNACKS, KAFFEE_TEE -> "shop=supermarket"
        GETRAENKE, ALKOHOL -> "shop=beverages"
        KOERPERPFLEGE, KOSMETIK, HAUSHALT_REINIGUNG, BABY -> "shop=chemist"
        TIER -> "shop=pet"
        GESUNDHEIT, MEDIKAMENTE -> "amenity=pharmacy"
        HAUSHALTSWAREN -> "shop=houseware"
        DEKO_WOHNEN -> "shop=interior_decoration"
        MOEBEL -> "shop=furniture"
        HEIMTEXTIL -> "shop=bed"
        WERKZEUG, ELEKTRO_KLEINTEILE -> "shop=doityourself"
        GARTEN -> "shop=garden_centre"
        ELEKTRONIK, HAUSHALTSGERAETE -> "shop=electronics"
        SCHREIBWAREN, PARTY_GESCHENK -> "shop=stationery"
        BUECHER -> "shop=books"
        ZEITSCHRIFTEN_TABAK -> "shop=kiosk"
        KLEIDUNG -> "shop=clothes"
        SCHUHE -> "shop=shoes"
        SPIELZEUG -> "shop=toys"
        SPORT -> "shop=sports"
        FAHRRAD -> "shop=bicycle"
        AUTO -> "shop=car_parts"
        BLUMEN -> "shop=florist"
    }

    private fun chainIn(text: String?, tag: String): StoreChain? {
        if (text.isNullOrBlank()) return null
        val words = normalize(text).split(' ')
        var best = -1
        for (start in words.indices) {
            val candidates = patternsByFirstWord[words[start]] ?: continue
            for (pattern in candidates) {
                if (best in 0..pattern.chainIndex) continue
                val chain = chains[pattern.chainIndex]
                if (matchesAt(words, start, pattern.words) && (chain.shopTags.isEmpty() || tag in chain.shopTags)) {
                    best = pattern.chainIndex
                }
            }
        }
        return chains.getOrNull(best)
    }

    private fun matchesAt(words: List<String>, start: Int, pattern: List<String>): Boolean =
        start + pattern.size <= words.size && pattern.indices.all { words[start + it] == pattern[it] }

    private fun shops(vararg values: String): Set<String> = values.mapTo(LinkedHashSet()) { "shop=$it" }

    private fun chain(
        id: String,
        name: String,
        shopTags: Set<String>,
        assortments: Set<Assortment>,
        vararg patterns: String,
        broad: Boolean = false,
    ) = StoreChain(id, name, patterns.toList(), shopTags, assortments, broad)
}
