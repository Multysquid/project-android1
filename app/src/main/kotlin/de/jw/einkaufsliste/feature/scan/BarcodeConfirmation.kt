package de.jw.einkaufsliste.feature.scan

import de.jw.einkaufsliste.core.products.OpenFactsApi

/**
 * Decides when a barcode read from camera frames counts: it needs a valid check digit
 * and must be read in two frames in a row. The code accepted last is ignored until it
 * has been out of view for [ignoreMillis], so a product still held in front of the
 * camera after adding it does not pop up again.
 */
class BarcodeConfirmation(private val ignoreMillis: Long = 1_500L) {
    private var candidate: String? = null
    private var ignored: String? = null
    private var ignoredSeenAt = 0L

    /** Takes the codes read in one frame; returns the code that now counts, or null. */
    fun onFrame(codes: List<String>, nowMillis: Long): String? {
        val valid = codes.map { it.trim() }.filter { OpenFactsApi.isValidBarcode(it) }
        ignored?.let { code ->
            if (code in valid) {
                ignoredSeenAt = nowMillis
            } else if (nowMillis - ignoredSeenAt > ignoreMillis) {
                ignored = null
            }
        }
        val code = valid.firstOrNull { it != ignored }
        if (code == null || code != candidate) {
            candidate = code
            return null
        }
        candidate = null
        ignored = code
        ignoredSeenAt = nowMillis
        return code
    }

    /** Forgets a code seen in only one frame so far. */
    fun reset() {
        candidate = null
    }

    /**
     * Counts the ignored code as seen at [nowMillis]. Called when frames are looked at again
     * after a pause, during which nothing could have told whether it is still in view.
     */
    fun restartIgnoreTimer(nowMillis: Long) {
        if (ignored != null) ignoredSeenAt = nowMillis
    }
}
