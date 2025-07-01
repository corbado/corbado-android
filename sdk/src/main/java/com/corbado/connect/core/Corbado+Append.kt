package com.corbado.connect.core

import android.content.Context
import com.corbado.connect.core.AppendPasskeyEvent.AppendCredentialExists
import com.corbado.connect.core.AppendPasskeyEvent.AppendError
import com.corbado.connect.core.AppendPasskeyEvent.AppendErrorUnexpected
import com.corbado.simplecredentialmanager.AuthorizationError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ConnectAppendStep {
    data class AskUserForAppend(val autoAppend: Boolean, val type: AppendType) : ConnectAppendStep()
    data class Skip(val developerDetails: String) : ConnectAppendStep()
}

enum class AppendType {
    DefaultAppend
}

sealed class ConnectAppendStatus {
    data class Completed(val passkeyDetails: PasskeyDetails?) : ConnectAppendStatus()

    data object Cancelled : ConnectAppendStatus()
    data object ExcludeCredentialsMatch : ConnectAppendStatus()
    data class Error(val cause: Exception) :
        ConnectAppendStatus()

    data class PasskeyDetails(
        val aaguidName: String, val iconLight: String, val iconDark: String
    )
}

enum class AppendSituation {
    CboApiNotAvailablePreAuthenticator, CboApiNotAvailablePostAuthenticator, CtApiNotAvailablePreAuthenticator, ClientPasskeyOperationCancelled, ClientExcludeCredentialsMatch, DeniedByPartialRollout, DeniedByPasskeyIntel, ExplicitSkipByUser, ClientPasskeyOperationCancelledSilent,
}

// Append methods
suspend fun Corbado.isAppendAllowed(connectTokenProvider: suspend (ConnectTokenType) -> String): ConnectAppendStep =
    withContext(Dispatchers.IO) {
        val appendInitLoaded = System.currentTimeMillis()

        try {
            val initRes = try {
                client.appendInit(buildClientInfo(), clientStateService.getInvitationToken()?.data)
            } catch (e: Exception) {
                client.recordAppendEvent(
                    AppendErrorUnexpected(e), AppendSituation.CboApiNotAvailablePreAuthenticator
                )
                return@withContext ConnectAppendStep.Skip("init failed: ${e.toString()}")
            }

            val appendData = ConnectAppendInitData(
                appendAllowed = initRes.appendAllowed, expiresAt = initRes.expiresAt
            )

            val p = process?.let {
                it.appendData = appendData
                it
            } ?: ConnectProcess(
                id = initRes.processID,
                frontendApiUrl = initRes.frontendApiUrl,
                appendData = appendData,
            )
            process = p
            client.setProcessId(p.id)

            if (!appendData.appendAllowed) {
                return@withContext ConnectAppendStep.Skip("append not allowed by gradual rollout")
            }

            val connectToken = try {
                connectTokenProvider(ConnectTokenType.PasskeyAppend)
            } catch (e: Exception) {
                client.recordAppendEvent(
                    AppendErrorUnexpected(e), AppendSituation.CtApiNotAvailablePreAuthenticator
                )
                return@withContext ConnectAppendStep.Skip("connect token provider failed: ${e.toString()}")
            }

            val startRsp = try {
                client.appendStart(
                    connectToken = connectToken,
                    forcePasskeyAppend = false,
                    loadedMs = appendInitLoaded
                )
            } catch (e: Exception) {
                client.recordAppendEvent(
                    AppendErrorUnexpected(e), AppendSituation.CboApiNotAvailablePreAuthenticator
                )
                return@withContext ConnectAppendStep.Skip("start failed: ${e.toString()}")
            }
            val options = startRsp.options
                ?: return@withContext ConnectAppendStep.Skip("append not allowed by passkey intel")
            p.attestationOptions = authController.serializeCreatePublicKeyCredentialRequest(options)

            return@withContext ConnectAppendStep.AskUserForAppend(
                startRsp.autoAppend, AppendType.DefaultAppend
            )
        } catch (e: Exception) {
            return@withContext ConnectAppendStep.Skip("append failed: ${e.toString()}")
        }
    }

suspend fun Corbado.completeAppend(activityContext: Context): ConnectAppendStatus = withContext(Dispatchers.IO) {
    val processCopy = process
    if (processCopy == null) {
        val e = IllegalStateException("process is null")
        client.recordAppendEvent(
            AppendErrorUnexpected(e), AppendSituation.CboApiNotAvailablePreAuthenticator
        )
        return@withContext ConnectAppendStatus.Error(e)
    }

    val attestationOptions = processCopy.attestationOptions
    if (attestationOptions.isNullOrBlank()) {
        val e = IllegalStateException("attestation options are missing or empty")
        client.recordAppendEvent(
            AppendErrorUnexpected(e), AppendSituation.CboApiNotAvailablePreAuthenticator
        )
        return@withContext ConnectAppendStatus.Error(e)
    }

    val authenticatorResponse = try {
        authController.createPasskey(activityContext, attestationOptions)
    } catch (e: AuthorizationError) {
        return@withContext when (e) {
            AuthorizationError.Cancelled -> {
                client.recordAppendEvent(
                    AppendError(e), AppendSituation.ClientPasskeyOperationCancelled
                )

                ConnectAppendStatus.Cancelled
            }

            AuthorizationError.ExcludeCredentialsMatch -> {
                client.recordAppendEvent(
                    AppendCredentialExists(e), AppendSituation.ClientExcludeCredentialsMatch
                )

                ConnectAppendStatus.ExcludeCredentialsMatch
            }

            else -> {
                client.recordAppendEvent(
                    AppendErrorUnexpected(e), AppendSituation.CboApiNotAvailablePostAuthenticator
                )

                ConnectAppendStatus.Error(e)
            }
        }
    }

    try {
        val typedAuthenticatorResponse =
            authController.typeCreatePublicKeyCredentialResponse(authenticatorResponse)

        val finishRsp = client.appendFinish(typedAuthenticatorResponse)

        finishRsp.passkeyOperation.let {
            val lastLogin = LastLogin.from(it)
            clientStateService.setLastLogin(lastLogin)
        }

        val passkeyDetails = finishRsp.passkeyOperation.aaguidDetails?.let {
            ConnectAppendStatus.PasskeyDetails(
                aaguidName = it.name, iconLight = it.iconLight, iconDark = it.iconDark
            )
        }
        return@withContext ConnectAppendStatus.Completed(passkeyDetails)
    } catch (e: Exception) {
        client.recordAppendEvent(
            AppendErrorUnexpected(e), AppendSituation.CboApiNotAvailablePostAuthenticator
        )

        return@withContext ConnectAppendStatus.Error(e)
    }
}

suspend fun Corbado.appendRecordExplicitAbortEvent() = withContext(Dispatchers.IO) {
    client.recordAppendEvent(AppendPasskeyEvent.AppendExplicitAbort)
}

suspend fun Corbado.appendRecordLearnMoreEvent() = withContext(Dispatchers.IO) {
    client.recordAppendEvent(AppendPasskeyEvent.AppendLearnMore)
}