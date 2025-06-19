package io.corbado.connect

import com.corbado.api.models.*
import com.corbado.api.v1.CorbadoConnectApi
import io.corbado.simplecredentialmanager.RPPlatformPublicKeyCredentialAssertion
import kotlinx.serialization.json.Json

internal class CorbadoClient(
    private val corbadoConnectApi: CorbadoConnectApi,
    private val processIdInterceptor: ProcessIdInterceptor
) {
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

    suspend fun appendStart(connectToken: String, forcePasskeyAppend: Boolean, loadedMs: Long = 0): ConnectAppendStartRsp {
        val req = ConnectAppendStartReq(
            appendTokenValue = connectToken,
            forcePasskeyAppend = forcePasskeyAppend,
            loadedMs = loadedMs
        )
        return corbadoConnectApi.connectAppendStart(req)
    }

    suspend fun appendFinish(attestationResponse: String): ConnectAppendFinishRsp {
        val req = ConnectAppendFinishReq(attestationResponse = attestationResponse)
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
        return corbadoConnectApi.connectManageList(req)
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
} 