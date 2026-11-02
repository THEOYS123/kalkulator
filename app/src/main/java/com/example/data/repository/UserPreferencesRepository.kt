package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.dataStore by preferencesDataStore(name = "netsubnet_prefs")

class UserPreferencesRepository(private val context: Context) {

    companion object {
        private val KEY_PIN_HASH = stringPreferencesKey("vault_pin_hash")
        private val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("vault_biometric_enabled")
        private val KEY_FAILED_ATTEMPTS = intPreferencesKey("vault_failed_attempts")
        private val KEY_LOCKOUT_UNTIL = longPreferencesKey("vault_lockout_until")
        private val KEY_SERVER_URL = stringPreferencesKey("cloud_server_url")
        private val KEY_AUTH_TOKEN = stringPreferencesKey("cloud_auth_token")
        private val KEY_AUTH_USERNAME = stringPreferencesKey("cloud_auth_username")
        private val KEY_HISTORY_JSON = stringPreferencesKey("calc_history_json")
        private val KEY_SECRET_TRIGGER_INFO_SEEN = booleanPreferencesKey("vault_trigger_info_seen")
        private val KEY_SECRET_IP_CODE = stringPreferencesKey("vault_secret_ip_code")

        const val DEFAULT_PIN = "1234"
        const val DEFAULT_SECRET_IP = "192.168.99.99"
        const val DEFAULT_SERVER_URL = "http://10.0.2.2/backend_php/api.php"
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    val pinHashFlow: Flow<String?> = context.dataStore.data.map { it[KEY_PIN_HASH] }
    val secretIpCodeFlow: Flow<String> = context.dataStore.data.map { it[KEY_SECRET_IP_CODE] ?: DEFAULT_SECRET_IP }
    val biometricEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_BIOMETRIC_ENABLED] ?: false }
    val failedAttemptsFlow: Flow<Int> = context.dataStore.data.map { it[KEY_FAILED_ATTEMPTS] ?: 0 }
    val lockoutUntilFlow: Flow<Long> = context.dataStore.data.map { it[KEY_LOCKOUT_UNTIL] ?: 0L }
    val serverUrlFlow: Flow<String> = context.dataStore.data.map { it[KEY_SERVER_URL] ?: DEFAULT_SERVER_URL }
    val authTokenFlow: Flow<String?> = context.dataStore.data.map { it[KEY_AUTH_TOKEN] }
    val authUsernameFlow: Flow<String?> = context.dataStore.data.map { it[KEY_AUTH_USERNAME] }
    val historyJsonFlow: Flow<String?> = context.dataStore.data.map { it[KEY_HISTORY_JSON] }

    suspend fun setSecretIpCode(code: String) {
        context.dataStore.edit { it[KEY_SECRET_IP_CODE] = code.trim() }
    }

    suspend fun verifyPin(enteredPin: String): Boolean {
        val enteredHash = hashPin(enteredPin)
        val prefs = context.dataStore.data.first()
        val storedHash = prefs[KEY_PIN_HASH]
        val targetHash = storedHash ?: hashPin(DEFAULT_PIN)
        return enteredHash == targetHash
    }

    suspend fun setPin(newPin: String) {
        val hash = hashPin(newPin)
        context.dataStore.edit {
            it[KEY_PIN_HASH] = hash
            it[KEY_FAILED_ATTEMPTS] = 0
            it[KEY_LOCKOUT_UNTIL] = 0L
        }
    }

    suspend fun recordFailedAttempt(): Pair<Int, Long> {
        var attempts = 0
        var lockoutUntil = 0L
        context.dataStore.edit {
            attempts = (it[KEY_FAILED_ATTEMPTS] ?: 0) + 1
            it[KEY_FAILED_ATTEMPTS] = attempts
            if (attempts >= 5) {
                // Lockout for 30 seconds
                lockoutUntil = System.currentTimeMillis() + 30_000L
                it[KEY_LOCKOUT_UNTIL] = lockoutUntil
            }
        }
        return Pair(attempts, lockoutUntil)
    }

    suspend fun resetFailedAttempts() {
        context.dataStore.edit {
            it[KEY_FAILED_ATTEMPTS] = 0
            it[KEY_LOCKOUT_UNTIL] = 0L
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[KEY_SERVER_URL] = url.trim() }
    }

    suspend fun saveAuthSession(token: String, username: String) {
        context.dataStore.edit {
            it[KEY_AUTH_TOKEN] = token
            it[KEY_AUTH_USERNAME] = username
        }
    }

    suspend fun clearAuthSession() {
        context.dataStore.edit {
            it.remove(KEY_AUTH_TOKEN)
            it.remove(KEY_AUTH_USERNAME)
        }
    }

    suspend fun saveHistoryJson(json: String) {
        context.dataStore.edit { it[KEY_HISTORY_JSON] = json }
    }
}
