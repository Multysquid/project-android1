package de.jw.einkaufsliste

import android.content.Context
import de.jw.einkaufsliste.data.db.AppDatabase
import de.jw.einkaufsliste.data.repo.AssortmentService
import de.jw.einkaufsliste.data.net.createHttpClient
import de.jw.einkaufsliste.data.repo.CategoryRepository
import de.jw.einkaufsliste.data.repo.ItemRepository
import de.jw.einkaufsliste.data.repo.MatchService
import de.jw.einkaufsliste.data.secure.ApiKeyStore
import de.jw.einkaufsliste.data.settings.SettingsRepository
import de.jw.einkaufsliste.feature.background.CheckScheduler
import de.jw.einkaufsliste.feature.background.LocationProvider
import de.jw.einkaufsliste.feature.background.PurchaseRecorder
import de.jw.einkaufsliste.feature.background.ShopCheckRunner
import de.jw.einkaufsliste.feature.gemini.GeminiClient
import de.jw.einkaufsliste.feature.products.ProductLookup
import de.jw.einkaufsliste.feature.shops.ShopRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container; one per process, created by [EinkaufApp]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** For work that must outlive a screen (recording a purchase, rescheduling checks). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }
    val apiKeyStore: ApiKeyStore by lazy { ApiKeyStore(appContext) }
    val httpClient by lazy { createHttpClient() }

    val categoryRepository: CategoryRepository by lazy { CategoryRepository(database) }
    val itemRepository: ItemRepository by lazy {
        ItemRepository(
            database,
            assortmentService,
            onItemBought = { item -> appScope.launch { purchaseRecorder.onItemBought(item) } },
            onItemNamed = { name -> appScope.launch { assortmentService.classify(name) } },
        )
    }
    val assortmentService: AssortmentService by lazy {
        AssortmentService(database, settingsRepository, apiKeyStore, geminiClient)
    }
    val shopRepository: ShopRepository by lazy { ShopRepository(database, httpClient, settingsRepository) }
    val matchService: MatchService by lazy { MatchService(database, itemRepository, shopRepository) }

    val locationProvider: LocationProvider by lazy { LocationProvider(appContext) }
    val purchaseRecorder: PurchaseRecorder by lazy { PurchaseRecorder(database, locationProvider, shopRepository) }
    val checkScheduler: CheckScheduler by lazy { CheckScheduler(appContext, settingsRepository, locationProvider) }
    val shopCheckRunner: ShopCheckRunner by lazy { ShopCheckRunner(appContext, this) }

    val productLookup: ProductLookup by lazy { ProductLookup(database, httpClient) }
    val geminiClient: GeminiClient by lazy {
        GeminiClient(httpClient, apiKeyStore, settingsRepository, categoryRepository)
    }
}

/** The process-wide container, from any context (Activity, Service, Receiver, Worker). */
val Context.appContainer: AppContainer
    get() = (applicationContext as EinkaufApp).container
