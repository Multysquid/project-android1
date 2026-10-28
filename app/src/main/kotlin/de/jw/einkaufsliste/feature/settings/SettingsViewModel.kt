package de.jw.einkaufsliste.feature.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.jw.einkaufsliste.AppContainer
import de.jw.einkaufsliste.core.backup.BackupFormatException
import de.jw.einkaufsliste.core.gemini.GeminiModel
import de.jw.einkaufsliste.data.repo.BackupService
import de.jw.einkaufsliste.data.settings.AppSettings
import de.jw.einkaufsliste.feature.background.CheckTrigger
import de.jw.einkaufsliste.feature.gemini.GeminiClient
import de.jw.einkaufsliste.feature.list.MISSING_SHOP_LABEL
import de.jw.einkaufsliste.feature.list.displayNameWithType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the button of a [SettingsMessage] snackbar does. */
internal enum class SettingsMessageAction(val label: String) {
    /** Opens "In der Nähe", which lists the shops a manual check found. */
    SHOW_NEARBY("Anzeigen"),

    /** Asks for location while in use, then runs "Jetzt prüfen" again. */
    GRANT_LOCATION("Erlauben"),
}

/** A one-off German snackbar message, optionally with a button. */
internal data class SettingsMessage(val text: String, val action: SettingsMessageAction? = null)

/**
 * Google's model list for the stored key: [fromGoogle] is null until it was loaded (or when
 * there is no key), [failed] says the last attempt did not work.
 */
internal data class GeminiModelList(
    val fromGoogle: List<GeminiModel>? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val settingsRepository = container.settingsRepository
    private val apiKeyStore = container.apiKeyStore

    /** Serializes settings writes in call order, so fast taps cannot overtake each other. */
    private val writeLock = Mutex()

    /** Serializes rescheduling, so the last run always sees the latest settings. */
    private val scheduleLock = Mutex()

    /** Null until the stored settings are loaded. */
    val settings: StateFlow<AppSettings?> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val hasApiKey: StateFlow<Boolean> = apiKeyStore.hasKey

    private val _checkRunning = MutableStateFlow(false)

    /** A manual "Jetzt prüfen" is in progress. */
    val checkRunning: StateFlow<Boolean> = _checkRunning.asStateFlow()

    private val _keyTestRunning = MutableStateFlow(false)
    val keyTestRunning: StateFlow<Boolean> = _keyTestRunning.asStateFlow()

    private val _geminiModels = MutableStateFlow(GeminiModelList())

    /** The models the stored key can use, for the model picker. */
    internal val geminiModels: StateFlow<GeminiModelList> = _geminiModels.asStateFlow()

    private var modelListJob: Job? = null

    private val _backupRunning = MutableStateFlow(false)

    /** An export or import of the list is in progress. */
    val backupRunning: StateFlow<Boolean> = _backupRunning.asStateFlow()

    private val backupService by lazy { BackupService(container.database) }

    private val _messages = Channel<SettingsMessage>(Channel.BUFFERED)

    /** One-off German messages for the snackbar. */
    internal val messages: Flow<SettingsMessage> = _messages.receiveAsFlow()

    private val _purchaseCount = MutableStateFlow<Int?>(null)

    /** Number of learned purchases ("bought X at shop Y"); null until counted. */
    val purchaseCount: StateFlow<Int?> = _purchaseCount.asStateFlow()

    private val _purchaseHistory = MutableStateFlow<List<LearnedShop>?>(null)

    /** The learned purchases by shop, for the "Gelernte Einkäufe" dialog; null while loading. */
    internal val purchaseHistory: StateFlow<List<LearnedShop>?> = _purchaseHistory.asStateFlow()

    fun setCheckEnabled(enabled: Boolean) {
        save(reschedule = true) { it.copy(checkEnabled = enabled) }
    }

    fun setRadius(meters: Int) {
        save(reschedule = true) { it.copy(radiusMeters = meters) }
    }

    fun setInterval(minutes: Int) {
        save(reschedule = true) { it.copy(intervalMinutes = minutes) }
    }

    fun setQuietHoursEnabled(enabled: Boolean) {
        save { it.copy(quietHoursEnabled = enabled) }
    }

    fun setQuietStart(minuteOfDay: Int) {
        save { it.copy(quietStartMinute = minuteOfDay) }
    }

    fun setQuietEnd(minuteOfDay: Int) {
        save { it.copy(quietEndMinute = minuteOfDay) }
    }

    fun setRespectOpeningHours(enabled: Boolean) {
        save { it.copy(respectOpeningHours = enabled) }
    }

    fun setCooldownHours(hours: Int) {
        save { it.copy(cooldownHours = hours) }
    }

    fun setGeminiModel(model: String) {
        save { it.copy(geminiModel = model) }
    }

    /**
     * Loads the models the stored key can use, once per screen unless [force]; without a key
     * the picker keeps showing the known models.
     */
    fun loadGeminiModels(force: Boolean = false) {
        val current = _geminiModels.value
        if (current.loading || (!force && current.fromGoogle != null)) return
        if (!apiKeyStore.hasKey.value) {
            _geminiModels.value = GeminiModelList()
            return
        }
        _geminiModels.value = current.copy(loading = true, failed = false)
        modelListJob = viewModelScope.launch {
            val result = container.geminiClient.listModels()
            _geminiModels.value = result.fold(
                onSuccess = { GeminiModelList(fromGoogle = it) },
                onFailure = { _geminiModels.value.copy(loading = false, failed = true) },
            )
        }
    }

    /** Turning it on asks Gemini about the open items right away, so the next check already uses the answers. */
    fun setClassifyWithGemini(enabled: Boolean) {
        val saving = save { it.copy(classifyWithGemini = enabled) }
        if (enabled) {
            container.appScope.launch {
                saving.join()
                container.assortmentService.classifyOpenItems()
            }
        }
    }

    /** Brings scheduling in line with changed permissions. */
    fun reschedule() {
        container.appScope.launch { applySchedule() }
    }

    fun showMessage(text: String) {
        _messages.trySend(SettingsMessage(text))
    }

    /**
     * "Jetzt prüfen": runs in the app scope, so leaving the screen does not cancel the check.
     * Found shops get an "Anzeigen" button; a missing location permission gets "Erlauben".
     */
    fun checkNow() {
        if (_checkRunning.value) return
        _checkRunning.value = true
        viewModelScope.launch {
            try {
                // Same order as the check: an empty list is reported before the missing permission.
                if (!container.locationProvider.hasForegroundPermission() && hasOpenItems()) {
                    _messages.send(
                        SettingsMessage("Für die Prüfung braucht die App deinen Standort.", SettingsMessageAction.GRANT_LOCATION),
                    )
                    return@launch
                }
                val outcome = container.appScope.async { container.shopCheckRunner.run(CheckTrigger.MANUAL) }.await()
                val action = if (outcome.matchingShops > 0) SettingsMessageAction.SHOW_NEARBY else null
                _messages.send(SettingsMessage(outcome.message, action))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(SettingsMessage("Die Prüfung hat nicht geklappt. Bitte nochmal versuchen."))
            } finally {
                _checkRunning.value = false
            }
        }
    }

    private suspend fun hasOpenItems(): Boolean =
        try {
            container.database.itemDao().getOpen().isNotEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Let the check itself report what is wrong.
            false
        }

    /** Counts the learned purchases; called whenever the screen is shown. */
    fun refreshPurchaseCount() {
        viewModelScope.launch {
            _purchaseCount.value = try {
                container.database.purchaseDao().count()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    /** Loads the learned purchases by shop into [purchaseHistory]. */
    fun loadPurchaseHistory() {
        _purchaseHistory.value = null
        viewModelScope.launch {
            val history = try {
                val purchases = container.database.purchaseDao().getSince(0L)
                val shopIds = purchases.map { it.shopId }.distinct()
                val shopDao = container.database.shopDao()
                // SQLite limits the number of query parameters.
                val titles = shopIds.chunked(SHOP_QUERY_CHUNK)
                    .flatMap { shopDao.getByIds(it) }
                    .associate { it.id to it.displayNameWithType() }
                _purchaseCount.value = purchases.size
                LearnedShop.group(purchases, titles, MISSING_SHOP_LABEL)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(SettingsMessage("Die gelernten Einkäufe konnten nicht geladen werden."))
                emptyList()
            }
            _purchaseHistory.value = history
        }
    }

    /** "Gelernte Einkäufe löschen": forgets where items were bought. The list itself stays. */
    fun deletePurchaseHistory() {
        viewModelScope.launch {
            val message = try {
                val deleted = container.appScope.async { container.database.purchaseDao().deleteAll() }.await()
                _purchaseCount.value = 0
                _purchaseHistory.value = emptyList()
                if (deleted == 1) "1 gelernter Einkauf gelöscht." else "$deleted gelernte Einkäufe gelöscht."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Die gelernten Einkäufe konnten nicht gelöscht werden."
            }
            _messages.send(SettingsMessage(message))
        }
    }

    fun saveApiKey(apiKey: String) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.Default) { runCatching { apiKeyStore.set(apiKey) }.isSuccess }
            showMessage(if (saved) "Schlüssel gespeichert." else "Der Schlüssel konnte nicht gespeichert werden.")
            if (saved) {
                resetGeminiModels()
                loadGeminiModels()
                container.appScope.launch { container.assortmentService.classifyOpenItems() }
            }
        }
    }

    /**
     * Tests [enteredKey], or the stored key when null, with the chosen model. Waits for settings
     * writes already started (a model picked just before), because the client reads the model
     * from the settings.
     */
    fun testApiKey(enteredKey: String?) {
        if (_keyTestRunning.value) return
        _keyTestRunning.value = true
        viewModelScope.launch {
            try {
                writeLock.withLock { }
                val result = container.geminiClient.testKey(enteredKey)
                val message = result.fold(
                    onSuccess = {
                        if (enteredKey != null) "Schlüssel und Modell funktionieren. Zum Behalten auf „Speichern“ tippen."
                        else "Schlüssel und Modell funktionieren."
                    },
                    onFailure = { it.message ?: GeminiClient.MESSAGE_UNEXPECTED },
                )
                showMessage(message)
            } finally {
                _keyTestRunning.value = false
            }
        }
    }

    /** "Exportieren": writes the list to [uri], the document the user just created. */
    fun exportList(resolver: ContentResolver, uri: Uri) {
        runBackup(failureMessage = "Die Liste konnte nicht gespeichert werden.") {
            val json = backupService.export()
            withContext(Dispatchers.IO) { resolver.writeBackup(uri, json) }
            "Liste exportiert."
        }
    }

    /** "Importieren": merges the backup in [uri] into the list. */
    fun importList(resolver: ContentResolver, uri: Uri) {
        runBackup(failureMessage = "Die Datei konnte nicht importiert werden.") {
            val json = withContext(Dispatchers.IO) { resolver.readBackup(uri) }
            backupService.import(json).message
        }
    }

    /**
     * Runs [work] in the app scope, so leaving the screen does not cancel a half-written file,
     * and shows its message, or a German error.
     */
    private fun runBackup(failureMessage: String, work: suspend () -> String) {
        if (_backupRunning.value) return
        _backupRunning.value = true
        viewModelScope.launch {
            try {
                val message = try {
                    container.appScope.async { work() }.await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: BackupFormatException) {
                    e.message ?: failureMessage
                } catch (e: BackupTooLargeException) {
                    "Die Datei ist zu groß für eine Sicherung (höchstens 5 MB)."
                } catch (e: Exception) {
                    failureMessage
                }
                showMessage(message)
            } finally {
                _backupRunning.value = false
            }
        }
    }

    fun deleteApiKey() {
        apiKeyStore.clear()
        resetGeminiModels()
        showMessage("Schlüssel gelöscht.")
    }

    /** Forgets the model list of the previous key, also one still loading. */
    private fun resetGeminiModels() {
        modelListJob?.cancel()
        modelListJob = null
        _geminiModels.value = GeminiModelList()
    }

    /**
     * Writes in the app scope, so a change made right before leaving the screen is not lost.
     * UNDISPATCHED takes the lock in call order.
     */
    private fun save(reschedule: Boolean = false, transform: (AppSettings) -> AppSettings): Job =
        container.appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                writeLock.withLock { settingsRepository.update(transform) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("Die Einstellung konnte nicht gespeichert werden.")
                return@launch
            }
            if (reschedule) applySchedule()
        }

    /**
     * Called for every change that affects scheduling (switch, radius, interval, permissions).
     * The settings screen is in the foreground, so the short-interval service may be started.
     */
    private suspend fun applySchedule() {
        scheduleLock.withLock {
            try {
                container.checkScheduler.apply(appInForeground = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // apply() is documented never to throw; a crash here must not take the app down.
            }
        }
    }

    private companion object {
        /** Shop ids per query, well below SQLite's limit of 999 parameters. */
        const val SHOP_QUERY_CHUNK = 500
    }
}
