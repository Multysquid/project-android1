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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssortmentGuesserTest {
    private fun assertGuess(expected: Assortment, vararg names: String) = assertGuess(setOf(expected), *names)

    private fun assertGuess(expected: Set<Assortment>, vararg names: String) {
        for (name in names) assertEquals("guess(\"$name\")", expected, AssortmentGuesser.guess(name, null))
    }

    private val food: Set<Assortment> = setOf(
        OBST_GEMUESE, BROT_BACKWAREN, MILCH_EIER, FLEISCH_WURST, FISCH, TIEFKUEHL, VORRAT, SUESSES_SNACKS, GETRAENKE,
        ALKOHOL, KAFFEE_TEE,
    )

    private val foodCategories: Set<String> = food.mapTo(HashSet()) { it.slug }

    @Test
    fun examplesFromTheSpec() {
        assertGuess(MILCH_EIER, "Vollmilch")
        assertGuess(FLEISCH_WURST, "Hackfleisch")
        assertGuess(TIEFKUEHL, "TK Spinat")
        assertGuess(KOERPERPFLEGE, "Zahnpasta")
        assertGuess(FISCH, "Kabeljau")
        assertGuess(ELEKTRO_KLEINTEILE, "Batterien AA")
        assertGuess(MEDIKAMENTE, "Ibuprofen 400")
        assertGuess(GESUNDHEIT, "Pflaster")
        assertGuess(WERKZEUG, "Schrauben 4x40")
        assertGuess(GARTEN, "Blumenerde")
        assertGuess(BLUMEN, "Blumenstrauß")
        assertGuess(ELEKTRONIK, "USB-C Ladekabel")
        assertGuess(AUTO, "Motoröl")
        assertGuess(KOERPERPFLEGE, "Haarfarbe")
        assertGuess(HAUSHALT_REINIGUNG, "Spülmaschinentabs")
        assertGuess(PARTY_GESCHENK, "Geschenkpapier")
    }

    @Test
    fun theProductComesBeforeWhatItIsFor() {
        assertGuess(ELEKTRO_KLEINTEILE, "Batterien für die Fernbedienung", "Batterien fuer Fernbedienung")
        assertGuess(MEDIKAMENTE, "Tabletten gegen Kopfschmerzen")
        assertGuess(PARTY_GESCHENK, "Geschenkpapier zum Geburtstag")
        // Nothing known before "für": the whole name decides.
        assertGuess(TIER, "Leckerli für Katzen")
    }

    @Test
    fun foodGoesToItsOwnAssortment() {
        assertGuess(OBST_GEMUESE, "Bananen", "Kartoffeln", "Rote Bete", "Salbei", "Rosenkohl", "Blumenkohl")
        assertGuess(BROT_BACKWAREN, "Brötchen", "Toastbrot", "Holzofenbrot", "Party Brötchen")
        assertGuess(MILCH_EIER, "Butter", "10 Eier", "Reibekäse", "Grillkäse", "Spiegelei", "Topfen")
        assertGuess(FLEISCH_WURST, "Salami", "Speckstreifen", "Grillwürstchen", "Hähnchenbrust")
        assertGuess(FISCH, "Lachs", "Tintenfisch", "Garnelen")
        assertGuess(TIEFKUEHL, "Pizza", "Eis", "Fischstäbchen", "Tiefkühl Himbeeren", "Garnelen TK")
        assertGuess(VORRAT, "Spaghetti", "Öl", "Olivenöl", "Tellerlinsen", "Grillsoße", "Glasur", "Lebensmittelfarbe")
        assertGuess(VORRAT, "Schokotropfen", "Puderzucker", "Buchweizen", "Sonnenblumenkerne", "Mandelblättchen")
        assertGuess(SUESSES_SNACKS, "Chips", "Gummibärchen", "Studentenfutter", "Adventskalender")
        assertGuess(GETRAENKE, "Mineralwasser", "Cola", "Multivitaminsaft", "Kombucha")
        assertGuess(ALKOHOL, "Bier", "Kellerbier", "Sekt", "Feuerzangenbowle")
        assertGuess(KAFFEE_TEE, "Kaffee", "Tee", "Kamillentee", "Teekanne")
    }

    @Test
    fun foodNamesKeepTheirCategory() {
        val names = listOf(
            "Vollmilch", "Kokosmilch", "Erdbeermarmelade", "Kaffeebohnen", "Schweinefleisch", "Kaffeesahne",
            "Eierlikör", "Weintrauben", "Milchschokolade", "Bierschinken", "Fischstäbchen", "Laugenbrezel",
            "Basmatireis", "Kräuterbutter", "Leberkäse", "Seelachsfilet", "Haferflocken", "Salzstangen",
            "Apfelschorle", "Kirschwasser", "Pfefferminztee", "Gemüsebrühe", "Pizzateig", "Kartoffelsalat",
            "Sushi Reis", "Gewürzgurken", "Grillfleisch", "Partywürstchen", "Kokosöl", "Rapsöl", "Fischfilet",
        )
        for (name in names) {
            val category = CategoryGuesser.guess(name)
            assertEquals(name, AssortmentGuesser.forCategory(category), AssortmentGuesser.guess(name, null))
        }
    }

    @Test
    fun drugstoreIsSplitIntoCareCosmeticsAndHealth() {
        assertGuess(KOERPERPFLEGE, "Duschgel", "Deo", "Deostift", "Rasierer", "Rasierwasser", "Wattepads", "Tampons")
        assertGuess(KOERPERPFLEGE, "Taschentücher", "Abschminktücher", "Kosmetiktücher", "Massageöl", "Vaseline")
        assertGuess(KOSMETIK, "Lippenstift", "Nagellack", "Parfum", "Make-up", "Mascara", "Kajalstift", "Puder")
        assertGuess(KOSMETIK, "Eau de Toilette", "Kosmetikspiegel", "Schminkpinsel")
        assertGuess(GESUNDHEIT, "Pflaster", "Heftpflaster", "Vitamine", "Vitamin D", "Magnesium", "Omega 3", "Zink")
        assertGuess(GESUNDHEIT, "Kondome", "Fieberthermometer", "Verband", "Schwangerschaftstest", "Kontaktlinsen")
        assertGuess(GESUNDHEIT, "Vitamintabletten", "Magnesium Tabletten", "Vitamin C Brausetabletten", "FFP2 Maske")
        assertGuess(GESUNDHEIT, "Händedesinfektion", "Wunddesinfektion", "Ohrstöpsel", "Wärmflasche", "Zeckenzange")
        assertGuess(GESUNDHEIT, "Baldrian", "Eisentabletten", "Schnelltest", "Mückenspray")
    }

    @Test
    fun medicinesArePharmacyOnly() {
        assertGuess(MEDIKAMENTE, "Ibuprofen", "Paracetamol", "Aspirin", "ASS 100", "Nasenspray", "Hustensaft")
        assertGuess(MEDIKAMENTE, "Halstabletten", "Wundsalbe", "Bepanthen", "Fieberzäpfchen", "Fiebersaft")
        assertGuess(MEDIKAMENTE, "Antibiotikum", "Rezept abholen", "Grippostad", "Tabletten", "Schmerztabletten")
        assertGuess(MEDIKAMENTE, "Aspirin Tabletten", "Ibuprofen Tabletten", "Augentropfen", "Globuli", "Pille")
        assertGuess(MEDIKAMENTE, "Blutzucker Teststreifen", "Kohletabletten", "Cortison Salbe", "Sinupret")
    }

    @Test
    fun householdIsSplitFinerThanItsCategory() {
        assertGuess(HAUSHALT_REINIGUNG, "Klopapier", "Spülmittel", "Waschmittel", "Müllbeutel", "Alufolie")
        assertGuess(HAUSHALT_REINIGUNG, "Glasreiniger", "Entkalker", "Gummihandschuhe", "Haushaltshandschuhe")
        assertGuess(HAUSHALT_REINIGUNG, "Spültabs", "Geschirrspültabs", "Streichhölzer", "Bügelwasser", "Zewa")
        assertGuess(HAUSHALTSWAREN, "Topf", "Kochtöpfe", "Pfanne", "Teller", "Tassen", "Gläser", "Weingläser")
        assertGuess(HAUSHALTSWAREN, "Besteck", "Kochlöffel", "Schneebesen", "Nudelsieb", "Käsereibe", "Deckel")
        assertGuess(HAUSHALTSWAREN, "Brotdose", "Kaffeekanne", "Kaffeebecher", "Kartoffelschäler", "Bügelbrett")
        assertGuess(HAUSHALTSWAREN, "Kleiderbügel", "Wäschekorb", "Mülleimer", "Pizzaschneider", "Holzspieße")
        assertGuess(DEKO_WOHNEN, "Bilderrahmen", "Vase", "Spiegel", "Duftkerze", "Stehlampe", "Christbaumkugeln")
        assertGuess(HEIMTEXTIL, "Bettwäsche", "Handtücher", "Tischdecke", "Vorhang", "Gardinen", "Wolldecke")
        assertGuess(HEIMTEXTIL, "Duschvorhang", "Geschirrtuch", "Topflappen", "Ofenhandschuh", "Teppich", "Fußmatte")
        assertGuess(MOEBEL, "Stuhl", "Schreibtisch", "Regal", "Kleiderschrank", "Kommode", "Matratze", "Bett")
        assertGuess(HAUSHALTSGERAETE, "Wasserkocher", "Toaster", "Staubsauger", "Föhn", "Mixer", "Stabmixer")
        assertGuess(HAUSHALTSGERAETE, "Kaffeemaschine", "Kaffeevollautomat", "Bügeleisen", "Kühlschrank")
        assertGuess(HAUSHALTSGERAETE, "Tiefkühltruhe", "Waschmaschine", "Eismaschine", "Saugroboter")
    }

    @Test
    fun electricsAreSplitIntoSmallPartsDevicesAndAppliances() {
        assertGuess(ELEKTRO_KLEINTEILE, "Batterie", "AAA Batterien", "Knopfzelle", "CR2032", "9V Block", "Akkus AA")
        assertGuess(ELEKTRO_KLEINTEILE, "Glühbirne", "LED Lampe", "Leuchtmittel", "Taschenlampe", "Lichterkette")
        assertGuess(ELEKTRO_KLEINTEILE, "Verlängerungskabel", "Steckdosenleiste", "Mehrfachstecker", "Kabeltrommel")
        assertGuess(ELEKTRONIK, "Kabel", "USB-Kabel", "HDMI Kabel", "Ladekabel", "Ladegerät", "Powerbank")
        assertGuess(ELEKTRONIK, "Kopfhörer", "Handyhülle", "Speicherkarte", "Micro SD Karte", "USB-Stick")
        assertGuess(ELEKTRONIK, "Druckerpatrone", "Toner", "Maus", "Tastatur", "Fernseher", "Laptop", "Wecker")
    }

    @Test
    fun diyGardenAndFlowers() {
        assertGuess(WERKZEUG, "Dübel", "Bohrer", "Akkuschrauber", "Silikon", "Wandfarbe", "Pinsel", "Holz")
        assertGuess(WERKZEUG, "Panzertape", "WD-40", "WD40", "Spachtel", "Kabelbinder", "Sekundenkleber")
        assertGuess(WERKZEUG, "Vorhängeschloss", "Teppichmesser", "Arbeitshandschuhe", "Trittleiter", "Leiter")
        assertGuess(GARTEN, "Blumenerde", "Saatgut", "Dünger", "Grillkohle", "Grillanzünder", "Gartenschlauch")
        assertGuess(GARTEN, "Rasensamen", "Rasenmäher", "Tulpenzwiebeln", "Blumentöpfe", "Heckenschere")
        assertGuess(GARTEN, "Grillzange", "Briketts", "Sonnenschirm", "Weihnachtsbaum", "Orchidee")
        assertGuess(BLUMEN, "Blumen", "Rosen", "Tulpen", "Strauß Tulpen", "Sonnenblumen", "Lilien")
    }

    @Test
    fun paperBooksAndTobacco() {
        assertGuess(SCHREIBWAREN, "Kugelschreiber", "Bleistift", "Collegeblock", "Briefumschläge", "Notizbuch")
        assertGuess(SCHREIBWAREN, "Druckerpapier", "Tesafilm", "Schere", "Taschenrechner", "Schulranzen")
        assertGuess(BUECHER, "Buch", "Kochbuch", "Taschenbuch", "Krimi", "Reiseführer", "Landkarte")
        assertGuess(ZEITSCHRIFTEN_TABAK, "Zeitung", "Zeitschrift", "Rätselheft", "Zigaretten", "Tabak")
        assertGuess(ZEITSCHRIFTEN_TABAK, "Blättchen", "Lotto", "Lottoschein", "Feuerzeug")
    }

    @Test
    fun clothesToysSportsAndVehicles() {
        assertGuess(KLEIDUNG, "Socken", "T-Shirt", "Unterhose", "Handschuhe", "Mütze", "Handtasche", "Regenschirm")
        assertGuess(SCHUHE, "Schuhe", "Turnschuhe", "Hausschuhe", "Gummistiefel", "Sneaker", "Schnürsenkel")
        assertGuess(SPIELZEUG, "Lego", "Puzzle", "Brettspiel", "Kartenspiel", "Spielkarten", "Puppe", "Seifenblasen")
        assertGuess(SPORT, "Fußball", "Tennisbälle", "Yogamatte", "Zelt", "Schlafsack", "Tischtennis", "Turnbeutel")
        assertGuess(FAHRRAD, "Fahrradschlauch", "Flickzeug", "Fahrradlicht", "Fahrradschloss", "Fahrradhelm")
        assertGuess(AUTO, "Scheibenwischwasser", "Frostschutz", "Eiskratzer", "Motoröl 5W30", "Autobatterie")
        assertGuess(AUTO, "Winterreifen", "Autoshampoo", "Warnweste", "Starthilfekabel")
    }

    @Test
    fun partyBabyAndPets() {
        assertGuess(PARTY_GESCHENK, "Glückwunschkarte", "Luftballons", "Geburtstagskerzen", "Geschenk", "Gutschein")
        assertGuess(PARTY_GESCHENK, "Konfetti", "Kostüm", "Wunderkerzen", "Silvesterraketen", "Partydeko")
        assertGuess(BABY, "Windeln", "Babybrei", "Schnuller", "Babyöl", "Babyshampoo", "Hipp Gläschen", "Kindersitz")
        assertGuess(TIER, "Katzenfutter", "Hundespielzeug", "Katzenstreu", "Wurmkur", "Fressnapf", "Hundekekse")
    }

    @Test
    fun someProductsAreSoldAsTwoAssortments() {
        assertGuess(setOf(HAUSHALT_REINIGUNG, DEKO_WOHNEN), "Kerzen", "Teelichter")
        assertGuess(setOf(HAUSHALT_REINIGUNG, PARTY_GESCHENK), "Servietten", "Pappteller", "Strohhalme")
        assertGuess(setOf(HAUSHALT_REINIGUNG, HAUSHALTSGERAETE), "Staubsaugerbeutel")
        assertGuess(setOf(HAUSHALT_REINIGUNG, GESUNDHEIT), "Desinfektionsmittel")
        assertGuess(setOf(KOERPERPFLEGE, BABY), "Feuchttücher")
        assertGuess(setOf(SUESSES_SNACKS, GESUNDHEIT), "Hustenbonbons", "Ricola")
        assertGuess(setOf(KAFFEE_TEE, MEDIKAMENTE), "Hustentee")
        assertGuess(setOf(SCHREIBWAREN, WERKZEUG), "Kleber", "Klebeband", "Klebstoff")
        assertGuess(setOf(SPORT, SPIELZEUG), "Ball")
        assertGuess(setOf(AUTO, GESUNDHEIT), "Verbandskasten")
    }

    @Test
    fun substringTrapsAreAvoided() {
        assertGuess(FISCH, "Kabeljau") // not "Kabel"
        assertGuess(KOERPERPFLEGE, "Haarfarbe") // not "Farbe"
        assertGuess(HAUSHALT_REINIGUNG, "Spülmaschinentabs", "Spülmaschinentabletten") // not "Tabletten"
        assertGuess(VORRAT, "Öl", "Rapsöl") // not "Motoröl"
        assertGuess(AUTO, "Motoröl", "Batteriewasser") // not "Öl", "Batterie", "Wasser"
        assertGuess(ELEKTRO_KLEINTEILE, "Batterien") // not "Autobatterie"
        assertGuess(ELEKTRO_KLEINTEILE, "Taschenlampe") // not "Tasche"
        assertGuess(GARTEN, "Rasensamen") // not "Rasierer"
        assertGuess(KOERPERPFLEGE, "Rasierer", "Rasierpinsel") // not "Rasen", "Pinsel"
        assertGuess(SCHREIBWAREN, "Kugelschreiber") // not "Reibe"
        assertGuess(VORRAT, "Mandelblättchen") // not "Blättchen"
        assertGuess(HAUSHALTSWAREN, "Deckel") // not "Decke"
        assertGuess(KOERPERPFLEGE, "Vaseline") // not "Vase"
        assertGuess(OBST_GEMUESE, "Artischocke") // not "Tisch"
        assertGuess(WERKZEUG, "Vorhängeschloss") // not "Vorhänge"
        assertGuess(MEDIKAMENTE, "Schmerztabletten") // not "Tablett"
        assertGuess(HAUSHALTSWAREN, "Tablett") // not "Tablet"
        assertGuess(ELEKTRONIK, "Tablet")
        assertGuess(FAHRRAD, "Fahrradschlauch") // not "Schlauch"
        assertGuess(GARTEN, "Schlauch", "Holzkohle")
        assertGuess(KLEIDUNG, "Handschuhe") // not "Schuhe"
        assertGuess(GARTEN, "Tulpenzwiebeln") // not "Zwiebeln"
        assertGuess(MOEBEL, "Kleiderschrank") // not "Kleid"
        assertGuess(HAUSHALTSGERAETE, "Kühlschrank") // not "Schrank"
        assertGuess(SPORT, "Tischtennis") // not "Tisch"
        assertGuess(PARTY_GESCHENK, "Geburtstagskarte") // not "Karte" of a phone
        assertGuess(ELEKTRONIK, "SIM-Karte", "SD-Karte") // not "Karte" for a birthday
    }

    @Test
    fun freezerMarkersWin() {
        assertGuess(TIEFKUEHL, "TK Spinat", "TK-Erbsen", "Tiefkühl Himbeeren", "Gefrorene Beeren", "Pizza Salami")
        assertGuess(HAUSHALTSGERAETE, "Tiefkühltruhe", "Gefrierschrank")
        assertGuess(HAUSHALTSWAREN, "Pizzaschneider")
        assertGuess(HAUSHALT_REINIGUNG, "Tiefkühlbeutel", "Gefrierbeutel")
    }

    @Test
    fun theCategoryHelpsWhenTheNameDoesNot() {
        assertEquals(setOf(WERKZEUG), AssortmentGuesser.guess("Xyzzy", "baumarkt"))
        assertEquals(setOf(KOERPERPFLEGE), AssortmentGuesser.guess("Xyzzy", "drogerie"))
        assertEquals(setOf(OBST_GEMUESE), AssortmentGuesser.guess("Romanesco", "obst_gemuese"))
        assertEquals(setOf(ELEKTRO_KLEINTEILE), AssortmentGuesser.guess("", "batterien_lampen"))
        assertEquals(emptySet<Assortment>(), AssortmentGuesser.guess("Xyzzy", "sonstiges"))
        assertEquals(emptySet<Assortment>(), AssortmentGuesser.guess("Xyzzy", null))
        assertEquals(emptySet<Assortment>(), AssortmentGuesser.guess("Xyzzy", "meine_liste"))
        assertEquals(emptySet<Assortment>(), AssortmentGuesser.guess("", null))
        assertEquals(emptySet<Assortment>(), AssortmentGuesser.guess("1,5 l", null))
    }

    @Test
    fun theNameWinsOverTheCategory() {
        assertEquals(setOf(MILCH_EIER), AssortmentGuesser.guess("Vollmilch", "baumarkt"))
        assertEquals(setOf(WERKZEUG), AssortmentGuesser.guess("Schrauben", "sonstiges"))
        assertEquals(setOf(TIEFKUEHL), AssortmentGuesser.guess("TK Erbsen", "obst_gemuese"))
        assertEquals(setOf(KOSMETIK), AssortmentGuesser.guess("Nagellack", "drogerie"))
    }

    @Test
    fun forCategoryCoversEveryDefaultCategory() {
        for (slug in CategoryCatalog.slugs - CategoryCatalog.FALLBACK_SLUG) {
            assertTrue(slug, AssortmentGuesser.forCategory(slug).isNotEmpty())
        }
        assertEquals(emptySet<Assortment>(), AssortmentGuesser.forCategory(CategoryCatalog.FALLBACK_SLUG))
        assertEquals(emptySet<Assortment>(), AssortmentGuesser.forCategory(null))
        assertEquals(emptySet<Assortment>(), AssortmentGuesser.forCategory("meine_liste"))

        assertEquals(setOf(KOERPERPFLEGE), AssortmentGuesser.forCategory("drogerie"))
        assertEquals(setOf(HAUSHALT_REINIGUNG), AssortmentGuesser.forCategory("haushalt"))
        assertEquals(setOf(MEDIKAMENTE), AssortmentGuesser.forCategory("medikamente"))
        assertEquals(setOf(WERKZEUG), AssortmentGuesser.forCategory("baumarkt"))
        assertEquals(setOf(ELEKTRO_KLEINTEILE), AssortmentGuesser.forCategory("batterien_lampen"))
        assertEquals(setOf(KLEIDUNG), AssortmentGuesser.forCategory("kleidung"))
        for (assortment in food) assertEquals(setOf(assortment), AssortmentGuesser.forCategory(assortment.slug))
    }

    @Test
    fun everyAssortmentHasKeywords() {
        val withKeywords = AssortmentGuesser.entries.flatMapTo(HashSet()) { it.second }
        assertEquals(Assortment.entries.toSet(), withKeywords)

        val withFineKeywords = AssortmentGuesser.fineKeywords.flatMapTo(HashSet()) { it.value }
        val missing = Assortment.entries.toSet() - food - withFineKeywords
        assertTrue("no finer keywords for $missing", missing.isEmpty())
    }

    @Test
    fun fineDictionaryIsLargeAndConsistent() {
        val fine = AssortmentGuesser.fineKeywords
        assertTrue("only ${fine.size} keywords", fine.size >= 500)

        val conflicts = fine.groupBy({ it.text }, { it.value }).filterValues { it.toSet().size > 1 }
        assertTrue("keywords with two meanings: $conflicts", conflicts.isEmpty())

        assertTrue(fine.all { (it.text.isNotEmpty() && it.text.all { c -> c in 'a'..'z' }) })
        assertTrue(fine.all { it.value.size in 1..2 })
    }

    @Test
    fun everyFineKeywordGuessesItsOwnAssortments() {
        for (keyword in AssortmentGuesser.fineKeywords) {
            assertEquals(keyword.text, keyword.value, AssortmentGuesser.guess(keyword.text, null))
        }
    }

    @Test
    fun foodKeywordsOfCategoryGuesserKeepTheirAssortment() {
        for ((text, slug) in CategoryGuesser.entries) {
            if (slug !in foodCategories) continue
            assertEquals(text, AssortmentGuesser.forCategory(slug), AssortmentGuesser.guess(text, null))
        }
    }

    /**
     * Where the finer dictionary splits a category, a longer keyword of that category that
     * contains a split-off keyword would silently keep the category's assortment ("Duschvorhang"
     * contains "Vorhang" but would stay [HAUSHALT_REINIGUNG]). Each such keyword must be listed
     * in the finer dictionary, so its assortment is a decision ("Handschuh" is [KLEIDUNG] although
     * it contains "Schuh"). A finer keyword unknown to [CategoryGuesser] counts as split off from
     * every category. Food keywords that contain a finer one ("Kabeljau", "Artischocke") are other
     * products and win by being longer.
     */
    @Test
    fun categoryKeywordsDoNotHideFinerKeywords() {
        val fine = AssortmentGuesser.fineKeywords
        val fineTexts = fine.mapTo(HashSet()) { it.text }
        val hidden = mutableListOf<String>()
        for (keyword in fine.filter { !it.wholeWord }) {
            val category = CategoryGuesser.guess(keyword.text)
            for (longer in CategoryGuesser.index.keywords) {
                if (longer.value in foodCategories || longer.text in fineTexts) continue
                if (longer.text.length <= keyword.text.length || keyword.text !in longer.text) continue
                if (category != null && category != longer.value) continue
                val assortments = AssortmentGuesser.forCategory(longer.value)
                if (assortments.isNotEmpty() && assortments != keyword.value) {
                    hidden += "${longer.text} (${longer.value}) hides ${keyword.text} ${keyword.value}"
                }
            }
        }
        assertTrue(hidden.joinToString("\n"), hidden.isEmpty())
    }
}
