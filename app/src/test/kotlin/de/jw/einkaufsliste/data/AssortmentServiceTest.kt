package de.jw.einkaufsliste.data

import de.jw.einkaufsliste.core.catalog.Assortment
import de.jw.einkaufsliste.core.catalog.AssortmentGuesser
import de.jw.einkaufsliste.data.db.AssortmentCacheEntity
import de.jw.einkaufsliste.data.repo.AssortmentService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssortmentServiceTest {
    /** Gemini took the pear for a light bulb. */
    private val wrongAnswer = AssortmentCacheEntity(
        nameKey = "birne",
        assortments = Assortment.toSlugs(listOf(Assortment.ELEKTRO_KLEINTEILE)),
        fetchedAt = 0L,
    )

    /** Gemini found no product in the name. */
    private val noProduct = AssortmentCacheEntity(nameKey = "xyz", assortments = "", fetchedAt = 0L)

    @Test
    fun cachedAnswersCountWhileTheSettingIsOn() = runBlocking {
        val answers = AssortmentService.answersWhileEnabled(
            MutableStateFlow(listOf(wrongAnswer, noProduct)),
            MutableStateFlow(true),
        ).first()
        assertEquals(mapOf("birne" to setOf(Assortment.ELEKTRO_KLEINTEILE)), answers)
        assertEquals(setOf(Assortment.ELEKTRO_KLEINTEILE), AssortmentService.resolve("Birne", "obst_gemuese", answers))
    }

    @Test
    fun turningTheSettingOffBringsBackTheWordList() = runBlocking {
        val enabled = MutableStateFlow(true)
        val flow = AssortmentService.answersWhileEnabled(MutableStateFlow(listOf(wrongAnswer)), enabled)

        enabled.value = false
        val off = flow.first()
        assertTrue(off.isEmpty())
        val pear = AssortmentService.resolve("Birne", "obst_gemuese", off)
        assertEquals(AssortmentGuesser.guess("Birne", "obst_gemuese"), pear)
        assertTrue(Assortment.OBST_GEMUESE in pear)
        assertFalse(Assortment.ELEKTRO_KLEINTEILE in pear)

        // On again: the cached answers are used again, without asking Gemini.
        enabled.value = true
        assertEquals(setOf(Assortment.ELEKTRO_KLEINTEILE), flow.first()["birne"])
    }

    @Test
    fun nothingToAskWhenEveryNameWasAskedBefore() {
        assertTrue(AssortmentService.namesToAsk(listOf("Milch", " milch ", "Brot"), setOf("milch", "brot"), limit = 5).isEmpty())
        assertTrue(AssortmentService.namesToAsk(emptyList(), emptySet(), limit = 5).isEmpty())
        assertTrue(AssortmentService.namesToAsk(listOf(" ", ""), emptySet(), limit = 5).isEmpty())
    }

    @Test
    fun eachNewNameIsAskedOnceUpToTheLimit() {
        val todo = AssortmentService.namesToAsk(
            listOf("Milch", " Brot ", "MILCH", "Käse", "Eier", "Mehl"),
            setOf("käse"),
            limit = 3,
        )
        assertEquals(listOf("milch" to "Milch", "brot" to "Brot", "eier" to "Eier"), todo)
    }
}
