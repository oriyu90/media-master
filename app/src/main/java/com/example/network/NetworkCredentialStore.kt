package com.example.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores network-location passwords encrypted at rest (AES-256, key in the
 * Android Keystore). Passwords are never written to DataStore, logs, or the
 * [NetworkLocation] JSON.
 *
 * If the encrypted store cannot be opened (rare: corrupted keyset), every
 * accessor degrades gracefully to "no password" rather than throwing.
 */
class NetworkCredentialStore(context: Context) {

    private val prefs: SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "network_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()

    fun setPassword(locationId: String, password: String) {
        prefs?.edit()?.putString(locationId, password)?.apply()
    }

    fun getPassword(locationId: String): String = prefs?.getString(locationId, null).orEmpty()

    fun removePassword(locationId: String) {
        prefs?.edit()?.remove(locationId)?.apply()
    }
}
