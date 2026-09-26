package com.ghaith.ironhud.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ghaith.ironhud.ai.KeyStatus
import com.ghaith.ironhud.ai.ProviderId
import com.ghaith.ironhud.plane.ObserverPlace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "hud_settings")

data class HudSettings(
    val voice: Boolean = true,
    val tint: Boolean = true,
    val autoLock: Boolean = true,
    val providerOrder: List<ProviderId> = ProviderId.entries,
    /** Per-provider model override; blank or "auto" means the default. */
    val models: Map<ProviderId, String> = emptyMap(),
    val keyStatus: Map<String, KeyStatus> = emptyMap(),
    /** Plane Mode observer: a chosen place, or null to use GPS. */
    val planeObserver: ObserverPlace? = null,
    /** Plane Mode at a chosen place: draw a virtual sky instead of the camera feed. */
    val skyView: Boolean = true,
)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore
    private val json = Json { ignoreUnknownKeys = true }
    private val statusSerializer = MapSerializer(String.serializer(), KeyStatus.serializer())
    private val modelSerializer = MapSerializer(String.serializer(), String.serializer())

    val settings: Flow<HudSettings> = store.data.map { p ->
        HudSettings(
            voice = p[VOICE] ?: true,
            tint = p[TINT] ?: true,
            autoLock = p[AUTO_LOCK] ?: true,
            providerOrder = p[ORDER]?.split(',')
                ?.mapNotNull { runCatching { ProviderId.valueOf(it) }.getOrNull() }
                ?.let { (it + ProviderId.entries).distinct() }
                ?: ProviderId.entries,
            models = p[MODELS]?.let { raw ->
                runCatching { json.decodeFromString(modelSerializer, raw) }.getOrNull()
                    ?.mapNotNull { (k, v) -> runCatching { ProviderId.valueOf(k) to v }.getOrNull() }?.toMap()
            }.orEmpty(),
            keyStatus = p[STATUS]?.let { raw -> runCatching { json.decodeFromString(statusSerializer, raw) }.getOrNull() }
                .orEmpty(),
            planeObserver = p[OBSERVER]?.let { raw -> runCatching { json.decodeFromString(ObserverPlace.serializer(), raw) }.getOrNull() },
            skyView = p[SKY_VIEW] ?: true,
        )
    }

    suspend fun setVoice(on: Boolean) = store.edit { it[VOICE] = on }
    suspend fun setTint(on: Boolean) = store.edit { it[TINT] = on }
    suspend fun setAutoLock(on: Boolean) = store.edit { it[AUTO_LOCK] = on }
    suspend fun setOrder(order: List<ProviderId>) = store.edit { it[ORDER] = order.joinToString(",") { p -> p.name } }

    suspend fun setModel(provider: ProviderId, model: String) = store.edit { p ->
        val current = p[MODELS]?.let { runCatching { json.decodeFromString(modelSerializer, it) }.getOrNull() }.orEmpty()
        p[MODELS] = json.encodeToString(modelSerializer, current + (provider.name to model.trim()))
    }

    suspend fun setPlaneObserver(place: ObserverPlace?) = store.edit {
        if (place == null) {
            it.remove(OBSERVER)
        } else {
            it[OBSERVER] = json.encodeToString(ObserverPlace.serializer(), place)
        }
    }

    suspend fun setSkyView(on: Boolean) = store.edit { it[SKY_VIEW] = on }

    suspend fun saveKeyStatus(status: Map<String, KeyStatus>) = store.edit {
        it[STATUS] = json.encodeToString(statusSerializer, status)
    }

    private companion object {
        val VOICE = booleanPreferencesKey("voice")
        val TINT = booleanPreferencesKey("tint")
        val AUTO_LOCK = booleanPreferencesKey("auto_lock")
        val ORDER = stringPreferencesKey("provider_order")
        val MODELS = stringPreferencesKey("models")
        val STATUS = stringPreferencesKey("key_status")
        val OBSERVER = stringPreferencesKey("plane_observer")
        val SKY_VIEW = booleanPreferencesKey("sky_view")
    }
}
