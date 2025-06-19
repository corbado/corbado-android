package io.corbado.connect

import android.content.Context
import android.content.SharedPreferences

internal class ClientStateService(context: Context, projectId: String) {
    private val prefs: SharedPreferences

    companion object {
        private const val PREFS_NAME = "CorbadoClientState"
        private const val KEY_LAST_LOGIN = "last_login"
        private const val KEY_CLIENT_ENV_HANDLE = "client_env_handle"
        private const val KEY_INVITATION_TOKEN = "invitation_token"
    }

    init {
        // Use a unique name for the preferences file based on the project ID
        val prefsName = "$PREFS_NAME-$projectId"
        prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }

    // Example of how we'll store and retrieve data.
    // I will add more methods as needed when implementing the main flows.

    fun setLastLogin(username: String) {
        prefs.edit().putString(KEY_LAST_LOGIN, username).apply()
    }

    fun getLastLogin(): String? {
        return prefs.getString(KEY_LAST_LOGIN, null)
    }

    fun clearLastLogin() {
        prefs.edit().remove(KEY_LAST_LOGIN).apply()
    }

    fun setInvitationToken(token: String) {
        prefs.edit().putString(KEY_INVITATION_TOKEN, token).apply()
    }

    fun getInvitationToken(): String? {
        return prefs.getString(KEY_INVITATION_TOKEN, null)
    }

    fun setClientEnvHandle(handle: String, ts: Long) {
        val data = "$handle,$ts"
        prefs.edit().putString(KEY_CLIENT_ENV_HANDLE, data).apply()
    }

    fun getClientEnvHandle(): Pair<String, Long>? {
        val data = prefs.getString(KEY_CLIENT_ENV_HANDLE, null) ?: return null
        val parts = data.split(',')
        if (parts.size != 2) return null
        return Pair(parts[0], parts[1].toLongOrNull() ?: 0)
    }

    fun clearClientEnvHandle() {
        prefs.edit().remove(KEY_CLIENT_ENV_HANDLE).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
} 