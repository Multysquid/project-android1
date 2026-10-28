package de.jw.einkaufsliste.data.repo

import android.util.Log
import de.jw.einkaufsliste.core.catalog.Assortment
import de.jw.einkaufsliste.core.catalog.AssortmentGuesser
import de.jw.einkaufsliste.core.text.ItemNames
import de.jw.einkaufsliste.data.db.AppDatabase
import de.jw.einkaufsliste.data.db.AssortmentCacheEntity
import de.jw.einkaufsliste.data.secure.ApiKeyStore
import de.jw.einkaufsliste.data.settings.SettingsRepository
import de.jw.einkaufsliste.feature.gemini.GeminiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Knows what kind of product each item is, so the shop check can look for it in every shop
 * that sells that kind (see StoreCatalog). The built-in keyword lists decide offline
 * ([AssortmentGuesser]); with a Gemini key and "Artikel mit Gemini einordnen" on, Gemini
 * classifies each new item name once and its answer is cached by name and wins. With the
 * setting off the cached answers do not count, so the keyword lists decide again.
 */
class AssortmentService(
    db: AppDatabase,
    private val settings: SettingsRepository,
    private val apiKeyStore: ApiKeyStore,
    private val gemini: GeminiClient,
) {
    private val cacheDao = db.assortmentCacheDao()
    private val itemDao = db.itemDao()

    /** One classification run at a time, so an item added during a check is not asked about twice. */
    private val classifyLock = Mutex()

    /**
     * Gemini's answers by normalized name while "Artikel mit Gemini einordnen" is on, else none;
     * names Gemini found no product in are left out.
     */
    fun observeCache(): Flow<Map<String, Set<Assortment>>> =
        answersWhileEnabled(cacheDao.observeAll(), settings.settings.map { it.classifyWithGemini })

    /** Like [observeCache], once. */
    suspend fun cache(): Map<String, Set<Assortment>> =
        if (settings.current().classifyWithGemini) cacheDao.getAll().toAnswers() else emptyMap()

    /** Asks Gemini about [name] if allowed and not asked before. Never throws except for cancellation. */
    suspend fun classify(name: String) = classifyNames(listOf(name), limit = 1)

    /** Asks Gemini about open items it was not asked about yet, at most [limit]. Never throws except for cancellation. */
    suspend fun classifyOpenItems(limit: Int = MAX_PER_RUN) = classifyNames(itemDao.getOpen().map { it.name }, limit)

    private suspend fun classifyNames(names: List<String>, limit: Int) {
        try {
            classifyLock.withLock { askGemini(names, limit) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Classifying items failed", e)
        }
    }

    private suspend fun askGemini(names: List<String>, limit: Int) {
        if (!settings.current().classifyWithGemini) return
        // Not get(): GeminiClient decrypts the key only for an actual request, so checks and edits
        // with nothing to ask leave the Keystore alone.
        if (!apiKeyStore.hasKey.value) return
        val todo = namesToAsk(names, cacheDao.getAll().mapTo(HashSet()) { it.nameKey }, limit)
        for ((key, name) in todo) {
            // Offline, rate limit or a rejected key: the others would fail the same way; the next run retries.
            val assortments = gemini.classify(name).getOrNull() ?: return
            cacheDao.upsert(
                AssortmentCacheEntity(nameKey = key, assortments = Assortment.toSlugs(assortments), fetchedAt = System.currentTimeMillis()),
            )
        }
    }

    companion object {
        private const val TAG = "Assortments"

        /** Gemini requests per check at most; new items are classified right when they are added. */
        const val MAX_PER_RUN = 5

        /** The answers in [rows] while [enabled] says "Artikel mit Gemini einordnen" is on, else none. */
        internal fun answersWhileEnabled(
            rows: Flow<List<AssortmentCacheEntity>>,
            enabled: Flow<Boolean>,
        ): Flow<Map<String, Set<Assortment>>> =
            combine(rows, enabled.distinctUntilChanged()) { entries, on -> if (on) entries.toAnswers() else emptyMap() }

        /**
         * (Normalized name, name) of the [names] Gemini still has to be asked about: not blank,
         * not in [asked] (normalized names asked about before), each once, at most [limit].
         */
        internal fun namesToAsk(names: List<String>, asked: Set<String>, limit: Int): List<Pair<String, String>> =
            names
                .map { ItemNames.normalize(it) to it.trim() }
                .filter { (key, _) -> key.isNotEmpty() && key !in asked }
                .distinctBy { it.first }
                .take(limit)

        private fun List<AssortmentCacheEntity>.toAnswers(): Map<String, Set<Assortment>> =
            mapNotNull { entry -> Assortment.parseSlugs(entry.assortments).takeIf { it.isNotEmpty() }?.let { entry.nameKey to it } }
                .toMap()

        /** Gemini's answer for [name] when [cache] has one, else the keyword guess (empty when that knows nothing). */
        fun resolve(name: String, categorySlug: String?, cache: Map<String, Set<Assortment>>): Set<Assortment> =
            cache[ItemNames.normalize(name)] ?: AssortmentGuesser.guess(name, categorySlug)
    }
}
