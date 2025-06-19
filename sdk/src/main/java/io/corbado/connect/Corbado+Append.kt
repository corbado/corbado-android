package io.corbado.connect

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Enums and classes for appending passkeys
sealed class ConnectAppendStep {
    data class AskUserForAppend(val autoAppend: Boolean, val type: AppendType) : ConnectAppendStep()
    object Skip : ConnectAppendStep()
}

enum class AppendType {
    DefaultAppend
}

sealed class ConnectAppendStatus {
    object Completed : ConnectAppendStatus()
    object Cancelled : ConnectAppendStatus()
    object ExcludeCredentialsMatch : ConnectAppendStatus()
    object Error : ConnectAppendStatus()
}

// Append methods
suspend fun Corbado.isAppendAllowed(connectTokenProvider: suspend () -> String): ConnectAppendStep = withContext(Dispatchers.IO) {
    try {
        // We will implement buildClientInfo() later
        val initRes = client.appendInit(null, clientStateService.getInvitationToken())
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
        val startRsp = client.appendStart(connectToken = connectToken, forcePasskeyAppend = false)

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
        val passkeyResponse = authController.create(attestationOptions)
        val finishRsp = client.appendFinish(passkeyResponse.data)

        finishRsp.passkeyOperation?.let {
            clientStateService.setLastLogin(it.identifierValue)
        }

        return@withContext ConnectAppendStatus.Completed
    } catch (e: AuthorizationError) {
        return@withContext when(e.type) {
            AuthorizationError.Type.CANCELLED -> ConnectAppendStatus.Cancelled
            AuthorizationError.Type.EXCLUDE_CREDENTIALS_MATCH -> ConnectAppendStatus.ExcludeCredentialsMatch
            else -> ConnectAppendStatus.Error
        }
    } catch (e: Exception) {
        return@withContext ConnectAppendStatus.Error
    }
}
