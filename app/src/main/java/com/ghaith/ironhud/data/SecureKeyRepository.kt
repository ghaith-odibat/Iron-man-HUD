package com.ghaith.ironhud.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ghaith.ironhud.ai.ApiKeyEntry
import com.ghaith.ironhud.ai.ProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.vaultStore: DataStore<Preferences> by preferencesDataStore(name = "hud_vault")

/**
 * API keys, AES-GCM encrypted with a key that lives in the Android Keystore (hardware-backed on
 * Samsung devices). The keys are never written anywhere in plain text and are excluded from backup.
 */
class SecureKeyRepository(context: Context) {
    private val store = context.applicationContext.vaultStore
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class StoredKey(val id: String, val provider: String, val blob: String)

    val keys: Flow<List<ApiKeyEntry>> = store.data.map { prefs ->
        decodeList(prefs[KEYS]).mapNotNull { stored ->
            val provider = runCatching { ProviderId.valueOf(stored.provider) }.getOrNull() ?: return@mapNotNull null
            val secret = runCatching { decrypt(stored.blob) }.getOrNull() ?: return@mapNotNull null
            ApiKeyEntry(stored.id, provider, secret)
        }
    }

    /** Adds a key; returns false if the exact same key is already stored. */
    suspend fun add(provider: ProviderId, secret: String): Boolean {
        val clean = secret.trim()
        if (clean.isEmpty()) return false
        var added = false
        store.edit { prefs ->
            val list = decodeList(prefs[KEYS])
            val duplicate = list.any { it.provider == provider.name && runCatching { decrypt(it.blob) }.getOrNull() == clean }
            if (!duplicate) {
                prefs[KEYS] = encodeList(list + StoredKey(UUID.randomUUID().toString(), provider.name, encrypt(clean)))
                added = true
            }
        }
        return added
    }

    suspend fun remove(id: String) {
        store.edit { prefs -> prefs[KEYS] = encodeList(decodeList(prefs[KEYS]).filterNot { it.id == id }) }
    }

    private fun decodeList(raw: String?): List<StoredKey> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(ListSerializer(StoredKey.serializer()), raw) }.getOrDefault(emptyList())

    private fun encodeList(list: List<StoredKey>) = json.encodeToString(ListSerializer(StoredKey.serializer()), list)

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + ct)
    }

    private fun decrypt(blob: String): String {
        val bytes = Base64.getDecoder().decode(blob)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }

    private companion object {
        val KEYS = stringPreferencesKey("keys_v1")
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "ironhud_vault_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
