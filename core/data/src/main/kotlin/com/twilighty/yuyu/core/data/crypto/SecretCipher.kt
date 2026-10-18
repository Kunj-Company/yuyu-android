package com.twilighty.yuyu.core.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Authenticated encryption for secrets at rest (session tokens, cached ticket tokens). */
interface SecretCipher {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(ciphertext: ByteArray): ByteArray
}

/**
 * AES-256-GCM with a random 12-byte IV prefixed to the ciphertext
 * (`iv || ciphertext+tag`). The key comes from [keyProvider]; in the app that
 * is a non-exportable Android Keystore key ([AndroidKeystoreKeyProvider]).
 * Replaces the deprecated androidx.security-crypto.
 */
open class AesGcmSecretCipher(private val keyProvider: () -> SecretKey) : SecretCipher {
    private val random = SecureRandom()

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val key = keyProvider()
        // Non-exportable Keystore keys (encoded == null) must generate their own IV
        // (randomized encryption is enforced); software keys get one from SecureRandom.
        if (key.encoded == null) {
            cipher.init(Cipher.ENCRYPT_MODE, key)
        } else {
            val iv = ByteArray(IV_BYTES).also(random::nextBytes)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "Unexpected IV length" }
        return iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        require(ciphertext.size > IV_BYTES) { "Ciphertext too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_BITS, ciphertext, 0, IV_BYTES))
        return cipher.doFinal(ciphertext, IV_BYTES, ciphertext.size - IV_BYTES)
    }

    companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/** Creates (once) and loads the app's AES key inside the Android Keystore. */
class AndroidKeystoreKeyProvider(private val alias: String = KEY_ALIAS) : () -> SecretKey {
    @Volatile
    private var cached: SecretKey? = null

    @Synchronized
    override fun invoke(): SecretKey {
        cached?.let { return it }
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
        val key = existing ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
        cached = key
        return key
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "yuyu_secrets_v1"
    }
}

@Singleton
class KeystoreSecretCipher @Inject constructor() : AesGcmSecretCipher(AndroidKeystoreKeyProvider())
