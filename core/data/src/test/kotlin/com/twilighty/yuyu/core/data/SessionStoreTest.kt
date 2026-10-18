package com.twilighty.yuyu.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import com.twilighty.yuyu.core.data.crypto.AesGcmSecretCipher
import com.twilighty.yuyu.core.data.crypto.decryptFromBase64
import com.twilighty.yuyu.core.data.crypto.encryptToBase64
import com.twilighty.yuyu.core.data.session.SessionStore
import com.twilighty.yuyu.core.data.session.StoredSession
import com.twilighty.yuyu.core.network.model.UserDto
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The Keystore only exists on devices, so these tests drive the same
 * AES-GCM code path with a software key ([AesGcmSecretCipher]).
 */
class SessionStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AesGcmSecretCipher { key }

    private val session = StoredSession(
        accessToken = "yma_access",
        accessTokenExpiresAt = "2030-01-01T00:15:00Z",
        refreshToken = "ymr_refresh",
        refreshTokenExpiresAt = "2030-03-01T00:00:00Z",
        user = UserDto(id = "u1", name = "Ada", email = "ada@example.test"),
    )

    @Test
    fun cipherRoundTripsAndUsesAFreshIv() {
        val a = cipher.encryptToBase64("ymr_refresh")
        val b = cipher.encryptToBase64("ymr_refresh")
        assertNotEquals(a, b)
        assertEquals("ymr_refresh", cipher.decryptFromBase64(a))
        assertFalse(a.contains("ymr_"))
    }

    @Test
    fun tamperedOrForeignCiphertextIsRejected() {
        val encrypted = java.util.Base64.getDecoder().decode(cipher.encryptToBase64("secret"))
        encrypted[encrypted.size - 1] = (encrypted.last().toInt() xor 1).toByte()
        assertNull(cipher.decryptFromBase64(java.util.Base64.getEncoder().encodeToString(encrypted)))
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertNull(AesGcmSecretCipher { otherKey }.decryptFromBase64(cipher.encryptToBase64("secret")))
    }

    @Test
    fun sessionPersistsEncryptedAndReloads() = runTest {
        val file = File(tmp.root, "session.preferences_pb")
        val scope1 = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store1 = PreferenceDataStoreFactory.create(scope = scope1) { file }
        SessionStore(store1, cipher).save(session)

        // What is on disk is ciphertext only.
        val raw = store1.data.first()[stringPreferencesKey("session_v1")]!!
        assertFalse(raw.contains("yma_access"))
        assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("ymr_refresh"))
        scope1.cancel()

        // A new process (new DataStore + store) decrypts the same session.
        val scope2 = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val reloaded = SessionStore(PreferenceDataStoreFactory.create(scope = scope2) { file }, cipher)
        assertEquals(session, reloaded.load())
        assertEquals("yma_access", reloaded.accessToken())
        assertEquals("ymr_refresh", reloaded.refreshToken())

        reloaded.onSessionInvalid()
        assertNull(reloaded.accessToken())
        assertNull(reloaded.session.value)
        scope2.cancel()
    }

    @Test
    fun undecryptableSessionLoadsAsSignedOut() = runTest {
        val file = File(tmp.root, "broken.preferences_pb")
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        SessionStore(dataStore, cipher).save(session)
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertNull(SessionStore(dataStore, AesGcmSecretCipher { otherKey }).load())
        scope.cancel()
    }
}
