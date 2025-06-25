package io.corbado.connect

import android.content.Context
import com.corbado.api.models.ClientInformation
import com.corbado.api.models.ClientStateMeta
import com.corbado.api.v1.CorbadoConnectApi
import io.corbado.simplecredentialmanager.AuthorizationController
import io.corbado.simplecredentialmanager.real.RealAuthorizationController
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Placeholder data classes for state management
internal data class ConnectProcess(
    val id: String,
    val frontendApiUrl: String,
    var loginData: ConnectLoginInitData? = null,
    var appendData: ConnectAppendInitData? = null,
    var manageData: ConnectManageInitData? = null,
    var attestationOptions: String? = null,
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

// Placeholder for error handling
data class AuthError(val code: String, val message: String)

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
    val description: String? = null
) : Exception(description)

val defaultErrorMessage = "Passkey error. Use password to log in."
val defaultAuthError = AuthError(code = "unavailable", message = "Passkey error. Use password to log in.")

class Corbado(
    private val projectId: String,
    private val context: Context,
    private val frontendApiUrlSuffix: String? = null,
    private val authorizationController: AuthorizationController? = null,
    internal val useOneTap: Boolean = true
) {
    internal val client: CorbadoClient
    internal val clientStateService: ClientStateService
    internal var authController: AuthorizationController
    internal var process: ConnectProcess? = null
    internal var loginInitCompleted: Long? = null
    internal var appendInitCompleted: Long? = null

    init {
        client = CorbadoClient(projectId, frontendApiUrlSuffix)
        clientStateService = ClientStateService(context, projectId)
        authController = authorizationController ?: RealAuthorizationController(context)
    }

    // Control methods
    fun clearLocalState() {
        clientStateService.clearAll()
    }

    fun setInvitationToken(token: String) {
        clientStateService.setInvitationToken(token)
    }

    fun setBlockedUrls(urls: List<String>) {
        client.setBlockedUrls(urls)
    }

    fun clearProcess() {
        process = null
        client.setProcessId(null)
    }

    internal fun buildClientInfo(): ClientInformation {
        val clientEnvHandleEntry = clientStateService.getClientEnvHandle()
        val clientStateMeta = clientEnvHandleEntry?.let {
            ClientStateMeta(ts = it.ts, source = ClientStateMeta.Source.native)
        }

        return ClientInformation(
            clientEnvHandle = clientEnvHandleEntry?.data,
            isNative = true,
            clientEnvHandleMeta = clientStateMeta,
            nativeMeta = PasskeyClientTelemetryCollector.collectData(context)
        )
    }
} 