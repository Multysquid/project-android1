package de.jw.einkaufsliste.core.notify

import de.jw.einkaufsliste.core.model.QuietHours
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPolicyTest {
    private fun minute(hour: Int, minute: Int = 0) = hour * 60 + minute

    @Test
    fun noQuietHours() {
        for (m in listOf(0, 1, 720, 1439)) assertFalse(NotificationPolicy.isQuietTime(m, null))
    }

    @Test
    fun equalStartAndEndMeansNeverQuiet() {
        val quiet = QuietHours(minute(22), minute(22))
        for (m in listOf(0, minute(21, 59), minute(22), minute(23, 59))) {
            assertFalse(NotificationPolicy.isQuietTime(m, quiet))
        }
    }

    @Test
    fun rangeWithinOneDay() {
        val quiet = QuietHours(minute(13), minute(15))
        assertFalse(NotificationPolicy.isQuietTime(minute(12, 59), quiet))
        assertTrue(NotificationPolicy.isQuietTime(minute(13), quiet))
        assertTrue(NotificationPolicy.isQuietTime(minute(14, 30), quiet))
        assertTrue(NotificationPolicy.isQuietTime(minute(14, 59), quiet))
        assertFalse(NotificationPolicy.isQuietTime(minute(15), quiet))
        assertFalse(NotificationPolicy.isQuietTime(0, quiet))
        assertFalse(NotificationPolicy.isQuietTime(1439, quiet))
    }

    @Test
    fun rangeOverMidnight() {
        val quiet = QuietHours(minute(22), minute(7))
        assertFalse(NotificationPolicy.isQuietTime(minute(21, 59), quiet))
        assertTrue(NotificationPolicy.isQuietTime(minute(22), quiet))
        assertTrue(NotificationPolicy.isQuietTime(1439, quiet))
        assertTrue(NotificationPolicy.isQuietTime(0, quiet))
        assertTrue(NotificationPolicy.isQuietTime(minute(3), quiet))
        assertTrue(NotificationPolicy.isQuietTime(minute(6, 59), quiet))
        assertFalse(NotificationPolicy.isQuietTime(minute(7), quiet))
        assertFalse(NotificationPolicy.isQuietTime(minute(12), quiet))
    }

    @Test
    fun rangeStartingAtMidnight() {
        val quiet = QuietHours(0, minute(6))
        assertTrue(NotificationPolicy.isQuietTime(0, quiet))
        assertTrue(NotificationPolicy.isQuietTime(minute(5, 59), quiet))
        assertFalse(NotificationPolicy.isQuietTime(minute(6), quiet))
        assertFalse(NotificationPolicy.isQuietTime(1439, quiet))
    }

    @Test
    fun rangeEndingAtMidnight() {
        val quiet = QuietHours(minute(20), 0)
        assertFalse(NotificationPolicy.isQuietTime(minute(19, 59), quiet))
        assertTrue(NotificationPolicy.isQuietTime(minute(20), quiet))
        assertTrue(NotificationPolicy.isQuietTime(1439, quiet))
        assertFalse(NotificationPolicy.isQuietTime(0, quiet))
    }

    @Test
    fun cooldownWithoutPreviousNotification() {
        assertTrue(NotificationPolicy.cooldownPassed(nowMillis = 1_000L, lastNotifiedMillis = null, cooldownHours = 24))
    }

    @Test
    fun cooldownBoundary() {
        val last = 1_700_000_000_000L
        val hours = 4
        val cooldown = hours * 3_600_000L
        assertFalse(NotificationPolicy.cooldownPassed(last, last, hours))
        assertFalse(NotificationPolicy.cooldownPassed(last + cooldown - 1, last, hours))
        assertTrue(NotificationPolicy.cooldownPassed(last + cooldown, last, hours))
        assertTrue(NotificationPolicy.cooldownPassed(last + cooldown + 1, last, hours))
    }

    @Test
    fun cooldownOfZeroOrLessAlwaysPasses() {
        val now = 1_700_000_000_000L
        assertTrue(NotificationPolicy.cooldownPassed(now, now, 0))
        assertTrue(NotificationPolicy.cooldownPassed(now, now, -3))
    }

    @Test
    fun longCooldownDoesNotOverflow() {
        val last = 1_700_000_000_000L
        val hours = 24 * 365
        assertFalse(NotificationPolicy.cooldownPassed(last + 3_600_000L, last, hours))
        assertTrue(NotificationPolicy.cooldownPassed(last + hours * 3_600_000L, last, hours))
    }

    @Test
    fun lastNotificationInTheFutureBlocks() {
        val now = 1_700_000_000_000L
        assertFalse(NotificationPolicy.cooldownPassed(now, now + 60_000L, 1))
    }
}
