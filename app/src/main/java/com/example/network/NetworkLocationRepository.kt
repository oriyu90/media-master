package com.example.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.networkDataStore: DataStore<Preferences> by preferencesDataStore(name = "network_locations")

/** Persists the list of saved [NetworkLocation]s as JSON in a dedicated DataStore. */
class NetworkLocationRepository(private val context: Context) {

    private val key = stringPreferencesKey("locations_json")
    private val json = Json { ignoreUnknownKeys = true }

    val locations: Flow<List<NetworkLocation>> = context.networkDataStore.data.map { prefs ->
        prefs[key]?.let { raw ->
            runCatching { json.decodeFromString<List<NetworkLocation>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    private suspend fun write(list: List<NetworkLocation>) {
        context.networkDataStore.edit { it[key] = json.encodeToString(list) }
    }

    /** Insert or replace by id (atomic read-modify-write inside edit {}). */
    suspend fun upsert(location: NetworkLocation) {
        context.networkDataStore.edit { prefs ->
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString<List<NetworkLocation>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val next = current.filterNot { it.id == location.id } + location
            prefs[key] = json.encodeToString(next)
        }
    }

    suspend fun delete(id: String) {
        context.networkDataStore.edit { prefs ->
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString<List<NetworkLocation>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[key] = json.encodeToString(current.filterNot { it.id == id })
        }
    }
}
