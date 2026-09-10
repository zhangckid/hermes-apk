package win.catgo.gpt.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureSessionStore(context: Context, namespace: String = "catgo_secure_session") {
    private val preferences = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)

    fun read(): String? {
        val encoded = preferences.getString(KEY_PAYLOAD, null) ?: return null
        return runCatching {
            val allBytes = Base64.decode(encoded, Base64.NO_WRAP)
            val ivLength = allBytes.first().toInt() and 0xff
            val iv = allBytes.copyOfRange(1, 1 + ivLength)
            val encrypted = allBytes.copyOfRange(1 + ivLength, allBytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted).decodeToString()
        }.getOrElse {
            clear()
            null
        }
    }

    fun write(value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.encodeToByteArray())
        val payload = byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + encrypted
        preferences.edit()
            .putString(KEY_PAYLOAD, Base64.encodeToString(payload, Base64.NO_WRAP))
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_PAYLOAD = "encrypted_cookies"
        const val KEY_ALIAS = "catgo-gpt-session-key-v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
