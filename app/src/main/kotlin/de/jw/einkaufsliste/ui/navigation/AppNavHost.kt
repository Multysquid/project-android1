package de.jw.einkaufsliste.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import de.jw.einkaufsliste.AppContainer
import de.jw.einkaufsliste.feature.categories.CategoriesScreen
import de.jw.einkaufsliste.feature.list.ListScreen
import de.jw.einkaufsliste.feature.photo.PhotoCaptureScreen
import de.jw.einkaufsliste.feature.scan.BarcodeScanScreen
import de.jw.einkaufsliste.feature.settings.BatterySetupScreen
import de.jw.einkaufsliste.feature.settings.SettingsScreen
import de.jw.einkaufsliste.feature.shops.NearbyScreen

object Routes {
    const val LIST = "list"
    const val BARCODE = "add/barcode"
    const val PHOTO = "add/photo"
    const val NEARBY = "nearby"
    const val SETTINGS = "settings"
    const val CATEGORIES = "settings/categories"
    const val BATTERY = "settings/battery"

    /** Optional argument of the settings screen: the section to scroll to, e.g. [SECTION_GEMINI]. */
    const val ARG_SECTION = "section"

    /** Settings section "Gemini", where the API key is entered. */
    const val SECTION_GEMINI = "gemini"

    /** Route pattern of the settings destination; [SETTINGS] alone opens it at the top. */
    const val SETTINGS_PATTERN = "$SETTINGS?$ARG_SECTION={$ARG_SECTION}"

    /** The settings, scrolled to [section]. */
    fun settings(section: String): String = "$SETTINGS?$ARG_SECTION=$section"
}

@Composable
fun AppNavHost(container: AppContainer, navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            ListScreen(
                container = container,
                onAddByBarcode = navController.openOnce(Routes.BARCODE),
                onAddByPhoto = navController.openOnce(Routes.PHOTO),
                onOpenNearby = navController.openOnce(Routes.NEARBY),
                onOpenSettings = navController.openOnce(Routes.SETTINGS),
            )
        }
        composable(Routes.BARCODE) {
            BarcodeScanScreen(container = container, onBack = navController.backOnce())
        }
        composable(Routes.PHOTO) {
            PhotoCaptureScreen(
                container = container,
                onBack = navController.backOnce(),
                // Both of its settings links are about the Gemini key.
                onOpenSettings = navController.openOnce(Routes.settings(Routes.SECTION_GEMINI)),
            )
        }
        composable(Routes.NEARBY) {
            NearbyScreen(
                container = container,
                onBack = navController.backOnce(),
                onOpenSettings = navController.openOrReturn(Routes.SETTINGS, Routes.SETTINGS_PATTERN),
            )
        }
        composable(
            route = Routes.SETTINGS_PATTERN,
            arguments = listOf(
                navArgument(Routes.ARG_SECTION) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            SettingsScreen(
                container = container,
                onBack = navController.backOnce(),
                onOpenCategories = navController.openOnce(Routes.CATEGORIES),
                onOpenBatterySetup = navController.openOnce(Routes.BATTERY),
                onOpenNearby = navController.openOrReturn(Routes.NEARBY),
                initialSection = entry.arguments?.getString(Routes.ARG_SECTION),
            )
        }
        composable(Routes.CATEGORIES) {
            CategoriesScreen(container = container, onBack = navController.backOnce())
        }
        composable(Routes.BATTERY) {
            BatterySetupScreen(container = container, onBack = navController.backOnce())
        }
    }
}

// All must be called inside the destination's content: dropUnlessResumed reads that
// NavBackStackEntry's lifecycle, which is RESUMED only while the screen is shown and not
// animating. A second tap during the transition (a double tap on "Zurück") is dropped, so it
// cannot pop the screen below or the start destination and leave a blank window.

/** Pops this destination, once. */
@Composable
private fun NavHostController.backOnce(): () -> Unit = dropUnlessResumed { popBackStack() }

/** Opens [route] from this destination, once; never stacks the same screen twice. */
@Composable
private fun NavHostController.openOnce(route: String): () -> Unit =
    dropUnlessResumed { navigate(route) { launchSingleTop = true } }

/**
 * Like [openOnce], but goes back to the destination with [pattern] when it is already on the
 * back stack, so Einstellungen and In der Nähe, which link to each other, do not pile up.
 */
@Composable
private fun NavHostController.openOrReturn(route: String, pattern: String = route): () -> Unit =
    dropUnlessResumed {
        if (!popBackStack(pattern, inclusive = false)) navigate(route) { launchSingleTop = true }
    }
