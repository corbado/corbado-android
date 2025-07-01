package com.corbado.connect.core

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.corbado.connect.api.models.PasskeyOperation
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Date

@Serializable
internal enum class LoginIdentifierType {
    EMAIL, PHONE, USERNAME
}

@Serializable
internal enum class PasskeyCeremonyType {
    LOCAL, CDA, SECURITY_KEY
}

@Serializable
internal data class LastLogin(
    val identifierType: LoginIdentifierType,
    val identifierValue: String,
    val ceremonyType: PasskeyCeremonyType,
    val operationType: String
) {
    companion object {
        fun from(passkeyOperation: PasskeyOperation): LastLogin {
            val ceremonyType = when (passkeyOperation.ceremonyType) {
                PasskeyOperation.CeremonyType.local -> PasskeyCeremonyType.LOCAL
                PasskeyOperation.CeremonyType.cda -> PasskeyCeremonyType.CDA
                PasskeyOperation.CeremonyType.securityMinusKey -> PasskeyCeremonyType.SECURITY_KEY
            }

            val identifierType = when (passkeyOperation.identifierType) {
                com.corbado.connect.api.models.LoginIdentifierType.email -> LoginIdentifierType.EMAIL
                com.corbado.connect.api.models.LoginIdentifierType.phone -> LoginIdentifierType.PHONE
                com.corbado.connect.api.models.LoginIdentifierType.username -> LoginIdentifierType.USERNAME
            }

            return LastLogin(
                identifierType = identifierType,
                identifierValue = passkeyOperation.identifierValue,
                ceremonyType = ceremonyType,
                operationType = passkeyOperation.operationType.value
            )
        }
    }
}

@Serializable
internal enum class Source(val metaSource: String) {
    LOCAL_STORAGE("ls"),
    URL("url"),
    NATIVE("native")
}

@Serializable
internal data class ClientStateEntry<T>(
    val data: T?,
    val source: Source,
    val ts: Long
)

internal class ClientStateService(context: Context, private val projectId: String) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("CorbadoClientState", Context.MODE_PRIVATE)

    private var cachedLastLogin: ClientStateEntry<LastLogin>? = null
    private var cachedClientEnvHandle: ClientStateEntry<String>? = null
    private var cachedInvitationToken: ClientStateEntry<String>? = null

    init {
        cachedLastLogin = getEntryFromUserDefaults(getStorageKeyLastLogin())
        cachedClientEnvHandle = getEntryFromUserDefaults(getStorageKeyClientHandle())
        cachedInvitationToken = getEntryFromUserDefaults(getStorageKeyInvitationToken())
    }

    fun getLastLogin(): ClientStateEntry<LastLogin>? {
        return cachedLastLogin
    }

    fun setLastLogin(lastLogin: LastLogin) {
        val entry = ClientStateEntry(lastLogin, Source.NATIVE, Date().time / 1000)
        cachedLastLogin = entry
        setEntryToUserDefaults(entry, getStorageKeyLastLogin())
    }

    fun clearLastLogin() {
        cachedLastLogin = null
        removeEntryFromUserDefaults(getStorageKeyLastLogin())
    }

    fun getClientEnvHandle(): ClientStateEntry<String>? {
        return cachedClientEnvHandle
    }

    fun setClientEnvHandle(clientEnvHandle: String) {
        val entry = ClientStateEntry(clientEnvHandle, Source.LOCAL_STORAGE, Date().time / 1000)
        cachedClientEnvHandle = entry
        setEntryToUserDefaults(entry, getStorageKeyClientHandle())
    }

    fun clearClientEnvHandle() {
        cachedClientEnvHandle = null
        removeEntryFromUserDefaults(getStorageKeyClientHandle())
    }

    fun getInvitationToken(): ClientStateEntry<String>? {
        return cachedInvitationToken
    }

    fun setInvitationToken(invitationToken: String) {
        val entry = ClientStateEntry(invitationToken, Source.NATIVE, Date().time / 1000)
        cachedInvitationToken = entry
        setEntryToUserDefaults(entry, getStorageKeyInvitationToken())
    }

    fun clearInvitationToken() {
        cachedInvitationToken = null
        removeEntryFromUserDefaults(getStorageKeyInvitationToken())
    }

    fun clearAll() {
        clearLastLogin()
        clearClientEnvHandle()
        clearInvitationToken()
    }

    private fun getStorageKeyClientHandle(): String = "cbo_client_handle-$projectId"
    private fun getStorageKeyLastLogin(): String = "cbo_connect_last_login-$projectId"
    private fun getStorageKeyInvitationToken(): String = "cbo_connect_invitation_token-$projectId"

    private inline fun <reified T> getEntryFromUserDefaults(key: String): ClientStateEntry<T>? {
        val json = prefs.getString(key, null) ?: return null
        return try {
            Json.decodeFromString(json)
        } catch (e: Exception) {
            // Log error
            null
        }
    }

    private inline fun <reified T> setEntryToUserDefaults(entry: ClientStateEntry<T>?, key: String) {
        if (entry == null) {
            prefs.edit { remove(key) }
            return
        }
        val json = Json.encodeToString(entry)
        prefs.edit { putString(key, json) }
    }

    private fun removeEntryFromUserDefaults(key: String) {
        prefs.edit { remove(key) }
    }
} 