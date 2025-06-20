package io.corbado.connect

import com.corbado.api.models.*
import com.corbado.api.v1.CorbadoConnectApi
import io.corbado.simplecredentialmanager.PublicKeyCredentialAssertion
import io.corbado.simplecredentialmanager.PublicKeyCredentialRegistration
import io.corbado.simplecredentialmanager.PublicKeyCredentialRegistration.PublicKeyCredentialRegistrationOptions
import io.corbado.simplecredentialmanager.RPPlatformPublicKeyCredentialAssertion
import io.corbado.simplecredentialmanager.RPPlatformPublicKeyCredentialRegistration
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

data class AppendStartResult(
    val options: PublicKeyCredentialRegistration?,
    val variant: ConnectAppendStartRsp.Variant,
    val isRestrictedBrowser: Boolean,
    val autoAppend: Boolean
)

internal class CorbadoClient(
    projectId: String,
    frontendApiUrlSuffix: String?
) {
    private val corbadoConnectApi: CorbadoConnectApi
    private val processIdInterceptor: ProcessIdInterceptor
    private val urlBlockingInterceptor = UrlBlockingInterceptor()

    init {
        val baseUrl = "https://%s.%s".format(projectId, frontendApiUrlSuffix ?: "frontendapi.cloud.corbado.io")
        processIdInterceptor = ProcessIdInterceptor()

        val httpClient = OkHttpClient.Builder()
            .addInterceptor(processIdInterceptor)
            .addInterceptor(urlBlockingInterceptor)
            .build()

        corbadoConnectApi = CorbadoConnectApi(baseUrl, httpClient)
    }

    companion object RelyingPartyServerDeserializer {
        fun deserializeAssertion(
            assertionOptions: String
        ): PublicKeyCredentialAssertion {
            val json = Json { ignoreUnknownKeys = true }
            return json.decodeFromString<PublicKeyCredentialAssertion>(assertionOptions)
        }

        fun deserializeAttestation(
            attestationOptions: String
        ): PublicKeyCredentialRegistration {
            val json = Json { ignoreUnknownKeys = true }
            return json.decodeFromString<PublicKeyCredentialRegistration>(attestationOptions)
        }
    }

    suspend fun loginInit(clientInfo: ClientInformation, invitationToken: String?): ConnectLoginInitRsp {
        val req = ConnectLoginInitReq(
            clientInformation = clientInfo,
            flags = mapOf(),
            invitationToken = invitationToken
        )
        return corbadoConnectApi.connectLoginInit(req)
    }

    suspend fun loginStart(identifier: String, source: ConnectLoginStartReq.Source = ConnectLoginStartReq.Source.textMinusField, loadedMs: Long = 0): ConnectLoginStartRsp {
        val req = ConnectLoginStartReq(
            identifier = identifier,
            source = source,
            loadedMs = loadedMs,
        )
        return corbadoConnectApi.connectLoginStart(req)
    }

    suspend fun loginFinish(authenticatorResponse: RPPlatformPublicKeyCredentialAssertion, isConditionalUI: Boolean): ConnectLoginFinishRsp {
        val assertionResponse = AuthenticateResponse(
            id = authenticatorResponse.id,
            rawId = authenticatorResponse.rawId,
            type = authenticatorResponse.type,
            response = AuthenticateResponse.AssertionResponse(
                clientDataJSON = authenticatorResponse.response.clientDataJSON,
                authenticatorData = authenticatorResponse.response.authenticatorData,
                signature = authenticatorResponse.response.signature,
                userHandle = authenticatorResponse.response.userHandle ?: ""
            ),
        )

        val json = Json { ignoreUnknownKeys = true }
        val serializedAssertionResponse = json.encodeToString(AuthenticateResponse.serializer(), assertionResponse)

        val req = ConnectLoginFinishReq(
            isConditionalUI = isConditionalUI,
            assertionResponse = serializedAssertionResponse
        )

        return corbadoConnectApi.connectLoginFinish(req)
    }

    suspend fun appendInit(clientInfo: ClientInformation, invitationToken: String?): ConnectAppendInitRsp {
        val req = ConnectAppendInitReq(
            clientInformation = clientInfo,
            flags = mapOf(),
            invitationToken = invitationToken
        )
        return corbadoConnectApi.connectAppendInit(req)
    }

    suspend fun appendStart(connectToken: String, forcePasskeyAppend: Boolean, loadedMs: Long = 0): AppendStartResult {
        val req = ConnectAppendStartReq(
            appendTokenValue = connectToken,
            forcePasskeyAppend = forcePasskeyAppend,
            loadedMs = loadedMs
        )

        val res = corbadoConnectApi.connectAppendStart(req)
        return AppendStartResult(
            options = res.attestationOptions.takeIf { it.isNotEmpty() }?.let {
                val json = Json { ignoreUnknownKeys = true }
                json.decodeFromString<PublicKeyCredentialRegistration>(it)
            },
            variant = res.variant,
            isRestrictedBrowser = res.isRestrictedBrowser,
            autoAppend = res.autoAppend
        )
    }

    suspend fun appendFinish(authenticatorResponse: RPPlatformPublicKeyCredentialRegistration): ConnectAppendFinishRsp {
        val attestationResponse = RPPlatformPublicKeyCredentialRegistration(
            id = authenticatorResponse.id,
            rawId = authenticatorResponse.rawId,
            type = authenticatorResponse.type,
            response = RPPlatformPublicKeyCredentialRegistration.Response(
                clientDataJSON = authenticatorResponse.response.clientDataJSON,
                attestationObject = authenticatorResponse.response.attestationObject,
                transports = authenticatorResponse.response.transports ?: emptyList()
            )
        )

        val json = Json { ignoreUnknownKeys = true }
        val serializedAttestationResponse = json.encodeToString(RPPlatformPublicKeyCredentialRegistration.serializer(), attestationResponse)

        val req = ConnectAppendFinishReq(attestationResponse = serializedAttestationResponse)
        return corbadoConnectApi.connectAppendFinish(req)
    }

    suspend fun manageInit(clientInfo: ClientInformation, invitationToken: String?): ConnectManageInitRsp {
        val req = ConnectManageInitReq(
            clientInformation = clientInfo,
            flags = mapOf(),
            invitationToken = invitationToken
        )
        return corbadoConnectApi.connectManageInit(req)
    }

    suspend fun manageList(connectToken: String): ConnectManageListRsp {
        val req = ConnectManageListReq(connectToken = connectToken)
        val res = corbadoConnectApi.connectManageList(req)

        return res
    }

    suspend fun manageDelete(connectToken: String, passkeyId: String): ConnectManageDeleteRsp {
        val req = ConnectManageDeleteReq(
            connectToken = connectToken,
            credentialID = passkeyId
        )
        return corbadoConnectApi.connectManageDelete(req)
    }

    fun setProcessId(processId: String?) {
        processIdInterceptor.processId = processId
    }

    fun setBlockedUrls(urls: List<String>) {
        urlBlockingInterceptor.setBlockedUrlPaths(urls)
    }
} 