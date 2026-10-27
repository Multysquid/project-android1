package de.jw.einkaufsliste.feature.settings

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException

/** Backup files larger than this are refused on import. */
internal const val MAX_BACKUP_BYTES = 5L * 1024 * 1024

/** MIME types offered when picking a backup: file managers label `.json` files differently. */
internal val BACKUP_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

/** The picked file is larger than [MAX_BACKUP_BYTES]. */
internal class BackupTooLargeException : IOException("Backup file larger than $MAX_BACKUP_BYTES bytes")

/**
 * "Save as" for a backup: the input is the suggested file name, the result the chosen
 * document or null when cancelled. Same as `ActivityResultContracts.CreateDocument("application/json")`,
 * plus CATEGORY_OPENABLE so only providers that can write a stream are offered.
 */
internal class CreateBackupDocument : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, input)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent.takeIf { resultCode == Activity.RESULT_OK }?.data
}

/**
 * Picks a backup file: the input is the accepted MIME types, the result the chosen document
 * or null when cancelled. Same as `ActivityResultContracts.OpenDocument()`, plus
 * CATEGORY_OPENABLE so virtual files without a byte stream are not offered.
 */
internal class OpenBackupDocument : ActivityResultContract<Array<String>, Uri?>() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, input)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent.takeIf { resultCode == Activity.RESULT_OK }?.data
}

/** Writes [text] as UTF-8 to [uri], replacing what was there. Blocking: call on Dispatchers.IO. */
internal fun ContentResolver.writeBackup(uri: Uri, text: String) {
    // "wt" truncates: plain "w" leaves the tail of a longer old file behind on some providers.
    val output = openOutputStream(uri, "wt") ?: throw FileNotFoundException("No output stream for $uri")
    output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
}

/**
 * Reads [uri] as UTF-8, at most [MAX_BACKUP_BYTES] (else [BackupTooLargeException]).
 * Blocking: call on Dispatchers.IO.
 */
internal fun ContentResolver.readBackup(uri: Uri): String {
    val input = openInputStream(uri) ?: throw FileNotFoundException("No input stream for $uri")
    return input.use { stream ->
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_BACKUP_BYTES) throw BackupTooLargeException()
            bytes.write(buffer, 0, read)
        }
        bytes.toByteArray().toString(Charsets.UTF_8)
    }
}
