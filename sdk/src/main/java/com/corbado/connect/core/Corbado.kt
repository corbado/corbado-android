package com.corbado.connect.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.corbado.connect.api.models.ClientInformation
import com.corbado.connect.api.models.ClientStateMeta
import com.corbado.simplecredentialmanager.AuthorizationController
import com.corbado.simplecredentialmanager.real.RealAuthorizationController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

// Placeholder data classes for state management
internal data class ConnectProcess(
    val id: String,
    val frontendApiUrl: String,
    var loginData: ConnectLoginInitData? = null,
    var appendData: ConnectAppendInitData? = null,
    var manageData: ConnectManageInitData? = null,
    var attestationOptions: String? = null,
    var attestationExpiry: Instant? = null
)

internal data class ConnectLoginInitData(
    val loginAllowed: Boolean,
    val conditionalUIChallenge: String?,
    val expiresAt: Long
)

internal data class ConnectAppendInitData(
    val appendAllowed: Boolean,
    val expiresAt: Long
)

internal data class ConnectManageInitData(
    val manageAllowed: Boolean,
    var flags: Map<String, String> = emptyMap(),
    val expiresAt: Long?
)

data class Passkey(
    val id: String,
    val tags: List<String>,
    val sourceOS: String,
    val sourceBrowser: String,
    val lastUsedMs: Long,
    val createdMs: Long,
    val aaguidDetails: AaguidDetails,
)

data class AaguidDetails(
    val name: String,
    val iconLight: String,
    val iconDark: String,
)

@Serializable
enum class ConnectTokenType {
    @SerialName("passkey-append") PasskeyAppend,
    @SerialName("passkey-delete") PasskeyDelete,
    @SerialName("passkey-list") PasskeyList
}

data class ConnectTokenError(
    override val message: String = "Error during connect token retrieval"
) : Exception(message)

const val defaultErrorMessage = "Passkey error. Use password to log in."

class Corbado(
    projectId: String,
    private val context: Context,
    frontendApiUrlSuffix: String? = null,
    internal val useOneTap: Boolean = true
) {
    internal val client: CorbadoClient
    internal val clientStateService: ClientStateService
    @VisibleForTesting
    var authController: AuthorizationController
    internal var process: ConnectProcess? = null
    internal val sdkInitTime: Instant = Instant.now()

    init {
        client = CorbadoClient(projectId, frontendApiUrlSuffix)
        clientStateService = ClientStateService(context, projectId)
        authController = RealAuthorizationController(context)
    }

    // Control methods
    fun clearLocalState() {
        clientStateService.clearAll()
    }

    fun setInvitationToken(token: String) {
        clientStateService.setInvitationToken(token)
    }

    fun cancelOnGoingPasskeyOperation() {
        authController.cancel()
    }

    fun setBlockedUrlPaths(urls: List<String>) {
        client.setBlockedUrlPaths(urls)
    }

    fun setTimeoutUrlPaths(setBlockedUrlPaths: Map<String, Long>) {
        client.setTimeoutUrlPaths(setBlockedUrlPaths)
    }

    fun clearProcess() {
        process = null
        client.setProcessId(null)
    }

    fun clearSituationDebounce() {
        clientStateService.clearSituationDebounceMap()
    }

    suspend fun recordLocalUnlock() = withContext(Dispatchers.IO) {
        client.recordLoginEvent(LoginPasskeyEvent.LocalUnlock)
    }

    internal fun buildClientInfo(): ClientInformation {
        val clientEnvHandleEntry = clientStateService.getClientEnvHandle()
        val clientStateMeta = clientEnvHandleEntry?.let {
            ClientStateMeta(ts = it.ts, source = ClientStateMeta.Source.native)
        }

        val nativeMeta = PasskeyClientTelemetryCollector.collectData(context, sdkInitTime)
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.d("CorbadoSDK", "Device telemetry: $nativeMeta")
        }

        return ClientInformation(
            clientEnvHandle = clientEnvHandleEntry?.data,
            isNative = true,
            clientEnvHandleMeta = clientStateMeta,
            nativeMeta = nativeMeta
        )
    }
} 