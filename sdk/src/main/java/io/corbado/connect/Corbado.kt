package io.corbado.connect

import android.content.Context
import com.corbado.api.v1.CorbadoConnectApi
import io.corbado.simplecredentialmanager.AuthorizationController
import io.corbado.simplecredentialmanager.real.RealAuthorizationController
import okhttp3.OkHttpClient
import com.corbado.api.models.ClientInformation
import com.corbado.api.models.ClientStateMeta

// Placeholder data classes for state management
internal data class ConnectProcess(
    val id: String,
    val frontendApiUrl: String,
    var loginData: ConnectLoginInitData? = null,
    var appendData: ConnectAppendInitData? = null,
    var attestationOptions: String? = null,
)

internal data class ConnectLoginInitData(
    val loginAllowed: Boolean,
    val conditionalUIChallenge: String?,
    val expiresAt: Int
)

internal data class ConnectAppendInitData(
    val appendAllowed: Boolean,
    val expiresAt: Int
)

// Placeholder for error handling
data class AuthError(val code: String, val message: String)

enum class ConnectTokenType {
    PasskeyAppend,
    PasskeyDelete,
    PasskeyList,
}

val defaultAuthError = AuthError(code = "unavailable", message = "Passkey error. Use password to log in.")

class Corbado(
    private val projectId: String,
    private val context: Context,
    private val frontendApiUrlSuffix: String? = null,
    internal val useOneTap: Boolean = true
) {
    internal val client: CorbadoClient
    internal val clientStateService: ClientStateService
    internal var authController: AuthorizationController
    internal var process: ConnectProcess? = null
    private val processIdInterceptor: ProcessIdInterceptor
    internal var loginInitCompleted: Long? = null

    init {
        val baseUrl = "https://%s.%s".format(projectId, frontendApiUrlSuffix ?: "frontendapi.cloud.corbado.io")
        processIdInterceptor = ProcessIdInterceptor()

        val corbadoConnectApi = CorbadoConnectApi(baseUrl)
        client = CorbadoClient(corbadoConnectApi, processIdInterceptor)
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

    fun clearProcess() {
        process = null
    }

    internal fun buildClientInfo(): ClientInformation {
        val clientEnvHandleEntry = clientStateService.getClientEnvHandle()
        val clientStateMeta = clientEnvHandleEntry?.let {
            ClientStateMeta(ts = it.second, source = ClientStateMeta.Source.native)
        }

        return ClientInformation(
            clientEnvHandle = clientEnvHandleEntry?.first,
            isNative = true,
            clientEnvHandleMeta = clientStateMeta,
            nativeMeta = PasskeyClientTelemetryCollector.collectData()
        )
    }
} 