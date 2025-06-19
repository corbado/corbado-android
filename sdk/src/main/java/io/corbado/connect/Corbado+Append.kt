package io.corbado.connect

import io.corbado.simplecredentialmanager.AuthorizationError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Enums and classes for appending passkeys
sealed class ConnectAppendStep {
    data class AskUserForAppend(val autoAppend: Boolean, val type: AppendType) : ConnectAppendStep()
    data object Skip : ConnectAppendStep()
}

enum class AppendType {
    DefaultAppend
}

sealed class ConnectAppendStatus {
    data object Completed : ConnectAppendStatus()
    data object Cancelled : ConnectAppendStatus()
    data object ExcludeCredentialsMatch : ConnectAppendStatus()
    data object Error : ConnectAppendStatus()
}

// Append methods
suspend fun Corbado.isAppendAllowed(connectTokenProvider: suspend () -> String): ConnectAppendStep = withContext(Dispatchers.IO) {
    val appendInitLoaded = System.currentTimeMillis()

    try {
        // We will implement buildClientInfo() later
        val initRes = client.appendInit(buildClientInfo(), clientStateService.getInvitationToken()?.data)
        val appendData = ConnectAppendInitData(
            appendAllowed = initRes.appendAllowed,
            expiresAt = initRes.expiresAt
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
            return@withContext ConnectAppendStep.Skip
        }

        val connectToken = connectTokenProvider()
        val startRsp = client.appendStart(connectToken = connectToken, forcePasskeyAppend = false, loadedMs = appendInitLoaded)

        if (startRsp.attestationOptions.isBlank()) {
            return@withContext ConnectAppendStep.Skip
        }

        p.attestationOptions = startRsp.attestationOptions

        return@withContext ConnectAppendStep.AskUserForAppend(startRsp.autoAppend, AppendType.DefaultAppend)
    } catch (e: Exception) {
        return@withContext ConnectAppendStep.Skip
    }
}

suspend fun Corbado.completeAppend(): ConnectAppendStatus = withContext(Dispatchers.IO) {
    val p = process ?: return@withContext ConnectAppendStatus.Error
    val attestationOptions = p.attestationOptions ?: return@withContext ConnectAppendStatus.Error

    try {
        val authenticatorResponse = authController.createPasskey(attestationOptions)
        val typedAuthenticatorResponse = authController.typeCreatePublicKeyCredentialResponse(authenticatorResponse)
        val finishRsp = client.appendFinish(typedAuthenticatorResponse)

        finishRsp.passkeyOperation.let {
            val lastLogin = LastLogin.from(it)
            clientStateService.setLastLogin(lastLogin)
        }

        return@withContext ConnectAppendStatus.Completed
    } catch (e: AuthorizationError) {
        return@withContext when(e) {
            AuthorizationError.Cancelled -> ConnectAppendStatus.Cancelled
            AuthorizationError.ExcludeCredentialsMatch -> ConnectAppendStatus.ExcludeCredentialsMatch
            else -> ConnectAppendStatus.Error
        }
    } catch (e: Exception) {
        return@withContext ConnectAppendStatus.Error
    }
}
