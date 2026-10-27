package de.jw.einkaufsliste.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** Notification channels and the extras used to open a screen from a notification. */
object Notifications {
    /** "Laden in der Nähe" alerts. */
    const val CHANNEL_SHOPS = "shops_nearby"

    /** The permanent notification of the foreground service (intervals below 15 minutes). */
    const val CHANNEL_SERVICE = "check_service"

    /** Intent extra on MainActivity naming the screen to open, see [SCREEN_NEARBY]. */
    const val EXTRA_OPEN_SCREEN = "open_screen"
    const val SCREEN_NEARBY = "nearby"

    const val SERVICE_NOTIFICATION_ID = 1
    const val SUMMARY_NOTIFICATION_ID = 2

    /** Group key for the per-shop notifications. */
    const val GROUP_SHOPS = "de.jw.einkaufsliste.SHOPS"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_SHOPS, "Läden in der Nähe", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Hinweis, wenn ein Laden in der Nähe wahrscheinlich etwas von deiner Liste hat"
                },
                NotificationChannel(CHANNEL_SERVICE, "Hintergrundprüfung", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Dauerhafte Anzeige, solange alle paar Minuten geprüft wird"
                    setShowBadge(false)
                },
            ),
        )
    }
}
