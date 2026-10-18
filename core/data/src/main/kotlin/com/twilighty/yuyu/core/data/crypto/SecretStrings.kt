package com.twilighty.yuyu.core.data.crypto

import java.security.MessageDigest

/** String helpers over [SecretCipher]: base64 (no wrap) ciphertext for storage. */
fun SecretCipher.encryptToBase64(plaintext: String): String =
    Base64Codec.encode(encrypt(plaintext.toByteArray(Charsets.UTF_8)))

/** Returns null when the value can't be decrypted (e.g. the Keystore key was reset). */
fun SecretCipher.decryptFromBase64(encoded: String): String? = runCatching {
    String(decrypt(Base64Codec.decode(encoded)), Charsets.UTF_8)
}.getOrNull()

/** Lower-case hex SHA-256, used to match ticket tokens offline without storing them. */
fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/** java.util.Base64 is API 26+, which is our minSdk; android.util.Base64 is unavailable on the JVM. */
internal object Base64Codec {
    fun encode(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)
    fun decode(text: String): ByteArray = java.util.Base64.getDecoder().decode(text)
}
