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

    /** Encrypts [plainText], returning Base64(iv + ciphertext). Returns [plainText] on failure. */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return plainText
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = cipher.iv + cipherText
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            printe(message = "Encryption failed", exception = e)
            plainText
        }
    }

    /**
     * Decrypts a value produced by [encrypt]. If [stored] is not valid ciphertext (e.g. a legacy
     * plaintext value written before encryption was introduced), it is returned unchanged so that
     * existing sessions are not lost; it will be re-encrypted on the next write.
     */
    fun decrypt(stored: String): String {
        if (stored.isEmpty()) return stored
        return try {
            val combined = Base64.decode(stored, Base64.NO_WRAP)
            if (combined.size <= IV_LENGTH) return stored
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
            // Legacy plaintext (or corrupted) value — return as-is for backward compatibility.
            stored
        }
    }
}
