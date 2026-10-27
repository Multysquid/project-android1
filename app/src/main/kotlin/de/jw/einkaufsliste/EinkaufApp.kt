package de.jw.einkaufsliste

import android.app.Application
import de.jw.einkaufsliste.ui.Notifications
import kotlinx.coroutines.launch

class EinkaufApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        // Fresh install: put the default categories in early. CategoryRepository also waits for
        // this before every read, so nothing sees an empty category table.
        container.appScope.launch { container.categoryRepository.ensureSeeded() }
    }
}
