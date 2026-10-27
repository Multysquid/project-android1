package de.jw.einkaufsliste.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryGuesserTest {
    private fun assertGuess(expected: String?, vararg names: String) {
        for (name in names) assertEquals("guess(\"$name\")", expected, CategoryGuesser.guess(name))
    }

    @Test
    fun examplesFromTheSpec() {
        assertGuess("milch_eier", "Vollmilch")
        assertGuess("fleisch_wurst", "Hackfleisch")
        assertGuess("haushalt", "Klopapier", "Toilettenpapier")
        assertGuess("batterien_lampen", "AA Batterien")
        assertGuess("medikamente", "Ibuprofen")
        assertGuess("tier", "Katzenfutter")
        assertGuess("baby", "Windeln")
        assertGuess("haushalt", "Spülmittel")
        assertGuess("drogerie", "Zahnpasta")
        assertGuess("baumarkt", "Schrauben")
        assertGuess("garten", "Blumenerde")
        assertGuess("elektronik", "Ladekabel")
        assertGuess("schreibwaren", "Kugelschreiber")
        assertGuess("kleidung", "Socken")
    }

    @Test
    fun shortKeywordsMatchWholeWordsOnly() {
        assertGuess("tiefkuehl", "Eis", "Eis am Stiel", "Vanille Eis")
        assertGuess("vorrat", "Reis", "Basmatireis")
        assertGuess("fleisch_wurst", "Fleisch")
        assertGuess("alkohol", "Weißwein")
        assertGuess("milch_eier", "Ei", "Eier", "10 Eier", "Bio-Eier")
        assertGuess("kaffee_tee", "Tee", "Grüner Tee", "Kaffee")
        assertGuess("haushalt", "Teelichter")
        assertGuess("alkohol", "Gin", "Rum", "Gin Tonic")
        assertGuess("obst_gemuese", "Ingwer")
    }

    @Test
    fun longestKeywordWins() {
        assertGuess("suesses_snacks", "Milchschokolade")
        assertGuess("vorrat", "Erdbeermarmelade")
        assertGuess("tiefkuehl", "Tiefkühlpizza")
        assertGuess("fleisch_wurst", "Bierschinken")
        assertGuess("kaffee_tee", "Kaffeebohnen")
        assertGuess("obst_gemuese", "Blumenkohl")
        assertGuess("batterien_lampen", "Glühbirne")
        assertGuess("fisch", "Kabeljau")
        assertGuess("obst_gemuese", "Weintrauben", "Preiselbeeren")
        assertGuess("fleisch_wurst", "Schweinefleisch")
        assertGuess("tiefkuehl", "Fischstäbchen", "Speiseeis", "Erdbeereis")
        assertGuess("suesses_snacks", "Studentenfutter")
        assertGuess("tier", "Hundekekse")
        assertGuess("drogerie", "Sonnenmilch")
        assertGuess("vorrat", "Kokosmilch", "Tomatenmark", "Gemüsebrühe")
        assertGuess("getraenke", "Eistee", "Mineralwasser", "Orangensaft")
        assertGuess("milch_eier", "Kaffeesahne")
        assertGuess("alkohol", "Eierlikör")
    }

    @Test
    fun schokomilchIsADrinkNotASweet() {
        val guess = CategoryGuesser.guess("Schokomilch")
        assertTrue("was $guess", guess == "milch_eier" || guess == "getraenke")
    }

    @Test
    fun substringTrapsAreAvoided() {
        assertGuess("obst_gemuese", "Salbei") // not "Salbe"
        assertGuess("schreibwaren", "Kleber") // not "Leber"
        assertGuess("garten", "Schlauch") // not "Lauch"
        assertGuess("elektronik", "Computer") // not "Pute"
        assertGuess("haushalt", "Insektenspray") // not "Sekt"
        assertGuess("schreibwaren", "Briefumschläge") // not "Brie"
        assertGuess("obst_gemuese", "Tomate", "Tomaten") // not "Mate"
        assertGuess("medikamente", "Tabletten")
        assertGuess("elektronik", "Tablet")
        assertGuess("garten", "Holzkohle") // not "Kohl"
        assertGuess("haushalt", "WC-Ente") // not "Ente"
        assertGuess("schreibwaren", "Kalender") // not "Lende"
        assertGuess("fisch", "Tintenfisch") // not "Tinte"
    }

    @Test
    fun hyphenatedAndSeparatedCompoundsAreJoined() {
        assertGuess("kaffee_tee", "Kräuter-Tee", "Kräutertee")
        assertGuess("milch_eier", "H-Milch", "Kaffee Sahne")
        assertGuess("getraenke", "O-Saft", "Club-Mate", "Red Bull")
        assertGuess("kleidung", "T-Shirt")
    }

    @Test
    fun freezerMarkersWin() {
        assertGuess("tiefkuehl", "TK Erbsen", "TK-Erbsen", "Tiefkühl Himbeeren", "Gefrorene Beeren", "Garnelen TK")
        assertGuess("tiefkuehl", "Pizza", "Pizza Salami", "Thunfischpizza")
        assertGuess("milch_eier", "Pizzateig", "Pizzakäse")
        assertGuess("haushalt", "Tiefkühlbeutel")
        assertGuess("elektronik", "Tiefkühltruhe")
    }

    @Test
    fun quantitiesUnitsAndPunctuationAreIgnored() {
        assertGuess("milch_eier", "1,5 l Milch", "Milch 3,5%", "6er Eier", "MILCH", "  milch  ")
        assertGuess("fleisch_wurst", "500g Hackfleisch", "500 g Hackfleisch")
        assertGuess("getraenke", "Wasser 6x1,5l", "Coca-Cola 1,5l")
        assertGuess("kaffee_tee", "2 Beutel Tee")
        assertGuess("haushalt", "Toilettenpapier 3-lagig")
        assertGuess("batterien_lampen", "9V Block")
    }

    @Test
    fun umlautsAndAccentsAreNormalized() {
        assertGuess("milch_eier", "Käse", "Kaese", "Crème fraîche", "Creme fraiche")
        assertGuess("vorrat", "Müsli", "Muesli")
        assertGuess("obst_gemuese", "Äpfel", "Aepfel")
        assertGuess("kaffee_tee", "Nescafé")
        assertGuess("haushalt", "Spülmaschinentabs", "Spuelmaschinentabs")
        assertGuess("medikamente", "Hustenlöser")
        assertGuess("drogerie", "Lippenstift")
        assertGuess("kleidung", "Mütze", "Gürtel")
    }

    @Test
    fun everydayItems() {
        assertGuess("obst_gemuese", "Bananen", "Kartoffeln", "Zwiebeln", "Paprika", "Rucola", "Kohlrabi", "Rote Bete")
        assertGuess("brot_backwaren", "Brötchen", "Toastbrot", "Laugenbrezel", "Käsekuchen", "Tortillas")
        assertGuess("milch_eier", "Butter", "Joghurt", "Quark", "Gouda", "Hafermilch", "Mandelmilch", "Kräuterbutter")
        assertGuess("fleisch_wurst", "Salami", "Würstchen", "Hähnchenbrust", "Putenschnitzel", "Leberkäse", "Gehacktes")
        assertGuess("fisch", "Lachs", "Thunfisch", "Garnelen", "Räucherlachs", "Seelachsfilet")
        assertGuess("tiefkuehl", "Pommes", "Rahmspinat", "Eiswürfel", "Flammkuchen")
        assertGuess("vorrat", "Spaghetti", "Nudeln", "Mehl", "Zucker", "Olivenöl", "Öl", "Ketchup", "Haferflocken")
        assertGuess("suesses_snacks", "Chips", "Gummibärchen", "Kekse", "Weingummi", "Katzenzungen", "Salzstangen")
        assertGuess("getraenke", "Cola", "Apfelschorle", "Malzbier", "Kokoswasser")
        assertGuess("alkohol", "Bier", "Rotwein", "Sekt", "Prosecco", "Kirschwasser", "Alkoholfreies Bier")
        assertGuess("kaffee_tee", "Espresso", "Kakao", "Pfefferminztee", "Instantkaffee")
        assertGuess("drogerie", "Shampoo", "Duschgel", "Deo", "Wattepads", "Taschentücher", "Zahnbürste")
        assertGuess("haushalt", "Waschmittel", "Müllbeutel", "Küchenrolle", "Alufolie", "Spülbürste", "Kleiderbügel")
        assertGuess("baby", "Pampers", "Schnuller", "Babybrei", "Pre Nahrung", "Babypuder")
        assertGuess("tier", "Hundefutter", "Katzenstreu", "Vogelfutter", "Kaninchenfutter", "Meerschweinchenfutter")
        assertGuess("medikamente", "Paracetamol", "Nasenspray", "Pflaster", "Hustensaft", "Hustenbonbons", "FFP2 Maske")
        assertGuess("baumarkt", "Dübel", "Nägel", "Akkuschrauber", "Wandfarbe", "Kabelbinder", "Sekundenkleber")
        assertGuess("garten", "Rasensamen", "Dünger", "Gießkanne", "Grillkohle", "Blumenzwiebeln")
        assertGuess("elektronik", "Kopfhörer", "USB-Kabel", "Powerbank", "Wasserkocher", "Handy", "SIM-Karte")
        assertGuess("batterien_lampen", "Batterien AAA", "LED Lampe", "Taschenlampe", "Knopfzelle", "LED Birne")
        assertGuess("schreibwaren", "Bleistift", "Heft", "Druckerpapier", "Geschenkpapier", "Briefmarken", "Tesafilm")
        assertGuess("kleidung", "Unterhose", "Schuhe", "Handschuhe", "Jeans", "Strumpfhose", "Schlafanzug")
        assertGuess("haushalt", "Gummihandschuhe")
    }

    @Test
    fun defaultCategoryNamesGuessTheirOwnSlug() {
        for (category in CategoryCatalog.defaults) {
            assertEquals(category.name, category.slug, CategoryGuesser.guess(category.name))
        }
    }

    @Test
    fun unknownOrEmptyGivesNull() {
        assertNull(CategoryGuesser.guess(""))
        assertNull(CategoryGuesser.guess("   "))
        assertNull(CategoryGuesser.guess("123"))
        assertNull(CategoryGuesser.guess("1,5 l"))
        assertNull(CategoryGuesser.guess("Xyzzy"))
        assertNull(CategoryGuesser.guess("Motoröl"))
        assertNull(CategoryGuesser.guess("!!!"))
    }

    @Test
    fun wordsNormalization() {
        assertEquals(listOf("h", "milch"), CategoryGuesser.words("1,5 l H-Milch"))
        assertEquals(listOf("hackfleisch"), CategoryGuesser.words("500g Hackfleisch"))
        assertEquals(listOf("weisswein", "trocken"), CategoryGuesser.words("Weißwein, trocken"))
        assertEquals(listOf("creme", "fraiche"), CategoryGuesser.words("Crème fraîche"))
        assertEquals(listOf("eier"), CategoryGuesser.words("10 Stück Eier"))
        assertEquals(listOf("tomaten"), CategoryGuesser.words("2 Dosen Tomaten"))
        assertEquals(listOf("gurken"), CategoryGuesser.words("2 Gurken"))
    }

    @Test
    fun dictionaryIsLargeAndConsistent() {
        val entries = CategoryGuesser.entries
        assertTrue("only ${entries.size} keywords", entries.size >= 250)

        val unknownSlugs = entries.map { it.second }.toSet() - CategoryCatalog.slugs
        assertTrue("unknown slugs: $unknownSlugs", unknownSlugs.isEmpty())

        val conflicts = entries.groupBy({ it.first }, { it.second })
            .filterValues { it.toSet().size > 1 }
        assertTrue("keywords with two categories: $conflicts", conflicts.isEmpty())

        assertTrue(entries.none { it.first.isEmpty() })
        assertTrue(entries.all { (text, _) -> text.all { it in 'a'..'z' } })
    }

    @Test
    fun everyCategoryHasKeywords() {
        val slugsWithKeywords = CategoryGuesser.entries.map { it.second }.toSet()
        assertEquals(CategoryCatalog.slugs, slugsWithKeywords)
    }
}
