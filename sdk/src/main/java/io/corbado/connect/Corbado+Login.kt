package io.corbado.connect

import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import com.corbado.api.models.FallbackOperationError
import io.corbado.simplecredentialmanager.AuthorizationError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Enums and classes for login
sealed class ConnectLoginStep {
    data class InitOneTap(val username: String) : ConnectLoginStep()
    data class InitTextField(val challenge: String?, val error: AuthError? = null) :
        ConnectLoginStep()

    data class InitFallback(val username: String? = null, val error: AuthError? = null) :
        ConnectLoginStep()

    data class Done(val session: String, val username: String) : ConnectLoginStep()
    object InitRetry : ConnectLoginStep()
}

sealed class ConnectLoginStatus {
    data class InitFallback(val username: String?, val error: AuthError? = null) :
        ConnectLoginStatus()

    data class Done(val session: String, val username: String) : ConnectLoginStatus()
    object InitRetry : ConnectLoginStatus()
    data class InitTextField(val challenge: String?, val error: AuthError? = null) :
        ConnectLoginStatus()
}


// Login methods
suspend fun Corbado.isLoginAllowed(): ConnectLoginStep = withContext(Dispatchers.IO) {
    try {
        val clientInfo = buildClientInfo()
        val invitationToken = clientStateService.getInvitationToken()
        val res = client.loginInit(clientInfo, invitationToken?.data)

        val loginData = ConnectLoginInitData(
            loginAllowed = res.loginAllowed,
            conditionalUIChallenge = res.conditionalUIChallenge,
            expiresAt = res.expiresAt,
        )

        val p = process?.takeIf { it.id == res.token }?.apply {
            this.loginData = loginData
        } ?: ConnectProcess(
            id = res.token,
            frontendApiUrl = res.frontendApiUrl,
            loginData = loginData,
        )
        process = p
        client.setProcessId(p.id)

        getConnectLoginStepLoginInit(loginData)
    } catch (e: Exception) {
        return@withContext ConnectLoginStep.InitFallback(
            error = AuthError(
                "generic_error",
                e.message ?: "An unknown error occurred"
            )
        )
    }
}

suspend fun Corbado.clearOneTap() {
    clientStateService.clearLastLogin()
}

suspend fun Corbado.loginWithOneTap(): ConnectLoginStatus = withContext(Dispatchers.IO) {
    val lastLogin = clientStateService.getLastLogin()?.data
        ?: return@withContext ConnectLoginStatus.InitFallback(
            null,
            AuthError("missing_last_login", "One-tap login requested but no last login found.")
        )

    return@withContext loginWithTextField(lastLogin.identifierValue)
}

suspend fun Corbado.loginWithTextField(identifier: String): ConnectLoginStatus =
    withContext(Dispatchers.IO) {
        var authenticatorInteraction = false

        try {
            val p = process ?: return@withContext ConnectLoginStatus.InitFallback(
                identifier,
                AuthError("invalid_state", "Process not initialized. Call isLoginAllowed() first.")
            )
            client.setProcessId(p.id)

            val loadedMs = loginInitCompleted ?: 0
            val startRsp = client.loginStart(identifier = identifier, loadedMs = loadedMs)

            startRsp.fallbackOperationError.let { err ->
                handleFallbackOperationError(err)?.let { return@withContext it }
            }

            val authenticatorRequest =
                GetPublicKeyCredentialOption(requestJson = startRsp.assertionOptions)
            val response = authController.authorize(listOf(authenticatorRequest))
            authenticatorInteraction = true
            val authenticatorResponse = when (val credential =
                response.credential) {
                is PublicKeyCredential -> authController.typeGetCredentialResponse(credential)
                else -> throw IllegalArgumentException(
                    "Credential must be of type PasskeyAssertionCredential. " +
                            "Actual type: ${credential.javaClass.simpleName}"
                )
            }

            val finishRsp = client.loginFinish(
                authenticatorResponse = authenticatorResponse,
                isConditionalUI = false
            )

            finishRsp.fallbackOperationError?.let {
                handleFallbackOperationError(it)?.let { return@withContext it }
            }

            finishRsp.passkeyOperation?.let {
                val lastLogin = LastLogin.from(it)
                clientStateService.setLastLogin(lastLogin)

                return@withContext ConnectLoginStatus.Done(
                    finishRsp.signedPasskeyData,
                    it.identifierValue
                )
            }

            return@withContext ConnectLoginStatus.InitFallback(
                identifier,
                AuthError("unknown_error", "Unknown error during login finish.")
            )
        } catch (e: AuthorizationError) {
            return@withContext when (e) {
                AuthorizationError.Cancelled -> ConnectLoginStatus.InitRetry
                AuthorizationError.NoCredentialsAvailable -> ConnectLoginStatus.InitFallback(
                    identifier
                )

                else -> ConnectLoginStatus.InitFallback(
                    identifier,
                    defaultAuthError
                )
            }
        } catch (e: Exception) {
            if (authenticatorInteraction) {
                return@withContext ConnectLoginStatus.InitFallback(identifier, defaultAuthError)
            } else {
                return@withContext ConnectLoginStatus.InitFallback(identifier)
            }
        }
    }

suspend fun Corbado.loginWithoutIdentifier(
    cuiChallenge: String,
    onStart: suspend () -> Unit = {}
): ConnectLoginStatus = withContext(Dispatchers.IO) {
    var authenticatorInteraction = false
    try {
        val authenticatorRequest =
            GetPublicKeyCredentialOption(requestJson = cuiChallenge)
        val response = authController.authorize(listOf(authenticatorRequest), true)
        authenticatorInteraction = true
        onStart()
        val authenticatorResponse = when (val credential = response.credential) {
            is PublicKeyCredential -> authController.typeGetCredentialResponse(credential)
            else -> throw IllegalArgumentException(
                "Credential must be of type PasskeyAssertionCredential. " +
                        "Actual type: ${credential.javaClass.simpleName}"
            )
        }

        val finishRsp = client.loginFinish(
            authenticatorResponse = authenticatorResponse,
            isConditionalUI = true
        )

        finishRsp.fallbackOperationError?.let {
            handleFallbackOperationError(it)?.let { return@withContext it }
        }

        finishRsp.passkeyOperation?.let {
            val lastLogin = LastLogin.from(it)
            clientStateService.setLastLogin(lastLogin)
            return@withContext ConnectLoginStatus.Done(
                finishRsp.signedPasskeyData,
                it.identifierValue
            )
        }

        return@withContext ConnectLoginStatus.InitTextField(
            null,
            AuthError("unknown_error", "Unknown error during login finish.")
        )
    } catch (e: AuthorizationError) {
        return@withContext when (e) {
            AuthorizationError.Cancelled -> ConnectLoginStatus.InitTextField(
                cuiChallenge,
                null
            )

            AuthorizationError.NoCredentialsAvailable -> ConnectLoginStatus.InitTextField(
                null,
                null
            )

            else -> ConnectLoginStatus.InitFallback("")
        }
    } catch (e: Exception) {
        if (authenticatorInteraction) {
            return@withContext ConnectLoginStatus.InitFallback("", defaultAuthError)
        } else {
            return@withContext ConnectLoginStatus.InitFallback("")
        }
    }
}

private fun handleFallbackOperationError(error: FallbackOperationError): ConnectLoginStatus? {
    if (error.initFallback) {
        val authError = error.error?.let { AuthError(it.code, it.message) }
        return ConnectLoginStatus.InitFallback(error.identifier, authError)
    }

    error.error?.let { nonNullError ->
        val authError = AuthError(nonNullError.code, nonNullError.message)
        return ConnectLoginStatus.InitTextField(null, authError)
    }

    return null
}

private suspend fun Corbado.getConnectLoginStepLoginInit(loginData: ConnectLoginInitData): ConnectLoginStep {
    loginInitCompleted = System.currentTimeMillis()

    if (!loginData.loginAllowed) {
        return ConnectLoginStep.InitFallback()
    }

    if (useOneTap) {
        clientStateService.getLastLogin()?.data?.let {
            return ConnectLoginStep.InitOneTap(it.identifierValue)
        }
    }

    return ConnectLoginStep.InitTextField(loginData.conditionalUIChallenge)
}
