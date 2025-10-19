package com.corbado.connect.core

import android.content.Context
import com.corbado.connect.core.AppendPasskeyEvent.AppendCredentialExists
import com.corbado.connect.core.AppendPasskeyEvent.AppendError
import com.corbado.connect.core.AppendPasskeyEvent.AppendErrorUnexpected
import com.corbado.simplecredentialmanager.AuthorizationError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.Duration

sealed class ConnectAppendStep {
    data class AskUserForAppend(
        val autoAppend: Boolean, 
        val type: AppendType, 
        val conditionalAppend: Boolean,
        val customData: Map<String, String>? = null
    ) : ConnectAppendStep()
    data class Skip(val developerDetails: String) : ConnectAppendStep()
}

enum class AppendType {
    DefaultAppend
}

enum class AppendCompletionType {
    Auto, Conditional, Manual, ManualRetry
}

data class AppendSituationType(val rawValue: String, val localDebounce: Duration = Duration.ofDays(1)) {
        companion object {
            val PostLogin = AppendSituationType("post-login", Duration.ZERO)
            val PasskeyList = AppendSituationType("passkey-list", Duration.ZERO)
        }
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
suspend fun Corbado.isAppendAllowed(
    connectTokenProvider: suspend (ConnectTokenType) -> String,
    situation: AppendSituationType = AppendSituationType.PostLogin
): ConnectAppendStep =
    withContext(Dispatchers.IO) {
        val lastAppendAt = clientStateService.getSituationDebounceMap()?.get(situation.rawValue)
        val now = Instant.now()

        clientStateService.setSituationDebounceMapEntry(situation.rawValue, now)
        if (lastAppendAt != null) {
            val elapsed = Duration.between(lastAppendAt, now)
            if (elapsed < situation.localDebounce) {
                // we could track this

                return@withContext ConnectAppendStep.Skip("append skipped due to local debounce")
            }
        }

        try {
            val initRes = try {
                client.appendInit(buildClientInfo(), clientStateService.getInvitationToken()?.data, situation.rawValue)
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

            initRes.newClientEnvHandle?.let {
                clientStateService.setClientEnvHandle(it)
            }

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
                    situation = situation.rawValue
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
            p.attestationExpiry = startRsp.expiresAt

            return@withContext ConnectAppendStep.AskUserForAppend(
                startRsp.autoAppend, AppendType.DefaultAppend, startRsp.conditionalAppend, startRsp.customData
            )
        } catch (e: Exception) {
            return@withContext ConnectAppendStep.Skip("append failed: ${e.toString()}")
        }
    }

suspend fun Corbado.completeAppend(
    activityContext: Context, 
    completionType: AppendCompletionType = AppendCompletionType.Manual,
    customData: Map<String, String>? = null,
    awaitCompletion: Boolean = true
): ConnectAppendStatus = withContext(Dispatchers.IO) {
    val processCopy = process
    if (processCopy == null) {
        val e = IllegalStateException("process is null")
        client.recordAppendEvent(
            AppendErrorUnexpected(e), AppendSituation.CboApiNotAvailablePreAuthenticator
        )
        return@withContext ConnectAppendStatus.Error(e)
    }

    if (processCopy.attestationExpiry?.isBefore(Instant.now()) == true) {
        val e = IllegalStateException("options are expired")
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

    //val isConditional = completionType == AppendCompletionType.Conditional
    val authenticatorResponse = try {
        authController.createPasskey(activityContext, attestationOptions, false)
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

    val finishBlock: suspend () -> ConnectAppendStatus = {
        try {
            val typedAuthenticatorResponse =
                authController.typeCreatePublicKeyCredentialResponse(authenticatorResponse)

            val finishRsp = client.appendFinish(typedAuthenticatorResponse, completionType, customData)

            finishRsp.passkeyOperation.let {
                val lastLogin = LastLogin.from(it)
                clientStateService.setLastLogin(lastLogin)
            }

            val passkeyDetails = finishRsp.passkeyOperation.aaguidDetails?.let {
                ConnectAppendStatus.PasskeyDetails(
                    aaguidName = it.name, iconLight = it.iconLight, iconDark = it.iconDark
                )
            }
            ConnectAppendStatus.Completed(passkeyDetails)
        } catch (e: Exception) {
            client.recordAppendEvent(
                AppendErrorUnexpected(e), AppendSituation.CboApiNotAvailablePostAuthenticator
            )
            ConnectAppendStatus.Error(e)
        }
    }

    return@withContext if (awaitCompletion) {
        finishBlock()
    } else {
        CoroutineScope(Dispatchers.IO).launch {
            finishBlock()
        }
        ConnectAppendStatus.Completed(null)
    }
}


suspend fun Corbado.appendRecordExplicitAbortEvent() = withContext(Dispatchers.IO) {
    client.recordAppendEvent(AppendPasskeyEvent.AppendExplicitAbort)
}

suspend fun Corbado.appendRecordLearnMoreEvent() = withContext(Dispatchers.IO) {
    client.recordAppendEvent(AppendPasskeyEvent.AppendLearnMore)
}