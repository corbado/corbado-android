package io.corbado.connect

import com.corbado.api.models.ConnectAppendFinishReq
import com.corbado.api.models.ConnectAppendStartReq
import com.corbado.api.models.ConnectManageDeleteReq
import com.corbado.api.models.ConnectManageListReq
import com.corbado.api.models.Passkey
import io.corbado.simplecredentialmanager.model.AuthorizationError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Enums and classes for managing passkeys
sealed class ConnectManageStep {
    data class Allowed(val passkeys: List<Passkey>) : ConnectManageStep()
    data class NotAllowed(val passkeys: List<Passkey>) : ConnectManageStep()
    data class Error(val message: String) : ConnectManageStep()
}

sealed class ConnectManageStatus {
    data class Done(val passkeys: List<Passkey>) : ConnectManageStatus()
    data class Error(val message: String? = null) : ConnectManageStatus()
    object PasskeyOperationCancelled : ConnectManageStatus()
    object PasskeyOperationExcludeCredentialsMatch : ConnectManageStatus()
}

// Manage methods
suspend fun Corbado.isManageAppendAllowed(connectTokenProvider: suspend (ConnectTokenType) -> String): ConnectManageStep = withContext(Dispatchers.IO) {
    try {
        // We will implement buildClientInfo() later
        val initRes = client.manageInit(null, clientStateService.getInvitationToken())
        if (!initRes.manageAllowed) {
            val passkeys = getPasskeys(connectTokenProvider)
            return@withContext ConnectManageStep.NotAllowed(passkeys)
        }

        val passkeys = getPasskeys(connectTokenProvider)
        return@withContext ConnectManageStep.Allowed(passkeys)
    } catch (e: Exception) {
        return@withContext ConnectManageStep.Error(e.message ?: "An unknown error occurred")
    }
}

suspend fun Corbado.completePasskeyListAppend(connectTokenProvider: suspend (ConnectTokenType) -> String): ConnectManageStatus = withContext(Dispatchers.IO) {
    try {
        val connectToken = connectTokenProvider(ConnectTokenType.PasskeyAppend)
        val startRsp = client.appendStart(connectToken = connectToken, forcePasskeyAppend = true)

        val passkeyResponse = authController.create(startRsp.attestationOptions)
        client.appendFinish(passkeyResponse.data)

        val passkeys = getPasskeys(connectTokenProvider)
        return@withContext ConnectManageStatus.Done(passkeys)
    } catch (e: AuthorizationError) {
        return@withContext when(e.type) {
            AuthorizationError.Type.CANCELLED -> ConnectManageStatus.PasskeyOperationCancelled
            AuthorizationError.Type.EXCLUDE_CREDENTIALS_MATCH -> ConnectManageStatus.PasskeyOperationExcludeCredentialsMatch
            else -> ConnectManageStatus.Error(e.message)
        }
    } catch (e: Exception) {
        return@withContext ConnectManageStatus.Error(e.message ?: "An unknown error occurred")
    }
}

suspend fun Corbado.deletePasskey(connectTokenProvider: suspend (ConnectTokenType) -> String, passkeyId: String): ConnectManageStatus = withContext(Dispatchers.IO) {
    try {
        val connectToken = connectTokenProvider(ConnectTokenType.PasskeyDelete)
        client.manageDelete(connectToken = connectToken, passkeyId = passkeyId)
        clientStateService.clearLastLogin()

        val passkeys = getPasskeys(connectTokenProvider)
        return@withContext ConnectManageStatus.Done(passkeys)
    } catch (e: Exception) {
        return@withContext ConnectManageStatus.Error(e.message ?: "An unknown error occurred")
    }
}

@Throws(Exception::class)
private suspend fun Corbado.getPasskeys(connectTokenProvider: suspend (ConnectTokenType) -> String): List<Passkey> {
    val connectToken = connectTokenProvider(ConnectTokenType.PasskeyList)
    val res = client.manageList(connectToken)
    return res.passkeys
}
