package de.jw.einkaufsliste.feature.background

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Runs [block] and returns its result, or null after logging [what] when it fails.
 *
 * Cancellation of the calling coroutine still propagates; a [CancellationException] from
 * elsewhere (for example a Play services Task that was cancelled) counts as a failure.
 */
internal suspend inline fun <T> runLogging(tag: String, what: String, block: () -> T): T? =
    try {
        block()
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
        Log.w(tag, what, e)
        null
    } catch (e: Exception) {
        Log.w(tag, what, e)
        null
    }
