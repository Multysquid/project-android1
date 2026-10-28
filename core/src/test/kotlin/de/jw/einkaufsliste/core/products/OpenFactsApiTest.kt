package de.jw.einkaufsliste.core.products

import de.jw.einkaufsliste.core.catalog.CategoryCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFactsApiTest {
    private val food = OpenFactsApi.SOURCE_FOOD

    private fun slug(vararg tags: String, source: String = food): String =
        OpenFactsApi.categorySlugFor(tags.toList(), null, source)

    // --- URL and source ---------------------------------------------------------------------------

    @Test
    fun productUrlAsksForNeededFieldsInGerman() {
        assertEquals(
            "https://world.openfoodfacts.org/api/v2/product/4006381333931.json" +
                "?fields=product_name,product_name_de,generic_name_de,generic_name,brands,quantity,categories_tags&lc=de",
            OpenFactsApi.productUrl("https://world.openfoodfacts.org", "4006381333931"),
        )
        assertEquals(
            "https://world.openbeautyfacts.org/api/v2/product/123.json" +
                "?fields=product_name,product_name_de,generic_name_de,generic_name,brands,quantity,categories_tags&lc=de",
            OpenFactsApi.productUrl("https://world.openbeautyfacts.org/", "123"),
        )
    }

    @Test
    fun sourceForHosts() {
        assertEquals(
            listOf("openfoodfacts", "openproductsfacts", "openbeautyfacts", "openpetfoodfacts"),
            OpenFactsApi.HOSTS.map(OpenFactsApi::sourceForHost),
        )
        assertEquals("openfoodfacts", OpenFactsApi.sourceForHost("https://de.openfoodfacts.org/"))
        assertEquals("openfoodfacts", OpenFactsApi.sourceForHost("https://world.openfoodfacts.net:443/api"))
        assertEquals("openfoodfacts", OpenFactsApi.sourceForHost("world.openfoodfacts.org"))
        assertEquals("example", OpenFactsApi.sourceForHost("https://products.example.com"))
        assertEquals("localhost", OpenFactsApi.sourceForHost("http://localhost:8080"))
    }

    // --- Barcodes ---------------------------------------------------------------------------------

    @Test
    fun validBarcodes() {
        listOf(
            "4006381333931", // EAN-13
            "5000112637922", // EAN-13
            "3017620422003", // EAN-13
            "4000417025005", // EAN-13
            "96385074", // EAN-8
            "40170725", // EAN-8
            "036000291452", // UPC-A
            "012345678905", // UPC-A
            "04252614", // UPC-E (not a valid EAN-8)
            "01234565", // UPC-E
        ).forEach { assertTrue(it, OpenFactsApi.isValidBarcode(it)) }
    }

    @Test
    fun invalidBarcodes() {
        listOf(
            "4006381333932",
            "5000112637923",
            "96385075",
            "036000291453",
            "04252615",
            "94252614", // would be valid UPC-E, but UPC-E starts with 0 or 1
            "",
            "abc",
            "400638133393a",
            "4006381333931 ",
            " 4006381333931",
            "400 6381333931",
            "-4006381333931",
            "1234567", // 7 digits
            "40063813339", // 11 digits
            "04006381333931", // 14 digits
            "٤٠٠٦٣٨١٣٣٣٩٣١", // Arabic-Indic digits
        ).forEach { assertFalse(it, OpenFactsApi.isValidBarcode(it)) }
    }

    @Test
    fun allZerosHaveValidCheckDigit() {
        assertTrue(OpenFactsApi.isValidBarcode("0000000000000"))
        assertTrue(OpenFactsApi.isValidBarcode("00000000"))
    }

    @Test
    fun expandsUpcE() {
        assertEquals("042100005264", OpenFactsApi.expandUpcE("04252614"))
        assertEquals("012345000065", OpenFactsApi.expandUpcE("01234565"))
        assertEquals("012000003455", OpenFactsApi.expandUpcE("01234505"))
        assertEquals("012300000455", OpenFactsApi.expandUpcE("01234535"))
        assertEquals("012340000055", OpenFactsApi.expandUpcE("01234545"))
        assertTrue(OpenFactsApi.isValidBarcode(OpenFactsApi.expandUpcE("04252614")))
    }

    // --- Parsing ----------------------------------------------------------------------------------

    @Test
    fun parsesRealisticFoodAnswer() {
        val product = OpenFactsApi.parse(COLA, "5000112637922", food)
        assertNotNull(product)
        product!!
        assertEquals("5000112637922", product.barcode)
        assertEquals("Coca-Cola Original Taste", product.name)
        assertEquals("Coca-Cola", product.brand)
        assertEquals("330 ml", product.quantity)
        assertEquals("getraenke", product.categorySlug)
        assertEquals("openfoodfacts", product.source)
    }

    @Test
    fun parsesGermanNameFirst() {
        val product = OpenFactsApi.parse(MILK, "4008452011004", food)!!
        assertEquals("Frische Vollmilch 3,5 %", product.name)
        assertEquals("Weihenstephan", product.brand)
        assertEquals("1 l", product.quantity)
        assertEquals("milch_eier", product.categorySlug)
    }

    @Test
    fun nameFallbackOrder() {
        fun name(fields: String): String? =
            OpenFactsApi.parse("""{"status":1,"product":{$fields}}""", "1", food)?.name

        assertEquals("A", name(""""product_name_de":"A","product_name":"B","generic_name_de":"C","generic_name":"D""""))
        assertEquals("B", name(""""product_name_de":"  ","product_name":"B","generic_name_de":"C","generic_name":"D""""))
        assertEquals("C", name(""""product_name":"","generic_name_de":"C","generic_name":"D""""))
        assertEquals("D", name(""""product_name_de":null,"generic_name":" D """"))
        assertEquals("Milch laktosefrei", name(""""product_name":"Milch \n laktosefrei""""))
        assertNull(name(""""product_name":"","generic_name":"   ","brands":"Milka""""))
        assertNull(name(""))
    }

    @Test
    fun brandIsFirstCommaSeparatedEntry() {
        fun brand(brands: String): String? =
            OpenFactsApi.parse("""{"status":1,"product":{"product_name":"X","brands":$brands}}""", "1", food)?.brand

        assertEquals("Milka", brand("\"Milka, Mondelez\""))
        assertEquals("Milka", brand("\"  Milka  \""))
        assertEquals("Mondelez", brand("\" , Mondelez\""))
        assertNull(brand("\"\""))
        assertNull(brand("\" , \""))
        assertNull(brand("null"))
    }

    @Test
    fun blankQuantityIsNull() {
        val json = """{"status":1,"product":{"product_name":"Kaffee","quantity":"  ","categories_tags":["en:coffees"]}}"""
        val product = OpenFactsApi.parse(json, "1", food)!!
        assertNull(product.quantity)
        assertEquals("kaffee_tee", product.categorySlug)
    }

    @Test
    fun statusAsNumberOrString() {
        val product = """"product":{"product_name":"Bier","categories_tags":["en:beers"]}"""
        assertNotNull(OpenFactsApi.parse("""{"status":1,$product}""", "1", food))
        assertNotNull(OpenFactsApi.parse("""{"status":"1",$product}""", "1", food))
        assertNotNull(OpenFactsApi.parse("""{"status":1.0,$product}""", "1", food))
        assertNull(OpenFactsApi.parse("""{"status":0,$product}""", "1", food))
        assertNull(OpenFactsApi.parse("""{"status":"0",$product}""", "1", food))
        assertNull(OpenFactsApi.parse("""{$product}""", "1", food))
        assertNull(OpenFactsApi.parse("""{"status":null,$product}""", "1", food))
    }

    @Test
    fun unknownProductIsNull() {
        assertNull(OpenFactsApi.parse(NOT_FOUND, "4000000000000", food))
        assertNull(OpenFactsApi.parse("""{"status":1}""", "1", food))
        assertNull(OpenFactsApi.parse("""{"status":1,"product":null}""", "1", food))
        assertNull(OpenFactsApi.parse("""{"status":1,"product":"x"}""", "1", food))
    }

    @Test
    fun oddInputIsNull() {
        for (input in listOf("", "  ", "null", "[]", "<html>502 Bad Gateway</html>", "{\"status\":1,\"product\":{")) {
            assertNull(input, OpenFactsApi.parse(input, "1", food))
        }
    }

    @Test
    fun parseStoresSourceAndUsesItForFallback() {
        val json = """{"status":1,"product":{"product_name":"Qxv Zrw","categories_tags":[]}}"""
        assertEquals("drogerie", OpenFactsApi.parse(json, "1", "openbeautyfacts")!!.categorySlug)
        assertEquals("openbeautyfacts", OpenFactsApi.parse(json, "1", "openbeautyfacts")!!.source)
        assertEquals("tier", OpenFactsApi.parse(json, "1", "openpetfoodfacts")!!.categorySlug)
    }

    @Test
    fun categoriesTagsMayBeMissingOrOdd() {
        fun slugOf(tags: String) =
            OpenFactsApi.parse("""{"status":1,"product":{"product_name":"Qxv Zrw","categories_tags":$tags}}""", "1", food)!!
                .categorySlug

        assertEquals("vorrat", slugOf("null"))
        assertEquals("vorrat", slugOf("{}"))
        assertEquals("alkohol", slugOf("[null, 5, \"en:beers\"]"))
        assertEquals("alkohol", slugOf("\"en:beverages,en:beers\""))
        assertEquals("vorrat", OpenFactsApi.parse("""{"status":1,"product":{"product_name":"Qxv Zrw"}}""", "1", food)!!.categorySlug)
    }

    @Test
    fun parsedSlugsAreCatalogSlugs() {
        for (json in listOf(COLA, MILK, BEER, FROZEN_PIZZA, CHOCOLATE, TOOTHPASTE)) {
            val product = OpenFactsApi.parse(json, "1", food)!!
            assertNotNull(product.categorySlug, CategoryCatalog.bySlug(product.categorySlug))
        }
    }

    // --- Category mapping -------------------------------------------------------------------------

    @Test
    fun realisticCategoryHierarchies() {
        assertEquals("alkohol", OpenFactsApi.parse(BEER, "1", food)!!.categorySlug)
        assertEquals("tiefkuehl", OpenFactsApi.parse(FROZEN_PIZZA, "1", food)!!.categorySlug)
        assertEquals("suesses_snacks", OpenFactsApi.parse(CHOCOLATE, "1", food)!!.categorySlug)
        assertEquals("drogerie", OpenFactsApi.parse(TOOTHPASTE, "1", "openbeautyfacts")!!.categorySlug)
    }

    @Test
    fun alcohol() {
        assertEquals("alkohol", slug("en:beverages", "en:alcoholic-beverages", "en:beers", "en:lagers"))
        assertEquals("alkohol", slug("en:beverages", "en:alcoholic-beverages", "en:wines", "en:red-wines", "fr:bordeaux"))
        assertEquals("alkohol", slug("en:beverages", "en:alcoholic-beverages", "en:spirits", "en:whiskies"))
        assertEquals("alkohol", slug("en:alcoholic-beverages"))
        assertEquals("alkohol", slug("de:bier"))
        assertEquals("alkohol", slug("en:beverages", "en:beers", "en:non-alcoholic-beers"))
        assertEquals("getraenke", slug("en:beverages", "en:non-alcoholic-beverages"))
        assertEquals("vorrat", slug("en:condiments", "en:vinegars", "en:wine-vinegars"))
        assertEquals("suesses_snacks", slug("en:confectioneries", "en:wine-gums"))
    }

    @Test
    fun frozenWinsAnywhere() {
        assertEquals("tiefkuehl", slug("en:frozen-foods", "en:seafood", "en:fishes", "en:fish-fingers"))
        assertEquals("tiefkuehl", slug("en:plant-based-foods", "en:vegetables", "en:frozen-vegetables", "en:frozen-peas"))
        assertEquals("tiefkuehl", slug("en:desserts", "en:frozen-desserts", "en:ice-creams-and-sorbets", "en:ice-creams"))
        assertEquals("tiefkuehl", slug("en:ice-cream-tubs"))
        assertEquals("getraenke", slug("en:beverages", "en:iced-teas"))
        assertEquals("getraenke", slug("en:beverages", "en:ice-teas"))
        assertEquals("getraenke", slug("en:beverages", "en:coffee-drinks", "en:iced-coffees"))
        assertEquals("suesses_snacks", slug("en:biscuits-and-cakes", "en:iced-cakes"))
    }

    @Test
    fun coffeeAndTea() {
        assertEquals("kaffee_tee", slug("en:beverages", "en:hot-beverages", "en:coffees", "en:ground-coffees"))
        assertEquals("kaffee_tee", slug("en:beverages", "en:teas", "en:herbal-teas"))
        assertEquals("kaffee_tee", slug("en:coffees", "en:coffee-capsules"))
        assertEquals("getraenke", slug("en:beverages", "en:tea-based-beverages", "en:iced-teas"))
        assertEquals("getraenke", slug("en:beverages", "en:coffee-drinks"))
        assertEquals("kaffee_tee", slug("en:cocoa-and-its-products", "en:cocoa-powders"))
    }

    @Test
    fun dairyAndEggs() {
        assertEquals("milch_eier", slug("en:dairies", "en:milks", "en:homogenized-milks", "en:whole-milks"))
        assertEquals(
            "milch_eier",
            slug("en:dairies", "en:fermented-foods", "en:fermented-milk-products", "en:yogurts", "en:fruit-yogurts", "en:strawberry-yogurts"),
        )
        assertEquals("milch_eier", slug("en:dairies", "en:cheeses", "en:hard-cheeses", "fr:emmental"))
        assertEquals("milch_eier", slug("en:farming-products", "en:eggs", "en:chicken-eggs"))
        assertEquals("milch_eier", slug("en:dairies", "en:butters", "en:salted-butters"))
        assertEquals("milch_eier", slug("en:dairies", "en:creams", "en:whipping-creams"))
        assertEquals(
            "milch_eier",
            slug(
                "en:plant-based-foods-and-beverages", "en:beverages", "en:plant-based-foods", "en:plant-based-beverages",
                "en:cereals-and-potatoes", "en:dairy-substitutes", "en:milk-substitutes",
                "en:plant-based-milk-alternatives", "en:cereal-based-drinks", "en:oat-based-drinks",
            ),
        )
        assertEquals("milch_eier", slug("en:beverages", "en:dairy-drinks", "en:chocolate-milks"))
        assertEquals("vorrat", slug("en:spreads", "en:nut-butters", "en:peanut-butters"))
        assertEquals("vorrat", slug("en:coconut-milks"))
    }

    @Test
    fun breadAndPastries() {
        assertEquals("brot_backwaren", slug("en:plant-based-foods", "en:cereals-and-potatoes", "en:breads", "en:sliced-breads"))
        assertEquals("brot_backwaren", slug("en:viennoiseries", "en:croissants"))
        assertEquals("brot_backwaren", slug("en:pastries"))
        assertEquals("vorrat", slug("en:flours", "en:bread-flours"))
    }

    @Test
    fun meatAndFish() {
        assertEquals("fleisch_wurst", slug("en:meats", "en:prepared-meats", "en:sausages", "en:dry-sausages", "en:salamis"))
        assertEquals("fleisch_wurst", slug("en:meats", "en:hams", "en:cooked-hams"))
        assertEquals("fleisch_wurst", slug("en:meats", "en:poultries", "en:chickens"))
        assertEquals("fisch", slug("en:seafood", "en:fishes", "en:salmons", "en:smoked-salmons"))
        assertEquals("fisch", slug("en:seafood", "en:canned-foods", "en:fishes", "en:canned-fishes", "en:tunas", "en:canned-tunas"))
        assertEquals("vorrat", slug("en:sauces", "en:fish-sauces"))
        assertEquals("vorrat", slug("en:meat-analogues"))
    }

    @Test
    fun fruitsAndVegetables() {
        assertEquals(
            "obst_gemuese",
            slug("en:plant-based-foods-and-beverages", "en:plant-based-foods", "en:fruits-and-vegetables-based-foods", "en:fruits-based-foods", "en:fruits", "en:apples"),
        )
        assertEquals("obst_gemuese", slug("en:plant-based-foods", "en:vegetables-based-foods", "en:fresh-vegetables", "en:carrots"))
        assertEquals("obst_gemuese", slug("en:potatoes"))
        assertEquals(
            "getraenke",
            slug("en:beverages", "en:plant-based-beverages", "en:fruit-based-beverages", "en:juices-and-nectars", "en:fruit-juices", "en:orange-juices"),
        )
        assertEquals("vorrat", slug("en:canned-foods", "en:canned-vegetables", "en:canned-corn"))
        assertEquals("vorrat", slug("en:spreads", "en:sweet-spreads", "en:jams", "en:fruit-jams", "en:strawberry-jams"))
        assertEquals("vorrat", slug("en:fruits-based-foods", "en:dried-fruits"))
        assertEquals("vorrat", slug("en:fats", "en:vegetable-fats", "en:vegetable-oils", "en:sunflower-oils"))
    }

    @Test
    fun drinks() {
        assertEquals("getraenke", slug("en:beverages", "en:carbonated-drinks", "en:sodas", "en:colas", "en:sweetened-beverages"))
        assertEquals("getraenke", slug("en:beverages", "en:waters", "en:spring-waters", "en:mineral-waters"))
        assertEquals("getraenke", slug("en:beverages", "en:energy-drinks"))
        assertEquals("getraenke", slug("en:beverages", "en:plant-based-beverages"))
        assertEquals("getraenke", slug("en:beverages"))
        assertEquals("vorrat", slug("en:plant-based-foods-and-beverages"))
    }

    @Test
    fun sweetsAndSnacks() {
        assertEquals(
            "suesses_snacks",
            slug("en:snacks", "en:sweet-snacks", "en:cocoa-and-its-products", "en:confectioneries", "en:chocolates", "en:milk-chocolates"),
        )
        assertEquals("suesses_snacks", slug("en:snacks", "en:sweet-snacks", "en:confectioneries", "en:candies", "en:gummies"))
        assertEquals("suesses_snacks", slug("en:snacks", "en:sweet-snacks", "en:biscuits-and-cakes", "en:biscuits", "en:butter-cookies"))
        assertEquals("suesses_snacks", slug("en:snacks", "en:salty-snacks", "en:appetizers", "en:chips-and-fries", "en:crisps", "en:potato-crisps"))
        assertEquals("suesses_snacks", slug("en:snacks", "en:salty-snacks", "en:nuts", "en:salted-peanuts"))
        assertEquals("suesses_snacks", slug("en:snacks", "en:energy-bars"))
        assertEquals("vorrat", slug("en:breakfasts", "en:spreads", "en:sweet-spreads", "en:hazelnut-spreads", "en:cocoa-and-hazelnuts-spreads"))
    }

    @Test
    fun pantry() {
        assertEquals("vorrat", slug("en:plant-based-foods", "en:cereals-and-potatoes", "en:cereals-and-their-products", "en:pastas", "en:dry-pastas", "en:spaghetti"))
        assertEquals("vorrat", slug("en:cereals-and-their-products", "en:cereal-grains", "en:rices", "en:basmati-rices"))
        assertEquals("vorrat", slug("en:breakfasts", "en:breakfast-cereals", "en:mueslis"))
        assertEquals("vorrat", slug("en:condiments", "en:sauces", "en:tomato-sauces", "en:ketchup"))
        assertEquals("vorrat", slug("en:sweeteners", "en:sugars", "en:white-sugars"))
        assertEquals("vorrat", slug("en:farming-products", "en:honeys"))
        assertEquals("vorrat", slug("en:meals", "en:soups"))
        assertEquals("vorrat", slug("en:condiments", "en:salts"))
    }

    @Test
    fun babyAndPets() {
        assertEquals("baby", slug("en:baby-foods", "en:baby-milks", "en:first-age-milks"))
        assertEquals("baby", slug("en:dairies", "en:milks", "en:infant-formulas"))
        assertEquals("baby", slug("en:baby-foods", "en:fruit-purees-for-babies"))
        assertEquals("obst_gemuese", slug("en:vegetables", "en:baby-carrots"))
        assertEquals("tier", slug("en:pet-food", "en:cat-food", "en:wet-cat-food"))
        assertEquals("tier", slug("en:dog-food"))
        assertEquals("tier", slug("en:meats", "en:dog-food"))
        assertEquals("fleisch_wurst", slug("en:meats", "en:sausages", "en:hot-dogs"))
        assertEquals("fleisch_wurst", slug("en:meats", "en:sausages", "en:hot-dog-sausages"))
        assertEquals("fleisch_wurst", slug("en:meats", "en:sausages", "en:corn-dogs"))
        assertEquals("suesses_snacks", slug("en:snacks", "en:sweet-snacks", "en:cat-tongues"))
    }

    @Test
    fun nonFoodProducts() {
        assertEquals("drogerie", slug("en:toothpastes", source = "openbeautyfacts"))
        assertEquals("drogerie", slug("en:hair-care", "en:shampoos", source = "openbeautyfacts"))
        assertEquals("drogerie", slug("en:face-creams", source = "openbeautyfacts"))
        assertEquals("drogerie", slug("en:body-butters", "en:shea-butters", source = "openbeautyfacts"))
        assertEquals("baby", slug("en:baby-care", "en:diapers", source = "openbeautyfacts"))
        assertEquals("haushalt", slug("en:cleaning-products", "en:dishwashing-liquids", source = "openproductsfacts"))
        assertEquals("haushalt", slug("en:laundry-detergents", source = "openproductsfacts"))
        assertEquals("batterien_lampen", slug("en:batteries", "en:alkaline-batteries", source = "openproductsfacts"))
        assertEquals("drogerie", slug("en:shower-gels", source = "openproductsfacts"))
    }

    @Test
    fun sourceFallbackWhenNothingMatches() {
        assertEquals("drogerie", slug(source = "openbeautyfacts"))
        assertEquals("drogerie", slug("en:unknown-thing", source = "openbeautyfacts"))
        assertEquals("tier", slug(source = "openpetfoodfacts"))
        assertEquals("tier", slug("en:beers", source = "openpetfoodfacts"))
        assertEquals("vorrat", slug(source = "openfoodfacts"))
        assertEquals("vorrat", slug("en:unknown-thing", "de:irgendwas", source = "openfoodfacts"))
        assertEquals("sonstiges", slug(source = "openproductsfacts"))
        assertEquals("sonstiges", slug(source = "user"))
        assertEquals("sonstiges", slug(source = ""))
    }

    @Test
    fun tagsAreNormalized() {
        assertEquals("alkohol", slug("EN:Beers"))
        assertEquals("alkohol", slug("  en:beers  "))
        assertEquals("alkohol", slug("beers"))
        assertEquals("milch_eier", slug("de:käse"))
        assertEquals("vorrat", slug("", " ", "en:", source = food))
    }

    @Test
    fun allMappedSlugsExistInCatalog() {
        val samples = listOf(
            listOf("en:beers"), listOf("en:frozen-foods"), listOf("en:coffees"), listOf("en:milks"), listOf("en:breads"),
            listOf("en:meats"), listOf("en:fishes"), listOf("en:fruits"), listOf("en:waters"), listOf("en:chocolates"),
            listOf("en:pastas"), listOf("en:baby-foods"), listOf("en:pet-food"), listOf("en:shampoos"),
            listOf("en:detergents"), listOf("en:batteries"), emptyList(),
        )
        for (source in OpenFactsApi.HOSTS.map(OpenFactsApi::sourceForHost) + "user") {
            for (tags in samples) {
                val slug = OpenFactsApi.categorySlugFor(tags, null, source)
                assertNotNull("$tags/$source -> $slug", CategoryCatalog.bySlug(slug))
            }
        }
    }

    private companion object {
        val COLA = """
            {
              "code": "5000112637922",
              "product": {
                "brands": "Coca-Cola, The Coca-Cola Company",
                "categories_tags": [
                  "en:beverages",
                  "en:carbonated-drinks",
                  "en:sodas",
                  "en:colas",
                  "en:sweetened-beverages"
                ],
                "generic_name": "",
                "generic_name_de": "Erfrischungsgetränk mit Pflanzenextrakten",
                "product_name": "Coca-Cola Original Taste",
                "product_name_de": "",
                "quantity": "330 ml"
              },
              "status": 1,
              "status_verbose": "product found"
            }
        """.trimIndent()

        val MILK = """
            {
              "code": "4008452011004",
              "product": {
                "brands": "Weihenstephan",
                "categories_tags": ["en:dairies", "en:milks", "en:homogenized-milks", "en:whole-milks", "en:fresh-milks"],
                "generic_name": "Fresh whole milk",
                "product_name": "Fresh whole milk",
                "product_name_de": "Frische Vollmilch 3,5 %",
                "quantity": "1 l"
              },
              "status": 1,
              "status_verbose": "product found"
            }
        """.trimIndent()

        val BEER = """
            {"code":"4029764001807","product":{"brands":"Club-Mate","product_name":"Krombacher Pils",
             "categories_tags":["en:beverages","en:alcoholic-beverages","en:beers","en:german-beers","en:pilsner-beers"],
             "quantity":"0,5 l"},"status":1,"status_verbose":"product found"}
        """.trimIndent()

        val FROZEN_PIZZA = """
            {"code":"4001724819806","product":{"brands":"Dr. Oetker, Ristorante","product_name_de":"Ristorante Pizza Salame",
             "categories_tags":["en:meals","en:pizzas-pies-and-quiches","en:pizzas","en:meat-pizzas","en:frozen-foods","en:frozen-pizzas","en:salami-pizzas"],
             "quantity":"320 g"},"status":1,"status_verbose":"product found"}
        """.trimIndent()

        val CHOCOLATE = """
            {"code":"4000417025005","product":{"brands":"Ritter Sport","product_name":"Ritter Sport Alpenmilch",
             "categories_tags":["en:snacks","en:sweet-snacks","en:cocoa-and-its-products","en:confectioneries","en:chocolates","en:milk-chocolates"],
             "quantity":"100 g"},"status":1,"status_verbose":"product found"}
        """.trimIndent()

        val TOOTHPASTE = """
            {"code":"4015000961007","product":{"brands":"Elmex","product_name":"Kariesschutz Zahnpasta",
             "categories_tags":["en:hygiene","en:toothpastes"],"quantity":"75 ml"},"status":1,"status_verbose":"product found"}
        """.trimIndent()

        val NOT_FOUND = """{"code":"4000000000000","status":0,"status_verbose":"product not found"}"""
    }
}
