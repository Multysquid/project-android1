package de.jw.einkaufsliste.data

import de.jw.einkaufsliste.data.settings.AppSettings
import de.jw.einkaufsliste.data.settings.SettingsUpgradeMigration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsUpgradeMigrationTest {
    @Test
    fun oldDefaultRadiusBecomesTheNewDefault() {
        assertEquals(500, SettingsUpgradeMigration.OLD_DEFAULT_RADIUS_METERS)
        assertEquals(1000, AppSettings.DEFAULT_RADIUS_METERS)
        assertEquals(AppSettings.DEFAULT_RADIUS_METERS, SettingsUpgradeMigration.upgradedRadius(500))
    }

    @Test
    fun otherRadiiAndNoRadiusStayAsTheyAre() {
        for (radius in listOf(100, 300, 750, 1000, 2000, 5000)) {
            assertNull("$radius", SettingsUpgradeMigration.upgradedRadius(radius))
        }
        assertNull(SettingsUpgradeMigration.upgradedRadius(null))
    }

    @Test
    fun usersWithAKeyFromBeforeDoNotSendItemNamesUntilTheyTurnItOn() {
        assertEquals(false, SettingsUpgradeMigration.upgradedClassifyWithGemini(stored = null, hasStoredKey = true))
    }

    @Test
    fun usersWithoutAKeyKeepTheDefault() {
        assertNull(SettingsUpgradeMigration.upgradedClassifyWithGemini(stored = null, hasStoredKey = false))
        assertEquals(true, AppSettings().classifyWithGemini)
    }

    @Test
    fun aChoiceTheUserMadeIsKept() {
        for (hasKey in listOf(true, false)) {
            assertNull(SettingsUpgradeMigration.upgradedClassifyWithGemini(stored = true, hasStoredKey = hasKey))
            assertNull(SettingsUpgradeMigration.upgradedClassifyWithGemini(stored = false, hasStoredKey = hasKey))
        }
    }
}
