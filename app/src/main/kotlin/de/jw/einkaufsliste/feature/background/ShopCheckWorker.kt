package de.jw.einkaufsliste.feature.background

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.jw.einkaufsliste.appContainer
import java.util.concurrent.TimeUnit

/** Runs one shop check; scheduled periodically by [CheckScheduler] and once after boot. */
class ShopCheckWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val name = inputData.getString(KEY_TRIGGER)
        val trigger = CheckTrigger.entries.firstOrNull { it.name == name } ?: CheckTrigger.PERIODIC
        applicationContext.appContainer.shopCheckRunner.run(trigger)
        // Never retried: a failed check (no location, no network) is simply repeated by the next run.
        return Result.success()
    }

    companion object {
        private const val TAG = "ShopCheckWorker"

        /** Input data key: the [CheckTrigger] name; missing means [CheckTrigger.PERIODIC]. */
        const val KEY_TRIGGER = "trigger"

        private const val ONE_TIME_WORK_NAME = "shop_check_once"

        /** Enqueues a single check (kept if one is already waiting). Never throws. */
        fun enqueueOnce(context: Context, trigger: CheckTrigger, delayMinutes: Long = 0) {
            try {
                val request = OneTimeWorkRequestBuilder<ShopCheckWorker>()
                    .setInputData(Data.Builder().putString(KEY_TRIGGER, trigger.name).build())
                    .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                    .build()
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.KEEP, request)
            } catch (e: Exception) {
                Log.w(TAG, "Could not enqueue check", e)
            }
        }
    }
}
