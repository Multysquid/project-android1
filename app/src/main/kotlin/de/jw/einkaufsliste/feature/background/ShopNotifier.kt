package de.jw.einkaufsliste.feature.background

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.jw.einkaufsliste.MainActivity
import de.jw.einkaufsliste.R
import de.jw.einkaufsliste.core.catalog.CategoryCatalog
import de.jw.einkaufsliste.core.geo.GeoMath
import de.jw.einkaufsliste.core.model.ShopInfo
import de.jw.einkaufsliste.core.model.ShopMatch
import de.jw.einkaufsliste.ui.Notifications

/** True when the app may show notifications (permission on Android 13+, not blocked in the system settings). */
internal fun canPostNotifications(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/** "Laden in der Nähe" notifications, one per shop, grouped with a summary. */
class ShopNotifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun canNotify(): Boolean = canPostNotifications(context)

    /**
     * Posts one notification per match (best first) and a group summary when more than one
     * shop notification is showing; the summary also counts [moreShops] found but not notified.
     * Returns the ids of the shops actually notified; empty when notifications are not allowed.
     * Never throws.
     */
    @SuppressLint("MissingPermission") // checked by canNotify()
    fun notifyShops(matches: List<ShopMatch>, moreShops: Int = 0): List<String> {
        if (matches.isEmpty() || !canNotify()) return emptyList()
        val postedIds = mutableListOf<String>()
        val postedTitles = mutableListOf<String>()
        for (match in matches.distinctBy { it.shop.id }) {
            val title = titleFor(match)
            try {
                manager.notify(notificationIdFor(match.shop.id), shopNotification(match, title))
                postedIds += match.shop.id
                postedTitles += title
            } catch (e: Exception) {
                // SecurityException when the permission was revoked meanwhile.
                Log.w(TAG, "Could not post notification", e)
            }
        }
        if (postedIds.isNotEmpty()) postSummaryIfGrouped(postedIds, postedTitles, moreShops.coerceAtLeast(0))
        return postedIds
    }

    private fun shopNotification(match: ShopMatch, title: String): Notification {
        val text = itemsText(match.items.map { it.name })
        return NotificationCompat.Builder(context, Notifications.CHANNEL_SHOPS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openNearbyIntent())
            .setGroup(Notifications.GROUP_SHOPS)
            .build()
    }

    /** Android shows grouped notifications only with a summary; its lines list all shops shown. */
    @SuppressLint("MissingPermission") // checked by canNotify() in notifyShops
    private fun postSummaryIfGrouped(postedIds: List<String>, postedTitles: List<String>, moreShops: Int) {
        val newIds = postedIds.mapTo(HashSet()) { notificationIdFor(it) }
        val olderTitles = activeShopNotifications()
            .filter { it.id !in newIds }
            .mapNotNull { it.notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() }
        val lines = postedTitles + olderTitles
        if (lines.size < 2) return
        val style = NotificationCompat.InboxStyle()
        lines.take(MAX_SUMMARY_LINES).forEach { style.addLine(it) }
        // Shops beyond the shown lines, including those of this check that got no notification of their own.
        val notListed = (lines.size - MAX_SUMMARY_LINES).coerceAtLeast(0) + moreShops
        if (notListed > 0) style.setSummaryText("und $notListed weitere")
        val summary = NotificationCompat.Builder(context, Notifications.CHANNEL_SHOPS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${lines.size + moreShops} Läden in der Nähe")
            .setContentText(lines.joinToString(", "))
            .setStyle(style)
            .setAutoCancel(true)
            .setContentIntent(openNearbyIntent())
            .setGroup(Notifications.GROUP_SHOPS)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .build()
        try {
            manager.notify(Notifications.SUMMARY_NOTIFICATION_ID, summary)
        } catch (e: Exception) {
            Log.w(TAG, "Could not post summary notification", e)
        }
    }

    private fun activeShopNotifications(): List<StatusBarNotification> =
        try {
            context.getSystemService(NotificationManager::class.java)?.activeNotifications.orEmpty()
                .filter {
                    it.notification.group == Notifications.GROUP_SHOPS &&
                        it.id != Notifications.SUMMARY_NOTIFICATION_ID
                }
        } catch (e: Exception) {
            emptyList()
        }

    private fun openNearbyIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(Notifications.EXTRA_OPEN_SCREEN, Notifications.SCREEN_NEARBY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_NEARBY,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        private const val TAG = "ShopNotifier"
        private const val MAX_ITEM_NAMES = 5
        private const val MAX_SUMMARY_LINES = 6

        /** Distinct from the service's request code, so the two PendingIntents do not overwrite each other's extras. */
        private const val REQUEST_OPEN_NEARBY = 100

        /** Stable per shop and never one of the fixed ids in [Notifications] (those are small). */
        fun notificationIdFor(shopId: String): Int = (shopId.hashCode() and 0x3FFF_FFFF) or 0x4000_0000

        /** "EDEKA Müller · 350 m". */
        fun titleFor(match: ShopMatch): String =
            "${shopDisplayName(match.shop)} · ${GeoMath.formatDistance(match.distanceMeters)}"

        /** Name, else brand, else the shop type ("Bäckerei"). */
        fun shopDisplayName(shop: ShopInfo): String =
            shop.name?.takeIf { it.isNotBlank() }
                ?: shop.brand?.takeIf { it.isNotBlank() }
                ?: CategoryCatalog.shopTagLabel(shop.tag)

        /** "Wahrscheinlich: Milch, Brot, Batterien"; more than six names end in "und 3 weitere". */
        fun itemsText(names: List<String>): String {
            // "und 1 weitere" would be odd: show the sixth name instead.
            val shown = if (names.size <= MAX_ITEM_NAMES + 1) names else names.take(MAX_ITEM_NAMES)
            val rest = names.size - shown.size
            return buildString {
                append("Wahrscheinlich: ")
                append(shown.joinToString(", "))
                if (rest > 0) append(" und $rest weitere")
            }
        }
    }
}
