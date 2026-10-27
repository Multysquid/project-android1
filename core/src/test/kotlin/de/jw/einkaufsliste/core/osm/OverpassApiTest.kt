package de.jw.einkaufsliste.core.osm

import de.jw.einkaufsliste.core.model.GeoPoint
import de.jw.einkaufsliste.core.model.ShopInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class OverpassApiTest {
    @Test
    fun buildsQueryInExactShape() {
        val query = OverpassApi.buildShopsQuery(GeoPoint(52.52, 13.405), 500)
        assertEquals(
            "[out:json][timeout:25];(nwr[\"shop\"](around:500,52.520000,13.405000);" +
                "nwr[\"amenity\"=\"pharmacy\"](around:500,52.520000,13.405000););out center tags;",
            query,
        )
    }

    @Test
    fun queryUsesDotsEvenWithGermanDefaultLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val query = OverpassApi.buildShopsQuery(GeoPoint(48.1371079, 11.5753822), 12000, timeoutSeconds = 90)
            assertEquals(
                "[out:json][timeout:90];(nwr[\"shop\"](around:12000,48.137108,11.575382);" +
                    "nwr[\"amenity\"=\"pharmacy\"](around:12000,48.137108,11.575382););out center tags;",
                query,
            )
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun queryFormatsNegativeCoordinates() {
        val query = OverpassApi.buildShopsQuery(GeoPoint(-33.8688, -0.5), 3000)
        assertTrue(query.contains("(around:3000,-33.868800,-0.500000)"))
    }

    @Test
    fun parsesNodesWaysAndRelations() {
        val shops = OverpassApi.parse(SAMPLE)
        val byId = shops.associateBy { it.id }

        assertEquals(
            ShopInfo(
                id = "node/1234567890",
                name = "REWE",
                brand = "REWE",
                lat = 52.5208,
                lon = 13.4094,
                tag = "shop=supermarket",
                openingHours = "Mo-Sa 07:00-22:00",
            ),
            byId["node/1234567890"],
        )
        assertEquals(
            ShopInfo(
                id = "way/987654",
                name = "Bäckerei Schmidt",
                brand = null,
                lat = 52.5191,
                lon = 13.4012,
                tag = "shop=bakery",
                openingHours = null,
            ),
            byId["way/987654"],
        )
        val relation = byId.getValue("relation/42")
        assertEquals("shop=mall", relation.tag)
        assertEquals(52.51, relation.lat, 1e-9)
        assertEquals(13.39, relation.lon, 1e-9)

        val pharmacy = byId.getValue("node/555")
        assertEquals("amenity=pharmacy", pharmacy.tag)
        assertEquals("Apotheke am Markt", pharmacy.name)
    }

    @Test
    fun skipsVacantShopsNonShopsAndElementsWithoutCoordinates() {
        val ids = OverpassApi.parse(SAMPLE).map { it.id }.toSet()
        assertTrue("node/777" !in ids) // shop=vacant
        assertTrue("node/778" !in ids) // shop=disused
        assertTrue("node/779" !in ids) // shop=no
        assertTrue("node/780" !in ids) // amenity=cafe
        assertTrue("node/781" !in ids) // no tags
        assertTrue("way/782" !in ids) // no center
        assertTrue("node/783" !in ids) // no lat/lon
        assertTrue("area/784" !in ids) // unknown type
        assertEquals(
            setOf("node/1234567890", "way/987654", "relation/42", "node/555", "node/600", "node/601", "node/602"),
            ids,
        )
    }

    @Test
    fun deduplicatesById() {
        val shops = OverpassApi.parse(SAMPLE)
        assertEquals(1, shops.count { it.id == "node/1234567890" })
        assertEquals(shops.size, shops.map { it.id }.toSet().size)
    }

    @Test
    fun nameFallsBackToGermanNameAndBlankBecomesNull() {
        val byId = OverpassApi.parse(SAMPLE).associateBy { it.id }
        assertEquals("Netto Marken-Discount", byId.getValue("node/600").name)
        assertNull(byId.getValue("node/601").name)
        assertNull(byId.getValue("node/601").brand)
        assertNull(byId.getValue("node/601").openingHours)
    }

    @Test
    fun takesFirstValueOfShopList() {
        assertEquals("shop=supermarket", OverpassApi.parse(SAMPLE).single { it.id == "node/602" }.tag)
    }

    @Test
    fun vacantShopThatIsAPharmacyCountsAsPharmacy() {
        val json = """
            {"elements":[{"type":"node","id":1,"lat":50.0,"lon":8.0,"tags":{"shop":"vacant","amenity":"pharmacy"}}]}
        """.trimIndent()
        assertEquals("amenity=pharmacy", OverpassApi.parse(json).single().tag)
    }

    @Test
    fun pharmacyWithShopTagCountsAsPharmacy() {
        val json = """
            {"elements":[{"type":"node","id":1,"lat":50.0,"lon":8.0,"tags":{"shop":"chemist","amenity":"pharmacy"}}]}
        """.trimIndent()
        assertEquals("amenity=pharmacy", OverpassApi.parse(json).single().tag)
    }

    @Test
    fun acceptsIdAndCoordinatesAsStrings() {
        val json = """
            {"elements":[{"type":"node","id":"17","lat":"50.5","lon":"8.25","tags":{"shop":"kiosk"}}]}
        """.trimIndent()
        val shop = OverpassApi.parse(json).single()
        assertEquals("node/17", shop.id)
        assertEquals(50.5, shop.lat, 0.0)
        assertEquals(8.25, shop.lon, 0.0)
    }

    @Test
    fun skipsOutOfRangeCoordinates() {
        val json = """
            {"elements":[{"type":"node","id":1,"lat":95.0,"lon":8.0,"tags":{"shop":"kiosk"}}]}
        """.trimIndent()
        assertTrue(OverpassApi.parse(json).isEmpty())
    }

    @Test
    fun oddInputGivesEmptyListOrNull() {
        for (input in listOf("", "   ", "<html>Too Many Requests</html>", "[]", "{}", "{\"elements\":{}}", "null", "{\"elements\":")) {
            assertEquals(input, emptyList<ShopInfo>(), OverpassApi.parse(input))
            assertNull(input, OverpassApi.parseOrNull(input))
        }
    }

    @Test
    fun oddElementsAreSkipped() {
        val json = """
            {"elements":[1, "x", null, [], {"type":null}, {"type":"node","id":null,"lat":1,"lon":1,"tags":{"shop":"a"}},
              {"type":"node","id":2,"lat":1,"lon":1,"tags":"shop=kiosk"},
              {"type":"node","id":3,"lat":1,"lon":1,"tags":{"shop":"kiosk","name":null,"brand":5}}]}
        """.trimIndent()
        val shop = OverpassApi.parse(json).single()
        assertEquals("node/3", shop.id)
        assertNull(shop.name)
        assertEquals("5", shop.brand)
    }

    @Test
    fun emptyAnswerIsValid() {
        val json = """{"version":0.6,"generator":"Overpass API 0.7.62","elements":[]}"""
        assertEquals(emptyList<ShopInfo>(), OverpassApi.parseOrNull(json))
    }

    @Test
    fun runtimeErrorRemarkIsNotAValidAnswer() {
        val json = """
            {
              "version": 0.6,
              "generator": "Overpass API 0.7.62.1 084b4234",
              "osm3s": {"timestamp_osm_base": "2026-10-19T10:00:00Z", "copyright": "The data included in this document is from www.openstreetmap.org."},
              "elements": [
                {"type":"node","id":1,"lat":50.0,"lon":8.0,"tags":{"shop":"supermarket"}}
              ],
              "remark": "runtime error: Query timed out in \"query\" at line 1 after 26 seconds."
            }
        """.trimIndent()
        assertNull(OverpassApi.parseOrNull(json))
        assertTrue(OverpassApi.parse(json).isEmpty())
    }

    @Test
    fun harmlessRemarkIsAccepted() {
        val json = """{"elements":[{"type":"node","id":1,"lat":50.0,"lon":8.0,"tags":{"shop":"bakery"}}],"remark":"note"}"""
        assertNotNull(OverpassApi.parseOrNull(json))
        assertEquals(1, OverpassApi.parse(json).size)
    }

    private companion object {
        val SAMPLE = """
            {
              "version": 0.6,
              "generator": "Overpass API 0.7.62.1 084b4234",
              "osm3s": {
                "timestamp_osm_base": "2026-10-19T09:58:12Z",
                "copyright": "The data included in this document is from www.openstreetmap.org. The data is made available under ODbL."
              },
              "elements": [
                {
                  "type": "node",
                  "id": 1234567890,
                  "lat": 52.5208,
                  "lon": 13.4094,
                  "tags": {
                    "addr:city": "Berlin",
                    "brand": "REWE",
                    "brand:wikidata": "Q16968817",
                    "name": "REWE",
                    "opening_hours": "Mo-Sa 07:00-22:00",
                    "shop": "supermarket"
                  }
                },
                {
                  "type": "node",
                  "id": 1234567890,
                  "lat": 52.5208,
                  "lon": 13.4094,
                  "tags": {"name": "REWE (duplicate)", "shop": "supermarket"}
                },
                {
                  "type": "way",
                  "id": 987654,
                  "center": {"lat": 52.5191, "lon": 13.4012},
                  "tags": {"building": "yes", "name": "Bäckerei Schmidt", "shop": "bakery", "brand": "  "}
                },
                {
                  "type": "relation",
                  "id": 42,
                  "center": {"lat": 52.51, "lon": 13.39},
                  "tags": {"name": "Alexa", "shop": "mall", "type": "multipolygon"}
                },
                {
                  "type": "node",
                  "id": 555,
                  "lat": 52.5222,
                  "lon": 13.4111,
                  "tags": {"amenity": "pharmacy", "healthcare": "pharmacy", "name": "Apotheke am Markt", "opening_hours": "Mo-Fr 08:00-18:30; Sa 09:00-13:00"}
                },
                {"type": "node", "id": 600, "lat": 52.5, "lon": 13.4, "tags": {"shop": "supermarket", "name:de": "Netto Marken-Discount", "brand": "Netto"}},
                {"type": "node", "id": 601, "lat": 52.5, "lon": 13.4, "tags": {"shop": "convenience", "name": "   ", "brand": "", "opening_hours": " "}},
                {"type": "node", "id": 602, "lat": 52.5, "lon": 13.4, "tags": {"shop": "supermarket;bakery", "name": "Markt"}},
                {"type": "node", "id": 777, "lat": 52.5, "lon": 13.4, "tags": {"shop": "vacant"}},
                {"type": "node", "id": 778, "lat": 52.5, "lon": 13.4, "tags": {"shop": "disused", "name": "Alter Laden"}},
                {"type": "node", "id": 779, "lat": 52.5, "lon": 13.4, "tags": {"shop": "no"}},
                {"type": "node", "id": 780, "lat": 52.5, "lon": 13.4, "tags": {"amenity": "cafe", "name": "Café"}},
                {"type": "node", "id": 781, "lat": 52.5, "lon": 13.4},
                {"type": "way", "id": 782, "tags": {"shop": "bakery"}},
                {"type": "node", "id": 783, "tags": {"shop": "bakery"}},
                {"type": "area", "id": 784, "lat": 52.5, "lon": 13.4, "tags": {"shop": "bakery"}}
              ]
            }
        """.trimIndent()
    }
}
