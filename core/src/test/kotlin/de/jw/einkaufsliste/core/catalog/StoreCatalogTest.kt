package de.jw.einkaufsliste.core.catalog

import de.jw.einkaufsliste.core.model.ShopInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreCatalogTest {
    private fun shop(tag: String, name: String? = null, brand: String? = null) =
        ShopInfo(id = "node/1", name = name, brand = brand, lat = 52.52, lon = 13.40, tag = tag, openingHours = null)

    private fun chainId(tag: String, name: String? = null, brand: String? = null): String? =
        StoreCatalog.chainFor(shop(tag, name, brand))?.id

    private fun assertNormalized(expected: String, vararg texts: String) {
        for (text in texts) assertEquals("normalize(\"$text\")", expected, StoreCatalog.normalize(text))
    }

    private fun chainNamed(name: String): StoreChain = StoreCatalog.chains.first { it.name == name }

    @Test
    fun normalizeSpellsOutUmlautsAndLowercases() {
        assertNormalized("baeckerei mueller", "Bäckerei Müller", "BÄCKEREI MÜLLER")
        assertNormalized("aldi sued", "ALDI SÜD")
        assertNormalized("oel strasse", "Öl-Straße")
        assertNormalized("mueller", "Müller")
    }

    @Test
    fun normalizeStripsAccentsAndTurnsEverythingElseIntoSingleSpaces() {
        assertNormalized("cafe creme", "Café Crème")
        assertNormalized("dm drogerie markt", "dm-drogerie markt")
        assertNormalized("presse buch", "Presse + Buch")
        assertNormalized("zoo co", "Zoo & Co.")
        assertNormalized("h m", "H&M")
        assertNormalized("a t u", "A.T.U")
        assertNormalized("rewe city", "  REWE   City  ", "REWE\tCity")
        assertNormalized("b1 discount baumarkt", "B1 Discount-Baumarkt")
        assertNormalized("", "", " - & ")
    }

    @Test
    fun normalizeDropsApostrophesWithoutSplitting() {
        assertNormalized("ernstings family", "Ernsting's family", "Ernsting’s family", "Ernsting´s family")
        assertNormalized("denns biomarkt", "denn's BioMarkt")
    }

    @Test
    fun brandWinsOverName() {
        assertEquals("lidl", chainId("shop=supermarket", name = "Rewe", brand = "Lidl"))
        assertEquals("lidl", chainId("shop=supermarket", name = "Lidl"))
        assertEquals("lidl", chainId("shop=supermarket", name = "Lidl Filiale", brand = "Unbekannt GmbH"))
        assertEquals("rewe", chainId("shop=supermarket", name = "REWE Markt Schmidt oHG", brand = ""))
    }

    @Test
    fun brandWithAnotherShopTypeFallsBackToTheName() {
        assertEquals("edeka", chainId("shop=supermarket", name = "Edeka Müller", brand = "Müller"))
        assertNull(chainId("shop=bakery", name = "Bäckerei Müller", brand = "Müller"))
    }

    @Test
    fun patternsMatchWholeWordsOnly() {
        assertEquals("dm", chainId("shop=chemist", name = "dm-drogerie markt"))
        assertEquals("dm", chainId("shop=chemist", brand = "dm"))
        assertNull(chainId("shop=chemist", name = "Admiral"))
        assertNull(chainId("shop=supermarket", name = "Penny's Bistro Markt Kaltenhit"))
        assertEquals("tedi", chainId("shop=variety_store", name = "TEDi"))
        assertNull(chainId("shop=variety_store", name = "Teddy Laden"))
    }

    @Test
    fun muellerIsTheDrugstoreOnlyAsADrugstore() {
        assertEquals("mueller", chainId("shop=chemist", brand = "Müller"))
        assertEquals("mueller", chainId("shop=chemist", name = "Müller Drogerie"))
        assertEquals("mueller", chainId("shop=department_store", name = "Müller"))
        assertNull(chainId("shop=bakery", name = "Bäckerei Müller"))
        assertNull(chainId("shop=butcher", name = "Fleischerei Müller"))
        assertNull(chainId("shop=electronics", name = "Elektro Müller"))
        assertEquals(setOf(Assortment.BROT_BACKWAREN), StoreCatalog.assortmentsOf(shop("shop=bakery", "Bäckerei Müller")))
    }

    @Test
    fun discountersAndSupermarkets() {
        assertEquals("aldi", chainId("shop=supermarket", brand = "ALDI SÜD"))
        assertEquals("aldi", chainId("shop=supermarket", brand = "ALDI Nord"))
        assertEquals("lidl", chainId("shop=supermarket", brand = "Lidl"))
        assertEquals("netto", chainId("shop=supermarket", brand = "Netto Marken-Discount"))
        assertEquals("netto", chainId("shop=supermarket", brand = "Netto"))
        assertEquals("rewe", chainId("shop=supermarket", name = "REWE City"))
        assertEquals("rewe", chainId("shop=convenience", name = "REWE To Go"))
        assertEquals("rewe_getraenkemarkt", chainId("shop=beverages", name = "REWE Getränkemarkt"))
        assertEquals("e_center", chainId("shop=supermarket", name = "E-Center"))
        assertEquals("e_center", chainId("shop=supermarket", brand = "EDEKA Center"))
        assertEquals("edeka", chainId("shop=supermarket", name = "EDEKA Xpress"))
        assertEquals("edeka", chainId("shop=supermarket", name = "Nah & Gut Weber"))
        assertEquals("penny", chainId("shop=supermarket", name = "PENNY Markt"))
        assertEquals("kaufland", chainId("shop=supermarket", brand = "Kaufland"))
    }

    @Test
    fun globusSupermarketAndGlobusBaumarktAreTwoChains() {
        assertEquals("globus", chainId("shop=supermarket", brand = "Globus"))
        assertEquals("globus_baumarkt", chainId("shop=doityourself", name = "Globus Baumarkt"))
        assertEquals("globus_baumarkt", chainId("shop=doityourself", brand = "Globus"))
        assertTrue(StoreCatalog.isBroad(shop("shop=supermarket", brand = "Globus")))
        assertFalse(StoreCatalog.isBroad(shop("shop=doityourself", brand = "Globus")))
    }

    @Test
    fun specialistChains() {
        assertEquals("mediamarkt", chainId("shop=electronics", brand = "MediaMarkt"))
        assertEquals("mediamarkt", chainId("shop=electronics", name = "Media Markt"))
        assertEquals("obi", chainId("shop=doityourself", brand = "OBI"))
        assertEquals("ernstings_family", chainId("shop=clothes", brand = "Ernsting's family"))
        assertEquals("getraenke_hoffmann", chainId("shop=beverages", name = "Getränke Hoffmann"))
        assertEquals("hm", chainId("shop=clothes", brand = "H&M"))
        assertEquals("zara_home", chainId("shop=interior_decoration", brand = "Zara Home"))
        assertEquals("zara_home", chainId("shop=clothes", name = "Zara Home"))
        assertEquals("zara", chainId("shop=clothes", name = "Zara"))
        assertEquals("galeria", chainId("shop=department_store", name = "Karstadt"))
        assertEquals("jysk", chainId("shop=furniture", name = "Dänisches Bettenlager"))
        assertEquals("atu", chainId("shop=car_repair", name = "A.T.U Auto-Teile-Unger"))
    }

    @Test
    fun shortCommonNamesNeedTheRightShopType() {
        assertEquals("hit", chainId("shop=supermarket", name = "HIT"))
        assertNull(chainId("shop=clothes", name = "Hit"))
        assertEquals("action", chainId("shop=variety_store", brand = "Action"))
        assertNull(chainId("shop=sports", name = "Action Sports"))
        assertEquals("expert", chainId("shop=electronics", brand = "expert"))
        assertNull(chainId("shop=bicycle", name = "Fahrrad Expert"))
        assertEquals("combi", chainId("shop=supermarket", name = "Combi"))
        assertNull(chainId("shop=bakery", name = "Combi Backshop"))
        assertEquals("netto", chainId("shop=supermarket", name = "Netto"))
        assertNull(chainId("shop=beverages", name = "Netto Getränke-Discount"))
        assertEquals("norma", chainId("shop=supermarket", name = "NORMA"))
        assertNull(chainId("shop=hairdresser", name = "Salon Norma"))
        assertEquals("edeka", chainId("shop=supermarket", name = "EDEKA"))
        assertNull(chainId("amenity=pharmacy", name = "Apotheke im EDEKA"))
    }

    @Test
    fun unknownShopsHaveNoChain() {
        assertNull(chainId("shop=supermarket", name = "Dorfladen Meier"))
        assertNull(chainId("shop=supermarket"))
        assertNull(chainId("shop=supermarket", name = " ", brand = ""))
    }

    @Test
    fun chainAssortmentsComeBeforeTheShopType() {
        val lidl = shop("shop=supermarket", brand = "Lidl")
        assertTrue(Assortment.OBST_GEMUESE in StoreCatalog.assortmentsOf(lidl))
        assertTrue(Assortment.ELEKTRO_KLEINTEILE in StoreCatalog.assortmentsOf(lidl))
        assertFalse(StoreCatalog.isBroad(lidl))

        val kaufland = shop("shop=supermarket", brand = "Kaufland")
        assertTrue(Assortment.WERKZEUG in StoreCatalog.assortmentsOf(kaufland))
        assertTrue(StoreCatalog.isBroad(kaufland))

        val dm = shop("shop=chemist", brand = "dm")
        assertTrue(Assortment.KOERPERPFLEGE in StoreCatalog.assortmentsOf(dm))
        assertFalse(Assortment.OBST_GEMUESE in StoreCatalog.assortmentsOf(dm))

        // A chain decides even when its shop type alone would be broad.
        val mueller = shop("shop=department_store", brand = "Müller")
        assertEquals(chainNamed("Müller").assortments, StoreCatalog.assortmentsOf(mueller))
        assertFalse(StoreCatalog.isBroad(mueller))
    }

    @Test
    fun independentShopsGoByTheirShopType() {
        assertEquals(setOf(Assortment.FLEISCH_WURST), StoreCatalog.assortmentsOf(shop("shop=butcher", "Metzgerei Huber")))
        val supermarket = shop("shop=supermarket", "Dorfladen Meier")
        assertTrue(Assortment.MILCH_EIER in StoreCatalog.assortmentsOf(supermarket))
        assertFalse(StoreCatalog.isBroad(supermarket))
        assertTrue(StoreCatalog.isBroad(shop("shop=department_store", "Kaufhaus Stolz")))
        assertTrue(StoreCatalog.isBroad(shop("shop=variety_store", "Sonderposten Schulz")))
        assertTrue(StoreCatalog.isBroad(shop(" shop=general ", "Dorfladen")))
    }

    @Test
    fun shopTypesThatSellNothingForTheListHaveNoAssortments() {
        for (tag in listOf("shop=hairdresser", "shop=optician", "shop=car", "shop=travel_agency", "shop=unknown_thing")) {
            assertEquals(tag, emptySet<Assortment>(), StoreCatalog.assortmentsOf(shop(tag, "Irgendwas")))
            assertFalse(tag, StoreCatalog.isBroad(shop(tag, "Irgendwas")))
        }
    }

    @Test
    fun medicinesOnlyAtPharmacies() {
        for (chain in StoreCatalog.chains) {
            assertFalse(chain.id, Assortment.MEDIKAMENTE in chain.assortments)
            assertFalse(chain.id, "amenity=pharmacy" in chain.shopTags)
        }
        val sellers = StoreCatalog.byShopTag.filterValues { Assortment.MEDIKAMENTE in it }.keys
        assertEquals(setOf("amenity=pharmacy"), sellers)
        assertTrue(Assortment.MEDIKAMENTE in StoreCatalog.assortmentsOf(shop("amenity=pharmacy", "Löwen-Apotheke")))
        assertTrue(Assortment.MEDIKAMENTE in StoreCatalog.assortmentsOf(shop("amenity=pharmacy", "Apotheke im Kaufland")))
        assertFalse(Assortment.MEDIKAMENTE in StoreCatalog.assortmentsOf(shop("shop=chemist", brand = "dm")))
    }

    @Test
    fun exampleChainsSellTheAssortment() {
        for (assortments in listOf(setOf(Assortment.ELEKTRO_KLEINTEILE), setOf(Assortment.WERKZEUG))) {
            val examples = StoreCatalog.exampleChains(assortments)
            assertEquals(assortments.toString(), 4, examples.size)
            assertEquals(examples.distinct(), examples)
            for (name in examples) assertTrue(name, chainNamed(name).assortments.any { it in assortments })
        }
        assertTrue("Aldi" in StoreCatalog.exampleChains(setOf(Assortment.ELEKTRO_KLEINTEILE)))
        assertEquals(2, StoreCatalog.exampleChains(setOf(Assortment.OBST_GEMUESE), limit = 2).size)
        assertTrue(StoreCatalog.exampleChains(setOf(Assortment.MEDIKAMENTE)).isEmpty())
    }

    @Test
    fun exampleChainsNameSpecialistsBeforeBroadStores() {
        val tools = StoreCatalog.exampleChains(setOf(Assortment.WERKZEUG))
        assertEquals("OBI", tools.first())
        assertTrue(tools.none { chainNamed(it).broad })
        assertEquals("Deichmann", StoreCatalog.exampleChains(setOf(Assortment.SCHUHE)).first())
    }

    @Test
    fun exampleChainsMixShopTypes() {
        val soap = StoreCatalog.exampleChains(setOf(Assortment.KOERPERPFLEGE))
        assertEquals(listOf("Aldi", "Lidl", "dm", "Rossmann"), soap)
        val batteries = StoreCatalog.exampleChains(setOf(Assortment.ELEKTRO_KLEINTEILE))
        assertTrue(batteries.toString(), "dm" in batteries)
    }

    @Test
    fun exampleChainsForNothingAreBroadStores() {
        val examples = StoreCatalog.exampleChains(emptySet())
        assertEquals(4, examples.size)
        for (name in examples) assertTrue(name, chainNamed(name).broad)
    }

    @Test
    fun shopTypeLabelsStartWithTheUsualShop() {
        assertEquals(listOf("Apotheke"), StoreCatalog.shopTypeLabels(setOf(Assortment.MEDIKAMENTE)))
        assertEquals("Apotheke", StoreCatalog.shopTypeLabels(setOf(Assortment.GESUNDHEIT)).first())
        assertEquals("Baumarkt", StoreCatalog.shopTypeLabels(setOf(Assortment.WERKZEUG)).first())
        assertEquals("Bäckerei", StoreCatalog.shopTypeLabels(setOf(Assortment.BROT_BACKWAREN)).first())
        assertEquals("Drogerie", StoreCatalog.shopTypeLabels(setOf(Assortment.KOERPERPFLEGE)).first())
        assertEquals("Metzgerei", StoreCatalog.shopTypeLabels(setOf(Assortment.FLEISCH_WURST)).first())
        assertEquals(
            listOf("Bäckerei", "Metzgerei"),
            StoreCatalog.shopTypeLabels(setOf(Assortment.BROT_BACKWAREN, Assortment.FLEISCH_WURST), limit = 2),
        )
    }

    @Test
    fun shopTypeLabelsAreDistinctGermanNames() {
        val everything = Assortment.entries.toSet()
        val all = StoreCatalog.shopTypeLabels(everything, limit = 1000)
        assertEquals(all.distinct(), all)
        for (label in all) {
            assertFalse(label, '=' in label || '_' in label)
            assertTrue(label, label.first().isUpperCase())
        }
        for (assortment in Assortment.entries) {
            val labels = StoreCatalog.shopTypeLabels(setOf(assortment))
            assertTrue(assortment.name, labels.size in 1..3)
        }
        assertEquals("Kaufhaus", StoreCatalog.shopTypeLabels(emptySet()).first())
    }

    @Test
    fun catalogIsConsistent() {
        val chains = StoreCatalog.chains
        assertTrue("${chains.size} chains", chains.size in 80..120)
        assertEquals(chains.size, chains.map { it.id }.toSet().size)
        assertEquals(chains.size, chains.map { it.name }.toSet().size)
        for (chain in chains) {
            assertTrue(chain.id, chain.patterns.isNotEmpty())
            assertTrue(chain.id, chain.assortments.isNotEmpty())
            assertTrue(chain.id, chain.shopTags.isNotEmpty())
            for (tag in chain.shopTags) assertTrue("${chain.id}: $tag", tag.startsWith("shop="))
            for (pattern in chain.patterns) {
                assertTrue("${chain.id}: \"$pattern\"", pattern.isNotEmpty())
                assertEquals(chain.id, StoreCatalog.normalize(pattern), pattern)
            }
        }
        for (tag in StoreCatalog.broadShopTags) assertTrue(tag, tag in StoreCatalog.byShopTag)
        for ((tag, assortments) in StoreCatalog.byShopTag) {
            assertTrue(tag, tag.startsWith("shop=") || tag == "amenity=pharmacy")
            assertTrue(tag, assortments.isNotEmpty())
        }
    }

    @Test
    fun everyAssortmentIsSoldSomewhere() {
        for (assortment in Assortment.entries) {
            val byChain = StoreCatalog.chains.any { assortment in it.assortments }
            val byTag = StoreCatalog.byShopTag.values.any { assortment in it }
            assertTrue(assortment.name, byChain || byTag)
        }
    }

    @Test
    fun everyChainCanBeReached() {
        // An earlier chain whose pattern is part of a later chain's pattern, for the same shop
        // type, would hide the later one ("edeka" before "edeka center").
        val chains = StoreCatalog.chains
        for ((i, earlier) in chains.withIndex()) {
            for (later in chains.drop(i + 1)) {
                if (earlier.shopTags.none { it in later.shopTags }) continue
                for (general in earlier.patterns) {
                    for (specific in later.patterns) {
                        assertFalse(
                            "${earlier.id} \"$general\" hides ${later.id} \"$specific\"",
                            " $specific ".contains(" $general "),
                        )
                    }
                }
            }
        }
    }
}
