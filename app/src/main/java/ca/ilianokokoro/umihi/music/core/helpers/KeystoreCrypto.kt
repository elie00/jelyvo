package ca.ilianokokoro.umihi.music.core.helpers

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Symmetric encryption for sensitive local values (e.g. session cookies) backed by a
 * non-exportable AES key in the Android Keystore. The key never leaves the device, so an
 * encrypted blob is useless if extracted via backup, root or malware.
 */
object KeystoreCrypto {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "umihi_secret_key"
    private const val TRANSFORMATION =
        "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128

    // Marks a blob written by the current format. ':' is outside the Base64 alphabet, so a
    // prefixed value can never be mistaken for one written before the prefix existed: a prefixed
    // blob that fails to decrypt is a genuine failure, not a legacy plaintext value.
    private const val VERSION_PREFIX = "v1:"

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    /**
     * Encrypts [plainText], returning [VERSION_PREFIX] + Base64(iv + ciphertext), or null when the
     * Keystore is unusable. Callers must not fall back to storing [plainText]: persisting a session
     * cookie in clear is worse than losing it.
     */
    fun encrypt(plainText: String): String? {
        if (plainText.isEmpty()) return plainText
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = cipher.iv + cipherText
            VERSION_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            printe(message = "Encryption failed", exception = e)
            null
        }
    }

    /**
     * Decrypts a value produced by [encrypt].
     *
     * Returns null when a [VERSION_PREFIX] blob cannot be decrypted: the key is device-bound, so
     * that is what a restored backup or a wiped Keystore looks like. Callers must read null as
     * "logged out" — returning the ciphertext would make a dead session look like a live one.
     *
     * Values without the prefix were written before it existed and are either plaintext or
     * unprefixed ciphertext, which cannot be told apart; they keep the old lenient behaviour so
     * existing sessions survive the upgrade, and are rewritten prefixed on the next save.
     */
    fun decrypt(stored: String): String? {
        if (stored.isEmpty()) return stored
        if (!stored.startsWith(VERSION_PREFIX)) {
            return decryptBlob(stored) ?: stored
        }
        return decryptBlob(stored.removePrefix(VERSION_PREFIX)).also {
            if (it == null) {
                printe(message = "Decryption failed, dropping the stored value")
            }
        }
    }

    /** Decrypts Base64(iv + ciphertext); null when the input is not decryptable with the key. */
    private fun decryptBlob(blob: String): String? {
        return try {
            val combined = Base64.decode(blob, Base64.NO_WRAP)
            if (combined.size <= IV_LENGTH) return null
            val iv = combined.copyOfRange(0, IV_LENGTH)
            val cipherText = combined.copyOfRange(IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(TAG_LENGTH_BITS, iv)
            )
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
