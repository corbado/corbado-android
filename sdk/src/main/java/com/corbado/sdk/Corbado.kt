package com.corbado.sdk

import com.corbado.api.models.Passkey

// Placeholder for error handling
data class AuthError(val code: String, val message: String)

// Enums and classes for login
sealed class ConnectLoginStep {
    data class InitOneTap(val username: String) : ConnectLoginStep()
    data class InitTextField(val challenge: String?) : ConnectLoginStep()
    data class InitFallback(val username: String? = null, val error: AuthError? = null) : ConnectLoginStep()
    data class Done(val session: String, val username: String) : ConnectLoginStep()
    object InitRetry : ConnectLoginStep()
}

sealed class ConnectLoginStatus {
    data class InitFallback(val username: String?, val error: AuthError? = null) : ConnectLoginStatus()
    data class Done(val session: String, val username: String) : ConnectLoginStatus()
    object InitRetry : ConnectLoginStatus()
    data class InitTextField(val challenge: String?, val error: AuthError? = null) : ConnectLoginStatus()
}

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


class Corbado(
    private val projectId: String,
    private val frontendApiUrlSuffix: String? = null,
    private val useOneTap: Boolean = true
) {
    // Control methods
    suspend fun clearLocalState() {
        // Implementation will be added in the next step
    }

    suspend fun setInvitationToken(token: String) {
        // Implementation will be added in the next step
    }

    fun clearProcess() {
        // Implementation will be added in the next step
    }

    // Login methods
    suspend fun isLoginAllowed(): ConnectLoginStep {
        // Placeholder implementation
        return ConnectLoginStep.InitFallback()
    }

    suspend fun clearOneTap() {
        // Implementation will be added in the next step
    }

    suspend fun loginWithOneTap(): ConnectLoginStatus {
        // Placeholder implementation
        return ConnectLoginStatus.InitFallback(null)
    }

    suspend fun loginWithTextField(identifier: String): ConnectLoginStatus {
        // Placeholder implementation
        return ConnectLoginStatus.InitFallback(identifier)
    }

    suspend fun loginWithoutIdentifier(
        cuiChallenge: String,
        conditionalUI: Boolean = true,
        preferImmediatelyAvailableCredentials: Boolean = true,
        onStart: suspend () -> Unit = {}
    ): ConnectLoginStatus {
        // Placeholder implementation
        return ConnectLoginStatus.InitFallback(null)
    }

    // Append methods
    suspend fun isAppendAllowed(connectTokenProvider: suspend () -> String): ConnectAppendStep {
        // Placeholder implementation
        return ConnectAppendStep.Skip
    }

    suspend fun completeAppend(): ConnectAppendStatus {
        // Placeholder implementation
        return ConnectAppendStatus.Error
    }

    // Manage methods
    suspend fun isManageAppendAllowed(connectTokenProvider: suspend (String) -> String): ConnectManageStep {
        // Placeholder implementation
        return ConnectManageStep.Error("Not implemented")
    }

    suspend fun completePasskeyListAppend(connectTokenProvider: suspend (String) -> String): ConnectManageStatus {
        // Placeholder implementation
        return ConnectManageStatus.Error("Not implemented")
    }

    suspend fun deletePasskey(connectTokenProvider: suspend (String) -> String, passkeyId: String): ConnectManageStatus {
        // Placeholder implementation
        return ConnectManageStatus.Error("Not implemented")
    }
} 