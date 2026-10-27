package de.jw.einkaufsliste.data.secure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the user's Gemini API key encrypted with an AES key that lives in the
 * Android Keystore and never leaves it.
 */
class ApiKeyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("secure", Context.MODE_PRIVATE)
    private val _hasKey = MutableStateFlow(prefs.contains(PREF_CIPHERTEXT))

    /** Whether a key is stored; the UI uses this to show or hide the photo option hint. */
    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    fun get(): String? {
        val cipherText = prefs.getString(PREF_CIPHERTEXT, null) ?: return null
        val iv = prefs.getString(PREF_IV, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            // Keystore key lost (e.g. after a device restore): the stored value is unreadable.
            clear()
            null
        }
    }

    /** Stores [apiKey]; a blank value removes the key. */
    fun set(apiKey: String?) {
        val trimmed = apiKey?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            clear()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val cipherText = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(PREF_CIPHERTEXT, Base64.encodeToString(cipherText, Base64.NO_WRAP))
            .putString(PREF_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
        _hasKey.value = true
    }

    fun clear() {
        prefs.edit().remove(PREF_CIPHERTEXT).remove(PREF_IV).apply()
        _hasKey.value = false
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "gemini_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREF_CIPHERTEXT = "gemini_key_ciphertext"
        const val PREF_IV = "gemini_key_iv"
    }
}
