package com.corbado.connect.core

import android.content.Context
import com.corbado.connect.core.ManagePasskeyEvent.ManageCredentialExists
import com.corbado.connect.core.ManagePasskeyEvent.ManageError
import com.corbado.connect.core.ManagePasskeyEvent.ManageErrorUnexpected
import com.corbado.connect.core.ManagePasskeyEvent.ManageLearnMore
import com.corbado.simplecredentialmanager.AuthorizationError
import com.corbado.simplecredentialmanager.PublicKeyCredentialSignalAllAcceptedCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

// Enums and classes for managing passkeys
sealed class ConnectManageStep {
    data class Allowed(val passkeys: List<Passkey>) : ConnectManageStep()
    data class NotAllowed(val passkeys: List<Passkey>) : ConnectManageStep()
    data class Error(val message: String) : ConnectManageStep()
}

sealed class ConnectManageStatus {
    data class Done(val passkeys: List<Passkey>) : ConnectManageStatus()
    data class Error(val message: String? = null) : ConnectManageStatus()
    data object PasskeyOperationCancelled : ConnectManageStatus()
    data object PasskeyOperationExcludeCredentialsMatch : ConnectManageStatus()
}

enum class ManageSituation {
    CboApiNotAvailableDuringInitialLoad,
    CtApiNotAvailableDuringInitialLoad,
    CboApiNotAvailableDuringDelete,
    CtApiNotAvailablePreDelete,
    CtApiNotAvailablePreAuthenticator,
    CboApiPasskeysNotSupported,
    CboApiNotAvailablePreAuthenticator,
    CboApiNotAvailablePostAuthenticator,
    ClientPasskeyOperationCancelled,
    ClientExcludeCredentialsMatch,
    CboApiPasskeysNotSupportedLight,
    Unknown,
    ClientSignalAllAcceptedCredentialsError
}

enum class ManageListMode {
    Default,
    PostDelete,
    PostAppend
}

data class PasskeyListResponse(
    val passkeys: List<Passkey>,
    val rpId: String,
    val userId: String,
    val signalAllAcceptedCredentials: Boolean
)

// Manage methods
suspend fun Corbado.isManageAppendAllowed(connectTokenProvider: suspend (ConnectTokenType) -> String): ConnectManageStep =
    withContext(Dispatchers.IO) {
        try {
            val allowed = manageAllowedStep1()
            val passkeys = getPasskeys(connectTokenProvider, ManageListMode.Default)
            if (!allowed) {
                return@withContext ConnectManageStep.NotAllowed(passkeys)
            }

            return@withContext ConnectManageStep.Allowed(passkeys)
        } catch (e: ConnectTokenError) {
            client.recordManageEvent(
                ManageErrorUnexpected(e),
                ManageSituation.CtApiNotAvailableDuringInitialLoad
            )

            return@withContext ConnectManageStep.Error(e.message)
        } catch (e: Exception) {
            client.recordManageEvent(
                ManageErrorUnexpected(e),
                ManageSituation.CboApiNotAvailableDuringInitialLoad
            )

            return@withContext ConnectManageStep.Error(e.toString())
        }
    }

suspend fun Corbado.completePasskeyListAppend(
    activityContext: Context,
    connectTokenProvider: suspend (ConnectTokenType) -> String
): ConnectManageStatus =
    withContext(Dispatchers.IO) {
        try {
            ensureValidManageProcess()

            val connectToken = connectTokenProvider(ConnectTokenType.PasskeyAppend)
            val startRsp = try {
                client.appendStart(
                    connectToken = connectToken,
                    forcePasskeyAppend = true,
                    situation = AppendSituationType.PasskeyList.rawValue
                )
            } catch (e: Exception) {
                client.recordManageEvent(
                    ManageErrorUnexpected(e),
                    ManageSituation.CboApiNotAvailablePreAuthenticator
                )
                return@withContext ConnectManageStatus.Error(
                    e.message ?: "An unknown error occurred during append start"
                )
            }

            val rawOptions = startRsp.options
                ?: return@withContext ConnectManageStatus.Error("Passkeys not supported on this device")
            val attestationOptions =
                authController.serializeCreatePublicKeyCredentialRequest(rawOptions)
            val authenticatorResponse = try {
                authController.createPasskey(activityContext, attestationOptions, false)
            } catch (e: AuthorizationError) {
                return@withContext when (e) {
                    AuthorizationError.Cancelled -> {
                        client.recordManageEvent(
                            ManageError(e),
                            ManageSituation.ClientPasskeyOperationCancelled
                        )

                        ConnectManageStatus.PasskeyOperationCancelled
                    }

                    AuthorizationError.ExcludeCredentialsMatch -> {
                        client.recordManageEvent(
                            ManageCredentialExists(e),
                            ManageSituation.ClientExcludeCredentialsMatch
                        )

                        ConnectManageStatus.PasskeyOperationExcludeCredentialsMatch
                    }

                    else -> {
                        client.recordManageEvent(
                            ManageErrorUnexpected(e),
                            ManageSituation.CboApiNotAvailablePostAuthenticator
                        )

                        ConnectManageStatus.Error(e.message)
                    }
                }
            }

            val typedAuthenticatorResponse =
                authController.typeCreatePublicKeyCredentialResponse(authenticatorResponse)
            val finishRsp = try {
                client.appendFinish(typedAuthenticatorResponse, AppendCompletionType.Manual)
            } catch (e: Exception) {
                client.recordManageEvent(
                    ManageErrorUnexpected(e),
                    ManageSituation.CboApiNotAvailablePostAuthenticator
                )

                return@withContext ConnectManageStatus.Error(e.toString())
            }

            finishRsp.passkeyOperation.let {
                val lastLogin = LastLogin.from(it)
                clientStateService.setLastLogin(lastLogin)
            }

            val passkeys = getPasskeys(connectTokenProvider, ManageListMode.PostAppend)
            return@withContext ConnectManageStatus.Done(passkeys)
        } catch (e: Exception) {
            client.recordManageEvent(
                ManageErrorUnexpected(e),
                ManageSituation.CtApiNotAvailablePreAuthenticator
            )
            return@withContext ConnectManageStatus.Error(e.message)

        } catch (e: Exception) {
            client.recordManageEvent(
                ManageErrorUnexpected(e),
                ManageSituation.Unknown
            )
            return@withContext ConnectManageStatus.Error(e.message ?: "An unknown error occurred")
        }
    }

suspend fun Corbado.deletePasskey(
    connectTokenProvider: suspend (ConnectTokenType) -> String,
    passkeyId: String
): ConnectManageStatus = withContext(Dispatchers.IO) {
    try {
        ensureValidManageProcess()

        val connectToken = connectTokenProvider(ConnectTokenType.PasskeyDelete)
        client.manageDelete(connectToken = connectToken, passkeyId = passkeyId)
        clientStateService.clearLastLogin()

        val passkeys = getPasskeys(connectTokenProvider, ManageListMode.PostDelete)
        return@withContext ConnectManageStatus.Done(passkeys)
    } catch (e: ConnectTokenError) {
        client.recordManageEvent(
            ManageErrorUnexpected(e),
            ManageSituation.CtApiNotAvailablePreDelete
        )
        return@withContext ConnectManageStatus.Error(e.message)
    } catch (e: Exception) {
        client.recordManageEvent(
            ManageErrorUnexpected(e),
            ManageSituation.CboApiNotAvailableDuringDelete
        )

        return@withContext ConnectManageStatus.Error(e.toString())
    }
}

suspend fun Corbado.manageRecordLearnMoreEvent() = withContext(Dispatchers.IO) {
    client.recordManageEvent(ManageLearnMore)
}

@Throws(Exception::class)
private suspend fun Corbado.getPasskeys(
    connectTokenProvider: suspend (ConnectTokenType) -> String,
    mode: ManageListMode
): List<Passkey> {
    val connectToken = connectTokenProvider(ConnectTokenType.PasskeyList)
    val res = client.manageList(connectToken, mode)
    val passkeys = res.passkeys.map { passkey ->
        Passkey(
            id = passkey.id,
            tags = passkey.tags,
            sourceOS = passkey.sourceOS,
            sourceBrowser = passkey.sourceBrowser,
            lastUsedMs = passkey.lastUsedMs,
            createdMs = passkey.createdMs,
            aaguidDetails = AaguidDetails(
                passkey.aaguidDetails.name,
                passkey.aaguidDetails.iconLight,
                passkey.aaguidDetails.iconDark
            )
        )
    }

    if (res.signalAllAcceptedCredentials) {
        val request = PublicKeyCredentialSignalAllAcceptedCredentials(
            res.rpID,
            res.userID,
            res.passkeys.map { it.credentialID }
        )
        val requestJson = authController.serializeSignalAllAcceptedCredentialsRequest(request)

        try {
            authController.signalAllAcceptedCredentials(requestJson)
        } catch (e: AuthorizationError) {
            client.recordManageEvent(
                ManageErrorUnexpected(e),
                ManageSituation.ClientSignalAllAcceptedCredentialsError
            )
        }
    }

    return passkeys
}

// safety margin (seconds) when checking the expiry of manage-init data
private const val manageInitExpiryMarginSec = 5L

/**
 * Re-runs manage-init if the manage-init data of the current process has expired (the backend removes processes
 * after their lifetime; a stale process ID would be rejected by the follow-up calls).
 */
private suspend fun Corbado.ensureValidManageProcess() {
    val expiresAt = process?.manageData?.expiresAt
    if (process != null && expiresAt != null && expiresAt > Instant.now().epochSecond + manageInitExpiryMarginSec) {
        return
    }

    manageAllowedStep1()
}

private suspend fun Corbado.manageAllowedStep1(): Boolean = withContext(Dispatchers.IO) {
    val initRes =
        client.manageInit(buildClientInfo(), clientStateService.getInvitationToken()?.data)
    val manageData = ConnectManageInitData(
        manageAllowed = initRes.manageAllowed,
        expiresAt = initRes.expiresAt
    )

    val p = process?.takeIf { it.id == initRes.processID }?.apply {
        this.manageData = manageData
    } ?: ConnectProcess(
        id = initRes.processID,
        frontendApiUrl = initRes.frontendApiUrl,
        manageData = manageData,
    )
    process = p

    client.setProcessId(p.id)
    initRes.newClientEnvHandle?.let {
        clientStateService.setClientEnvHandle(it)
    }

    initRes.manageAllowed
}