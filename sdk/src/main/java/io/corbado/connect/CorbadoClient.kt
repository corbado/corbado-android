package com.corbado.connect

import com.corbado.api.models.ClientInformation
import com.corbado.api.models.ConnectAppendFinishReq
import com.corbado.api.models.ConnectAppendFinishRsp
import com.corbado.api.models.ConnectAppendInitReq
import com.corbado.api.models.ConnectAppendInitRsp
import com.corbado.api.models.ConnectAppendStartReq
import com.corbado.api.models.ConnectAppendStartRsp
import com.corbado.api.models.ConnectEventCreateReq
import com.corbado.api.models.ConnectLoginFinishReq
import com.corbado.api.models.ConnectLoginFinishRsp
import com.corbado.api.models.ConnectLoginInitReq
import com.corbado.api.models.ConnectLoginInitRsp
import com.corbado.api.models.ConnectLoginStartReq
import com.corbado.api.models.ConnectLoginStartRsp
import com.corbado.api.models.ConnectManageDeleteReq
import com.corbado.api.models.ConnectManageDeleteRsp
import com.corbado.api.models.ConnectManageInitReq
import com.corbado.api.models.ConnectManageInitRsp
import com.corbado.api.models.ConnectManageListReq
import com.corbado.api.models.ConnectManageListRsp
import com.corbado.api.models.PasskeyEventType
import com.corbado.api.v1.CorbadoConnectApi
import com.corbado.simplecredentialmanager.PublicKeyCredentialAssertion
import com.corbado.simplecredentialmanager.PublicKeyCredentialRegistration
import com.corbado.simplecredentialmanager.RPPlatformPublicKeyCredentialAssertion
import com.corbado.simplecredentialmanager.RPPlatformPublicKeyCredentialRegistration
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

data class AppendStartResult(
    val options: PublicKeyCredentialRegistration?,
    val variant: ConnectAppendStartRsp.Variant,
    val isRestrictedBrowser: Boolean,
    val autoAppend: Boolean
)

sealed class LoginPasskeyEvent {
    data object LoginExplicitAbort : LoginPasskeyEvent()
    data class LoginError(val exception: Exception) : LoginPasskeyEvent()
    data class LoginErrorUnexpected(val exception: Exception) : LoginPasskeyEvent()
    object LoginOneTapSwitch : LoginPasskeyEvent()
    object LoginNoCredentials : LoginPasskeyEvent()
    object LocalUnlock : LoginPasskeyEvent()
}

sealed class AppendPasskeyEvent {
    data object AppendExplicitAbort : AppendPasskeyEvent()
    data class AppendCredentialExists(val exception: Exception) : AppendPasskeyEvent()
    data class AppendError(val exception: Exception) : AppendPasskeyEvent()
    data class AppendErrorUnexpected(val exception: Exception) : AppendPasskeyEvent()
    object AppendLearnMore : AppendPasskeyEvent()
}

sealed class ManagePasskeyEvent {
    data class ManageError(val exception: Exception) : ManagePasskeyEvent()
    data class ManageCredentialExists(val exception: Exception) : ManagePasskeyEvent()
    data class ManageErrorUnexpected(val exception: Exception) : ManagePasskeyEvent()
    object ManageLearnMore : ManagePasskeyEvent()
}


internal class CorbadoClient(
    projectId: String,
    frontendApiUrlSuffix: String?
) {
    private val corbadoConnectApi: CorbadoConnectApi
    private val processIdInterceptor: ProcessIdInterceptor
    private val urlBlockingInterceptor = UrlBlockingInterceptor()

    init {
        val baseUrl = "https://%s.%s".format(
            projectId,
            frontendApiUrlSuffix ?: "frontendapi.cloud.corbado.io"
        )
        processIdInterceptor = ProcessIdInterceptor()

        val httpClient = OkHttpClient.Builder()
            .addInterceptor(processIdInterceptor)
            .addInterceptor(urlBlockingInterceptor)
            .callTimeout(10, TimeUnit.SECONDS)
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

    fun loginInit(clientInfo: ClientInformation, invitationToken: String?): ConnectLoginInitRsp {
        val req = ConnectLoginInitReq(
            clientInformation = clientInfo,
            flags = mapOf(),
            invitationToken = invitationToken
        )
        return corbadoConnectApi.connectLoginInit(req)
    }

    fun loginStart(
        identifier: String,
        source: ConnectLoginStartReq.Source = ConnectLoginStartReq.Source.textMinusField,
        loadedMs: Long = 0
    ): ConnectLoginStartRsp {
        val req = ConnectLoginStartReq(
            identifier = identifier,
            source = source,
            loadedMs = loadedMs,
        )
        return corbadoConnectApi.connectLoginStart(req)
    }

    fun loginFinish(
        authenticatorResponse: RPPlatformPublicKeyCredentialAssertion,
        isConditionalUI: Boolean
    ): ConnectLoginFinishRsp {
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
        val serializedAssertionResponse =
            json.encodeToString(AuthenticateResponse.serializer(), assertionResponse)

        val req = ConnectLoginFinishReq(
            isConditionalUI = isConditionalUI,
            assertionResponse = serializedAssertionResponse
        )

        return corbadoConnectApi.connectLoginFinish(req)
    }

    fun appendInit(clientInfo: ClientInformation, invitationToken: String?): ConnectAppendInitRsp {
        val req = ConnectAppendInitReq(
            clientInformation = clientInfo,
            flags = mapOf(),
            invitationToken = invitationToken
        )
        return corbadoConnectApi.connectAppendInit(req)
    }

    fun appendStart(
        connectToken: String,
        forcePasskeyAppend: Boolean,
        loadedMs: Long = 0
    ): AppendStartResult {
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

    fun appendFinish(authenticatorResponse: RPPlatformPublicKeyCredentialRegistration): ConnectAppendFinishRsp {
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
        val serializedAttestationResponse = json.encodeToString(
            RPPlatformPublicKeyCredentialRegistration.serializer(),
            attestationResponse
        )

        val req = ConnectAppendFinishReq(attestationResponse = serializedAttestationResponse)
        return corbadoConnectApi.connectAppendFinish(req)
    }

    fun manageInit(clientInfo: ClientInformation, invitationToken: String?): ConnectManageInitRsp {
        val req = ConnectManageInitReq(
            clientInformation = clientInfo,
            flags = mapOf(),
            invitationToken = invitationToken
        )
        return corbadoConnectApi.connectManageInit(req)
    }

    fun manageList(connectToken: String): ConnectManageListRsp {
        val req = ConnectManageListReq(connectToken = connectToken)
        val res = corbadoConnectApi.connectManageList(req)

        return res
    }

    fun manageDelete(connectToken: String, passkeyId: String): ConnectManageDeleteRsp {
        val req = ConnectManageDeleteReq(
            connectToken = connectToken,
            credentialID = passkeyId
        )
        return corbadoConnectApi.connectManageDelete(req)
    }

    fun recordLoginEvent(event: LoginPasskeyEvent, situation: LoginSituation? = null) {
        try {
            val baseMessage = situation?.let {
                "situation: ${it.ordinal} "
            } ?: ""

            val req = when (event) {
                is LoginPasskeyEvent.LoginExplicitAbort -> ConnectEventCreateReq(
                    eventType = PasskeyEventType.loginMinusExplicitMinusAbort,
                )

                is LoginPasskeyEvent.LoginError -> ConnectEventCreateReq(
                    eventType = PasskeyEventType.loginMinusError,
                    message = "$baseMessage${serializeException(event.exception)}",
                )

                is LoginPasskeyEvent.LoginErrorUnexpected -> ConnectEventCreateReq(
                    eventType = PasskeyEventType.loginMinusErrorMinusUnexpected,
                    message = "$baseMessage${serializeException(event.exception)}",
                )

                LoginPasskeyEvent.LoginOneTapSwitch -> ConnectEventCreateReq(
                    eventType = PasskeyEventType.loginMinusOneMinusTapMinusSwitch
                )

                LoginPasskeyEvent.LoginNoCredentials -> ConnectEventCreateReq(
                    eventType = PasskeyEventType.loginMinusNoMinusCredentials
                )

                LoginPasskeyEvent.LocalUnlock -> ConnectEventCreateReq(
                    eventType = PasskeyEventType.localMinusUnlock
                )
            }

            return corbadoConnectApi.connectEventCreate(req)
        } catch (_: Exception) {
            // recording must never fail the main flow
        }
    }

    fun recordAppendEvent(event: AppendPasskeyEvent, situation: AppendSituation? = null) {
        try {
            val baseMessage = situation?.let {
                "situation: ${it.ordinal} "
            } ?: ""

            val req = when (event) {
                is AppendPasskeyEvent.AppendExplicitAbort -> ConnectEventCreateReq(
                    eventType = PasskeyEventType.appendMinusExplicitMinusAbort,
                )

                is AppendPasskeyEvent.AppendCredentialExists -> ConnectEventCreateReq(
                    eventType = PasskeyEventType.appendMinusCredentialMinusExists,
                    message = "$baseMessage${serializeException(event.exception)}",
                )

                is AppendPasskeyEvent.AppendError -> ConnectEventCreateReq(
                    eventType = PasskeyEventType.appendMinusError,
                    message = "$baseMessage${serializeException(event.exception)}",
                )

                is AppendPasskeyEvent.AppendErrorUnexpected -> ConnectEventCreateReq(
                    eventType = PasskeyEventType.appendMinusErrorMinusUnexpected,
                    message = "$baseMessage${serializeException(event.exception)}",
                )

                AppendPasskeyEvent.AppendLearnMore -> ConnectEventCreateReq(
                    eventType = PasskeyEventType.appendMinusLearnMinusMore
                )
            }

            return corbadoConnectApi.connectEventCreate(req)
        } catch (_: Exception) {
            // recording must never fail the main flow
        }
    }

    fun recordManageEvent(event: ManagePasskeyEvent, situation: ManageSituation? = null) {
        try {
            val baseMessage = situation?.let {
                "situation: ${it.ordinal} "
            } ?: ""

            val req = when (event) {
                is ManagePasskeyEvent.ManageError -> ConnectEventCreateReq(
                    eventType = PasskeyEventType.manageMinusError,
                    message = "$baseMessage${serializeException(event.exception)}",
                )

                is ManagePasskeyEvent.ManageErrorUnexpected -> ConnectEventCreateReq(
                    eventType = PasskeyEventType.manageMinusErrorMinusUnexpected,
                    message = "$baseMessage${serializeException(event.exception)}",
                )

                ManagePasskeyEvent.ManageLearnMore -> ConnectEventCreateReq(
                    eventType = PasskeyEventType.manageMinusLearnMinusMore
                )

                is ManagePasskeyEvent.ManageCredentialExists -> ConnectEventCreateReq(
                    eventType = PasskeyEventType.manageMinusCredentialMinusExists,
                    message = "$baseMessage${serializeException(event.exception)}",
                )
            }

            return corbadoConnectApi.connectEventCreate(req)
        } catch (_: Exception) {
            // recording must never fail the main flow
        }
    }

    private fun serializeException(e: Exception): String {
        val message = e.message ?: "-"
        val cause = e.cause?.message ?: "-"

        return "message: $message cause: $cause"
    }

    fun setProcessId(processId: String?) {
        processIdInterceptor.processId = processId
    }

    fun setBlockedUrlPaths(urls: List<String>) {
        urlBlockingInterceptor.setBlockedUrlPaths(urls)
    }

    fun setTimeoutUrlPaths(pathsWithTimeouts: Map<String, Long>) {
        urlBlockingInterceptor.setTimeoutUrlPaths(pathsWithTimeouts)
    }
} 