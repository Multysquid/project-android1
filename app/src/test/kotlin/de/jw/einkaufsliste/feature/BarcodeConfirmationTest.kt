package de.jw.einkaufsliste.feature

import de.jw.einkaufsliste.core.products.OpenFactsApi
import de.jw.einkaufsliste.feature.scan.BarcodeConfirmation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeConfirmationTest {
    private val a = "4006381333931"
    private val b = "96385074"
    private val invalid = "4006381333932"

    @Test
    fun codesUsedHereAreWhatWeThink() {
        assertTrue(OpenFactsApi.isValidBarcode(a))
        assertTrue(OpenFactsApi.isValidBarcode(b))
        assertFalse(OpenFactsApi.isValidBarcode(invalid))
    }

    @Test
    fun needsTwoFramesInARow() {
        val c = BarcodeConfirmation()
        assertNull(c.onFrame(listOf(a), 0))
        assertEquals(a, c.onFrame(listOf(a), 30))
    }

    @Test
    fun invalidCheckDigitNeverCounts() {
        val c = BarcodeConfirmation()
        repeat(5) { assertNull(c.onFrame(listOf(invalid), it * 30L)) }
    }

    @Test
    fun gapOrOtherCodeBreaksTheStreak() {
        val c = BarcodeConfirmation()
        assertNull(c.onFrame(listOf(a), 0))
        assertNull(c.onFrame(emptyList(), 30))
        assertNull(c.onFrame(listOf(a), 60))
        assertNull(c.onFrame(listOf(b), 90))
        assertNull(c.onFrame(listOf(a), 120))
        assertEquals(a, c.onFrame(listOf(" $a "), 150))
    }

    @Test
    fun resetForgetsHalfSeenCode() {
        val c = BarcodeConfirmation()
        assertNull(c.onFrame(listOf(a), 0))
        c.reset()
        assertNull(c.onFrame(listOf(a), 30))
        assertEquals(a, c.onFrame(listOf(a), 60))
    }

    @Test
    fun acceptedCodeIsIgnoredWhileStillInView() {
        val c = BarcodeConfirmation(ignoreMillis = 1_500)
        c.onFrame(listOf(a), 0)
        assertEquals(a, c.onFrame(listOf(a), 30))
        // Sheet was open for 10 s; the product is still in front of the camera.
        for (t in 10_000L..14_000L step 100) assertNull(c.onFrame(listOf(a), t))
        // Another product next to it still works.
        assertNull(c.onFrame(listOf(a, b), 14_100))
        assertEquals(b, c.onFrame(listOf(b, a), 14_130))
    }

    @Test
    fun acceptedCodeCountsAgainAfterBeingOutOfView() {
        val c = BarcodeConfirmation(ignoreMillis = 1_500)
        c.onFrame(listOf(a), 0)
        assertEquals(a, c.onFrame(listOf(a), 30))
        assertNull(c.onFrame(listOf(a), 5_000))
        assertNull(c.onFrame(emptyList(), 5_100))
        assertNull(c.onFrame(listOf(a), 5_200)) // back too soon: still ignored
        assertNull(c.onFrame(emptyList(), 7_000))
        assertNull(c.onFrame(listOf(a), 7_030))
        assertEquals(a, c.onFrame(listOf(a), 7_060))
    }

    @Test
    fun longPauseWithoutTheCodeClearsTheIgnoreRightAway() {
        val c = BarcodeConfirmation(ignoreMillis = 1_500)
        c.onFrame(listOf(a), 0)
        assertEquals(a, c.onFrame(listOf(a), 30))
        assertNull(c.onFrame(emptyList(), 20_000))
        assertNull(c.onFrame(listOf(a), 20_030))
        assertEquals(a, c.onFrame(listOf(a), 20_060))
    }
}
