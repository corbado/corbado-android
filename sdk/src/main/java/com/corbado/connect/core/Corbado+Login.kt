package com.corbado.connect.core

import android.content.Context
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import com.corbado.connect.api.models.FallbackOperationError
import com.corbado.simplecredentialmanager.AuthorizationError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class LoginWithoutIdentifierError(val message: String) {
    object CorbadoAPIError : LoginWithoutIdentifierError(defaultErrorMessage)
    object UnHandledError : LoginWithoutIdentifierError(defaultErrorMessage)
    data class CustomError(val code: String, val messageFromBackend: String) :
        LoginWithoutIdentifierError(messageFromBackend)

    data class PasskeyDeletedOnServer(val messageFromBackend: String) :
        LoginWithoutIdentifierError(messageFromBackend)

    data class ProjectIDMismatch(
        val messageFromBackend: String,
            val wrongProjectName: String? = null,
        val correctProjectName: String? = null
    ) : LoginWithoutIdentifierError(messageFromBackend)
}

sealed class LoginWithIdentifierError(val message: String) {
    object CorbadoAPIError : LoginWithIdentifierError(defaultErrorMessage)
    object UnHandledError : LoginWithIdentifierError(defaultErrorMessage)
    data class CustomError(val code: String, val messageFromBackend: String) :
        LoginWithIdentifierError(messageFromBackend)

    object InvalidStateError : LoginWithIdentifierError(defaultErrorMessage)

    object UserNotFound : LoginWithIdentifierError("No account matches that email.")
}

// Enums and classes for login
sealed class ConnectLoginStep {
    data class InitOneTap(val username: String) : ConnectLoginStep()
    data class InitTextField(val challenge: String?, val cause: Exception? = null) :
        ConnectLoginStep()

    data class InitFallback(val username: String? = null, val cause: Exception? = null) :
        ConnectLoginStep()
}

sealed class ConnectLoginWithIdentifierStatus {
    data class Done(val session: String, val username: String) : ConnectLoginWithIdentifierStatus()

    data class Error(
        val error: LoginWithIdentifierError,
        val triggerFallback: Boolean,
        val developerDetails: String,
        val username: String? = null,
    ) : ConnectLoginWithIdentifierStatus()


    data class InitSilentFallback(val username: String? = null, val developerDetails: String) :
        ConnectLoginWithIdentifierStatus()

    data class InitRetry(val developerDetails: String) : ConnectLoginWithIdentifierStatus()
}

sealed class ConnectLoginWithoutIdentifierStatus {
    data class Done(val session: String, val username: String) :
        ConnectLoginWithoutIdentifierStatus()

    data class Error(
        val error: LoginWithoutIdentifierError,
        val triggerFallback: Boolean,
        val developerDetails: String,
        val username: String? = null,
    ) : ConnectLoginWithoutIdentifierStatus()

    data class InitSilentFallback(val username: String? = null, val developerDetails: String) :
        ConnectLoginWithoutIdentifierStatus()

    data class Ignore(val developerDetails: String) : ConnectLoginWithoutIdentifierStatus()
}

enum class LoginSituation {
    CboApiNotAvailablePreConditionalAuthenticator, ClientPasskeyConditionalOperationCancelled, ClientPasskeyOperationCancelledTooManyTimes, PasskeyNotAvailablePostConditionalAuthenticator, CboApiNotAvailablePostConditionalAuthenticator, CboApiNotAvailablePreAuthenticator, ClientPasskeyOperationCancelled, CboApiNotAvailablePostAuthenticator, CtApiNotAvailablePostAuthenticator, ExplicitFallbackByUser, PreAuthenticatorUserNotFound, DeniedByPartialRollout, PreAuthenticatorCustomError, PreAuthenticatorExistingPasskeysNotAvailable, PreAuthenticatorNoPasskeyAvailable, CboApiFallbackOperationError,
}

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
        client.recordLoginEvent(
            LoginPasskeyEvent.LoginErrorUnexpected(e),
            LoginSituation.CboApiNotAvailablePreAuthenticator
        )

        return@withContext ConnectLoginStep.InitFallback(cause = e)
    }
}

fun Corbado.clearOneTap() {
    client.recordLoginEvent(LoginPasskeyEvent.LoginOneTapSwitch)

    clientStateService.clearLastLogin()
}

suspend fun Corbado.loginWithOneTap(activityContext: Context): ConnectLoginWithIdentifierStatus =
    withContext(Dispatchers.IO) {
        val lastLogin = clientStateService.getLastLogin()?.data
            ?: return@withContext ConnectLoginWithIdentifierStatus.Error(
                error = LoginWithIdentifierError.InvalidStateError,
                triggerFallback = true,
                developerDetails = "One-tap login requested but no last login found.",
            )

        return@withContext loginWithTextField(activityContext, lastLogin.identifierValue)
    }

suspend fun Corbado.loginWithTextField(
    activityContext: Context,
    identifier: String
): ConnectLoginWithIdentifierStatus =
    withContext(Dispatchers.IO) {
        val p = process
        if (p == null) {
            client.recordLoginEvent(
                LoginPasskeyEvent.LoginErrorUnexpected(
                    IllegalStateException("Process not initialized")
                ), LoginSituation.CboApiNotAvailablePreAuthenticator
            )

            return@withContext ConnectLoginWithIdentifierStatus.Error(
                LoginWithIdentifierError.InvalidStateError,
                triggerFallback = true,
                "Process not initialized. Call isLoginAllowed() first.",
                identifier,
            )
        }

        client.setProcessId(p.id)

        val (assertionOptions, preferImmediatelyAvailable) = try {
            val startRsp = client.loginStart(identifier = identifier)

            startRsp.fallbackOperationError.let { err ->
                handleFallbackOperationErrorForLoginWithIdentifier(err)?.let { return@withContext it }
            }

            Pair(
                authController.serializeGetCredentialRequest(
                    CorbadoClient.RelyingPartyServerDeserializer.deserializeAssertion(
                        startRsp.assertionOptions
                    )
                ), startRsp.preferImmediatelyAvailable
            )
        } catch (e: Exception) {
            client.recordLoginEvent(
                LoginPasskeyEvent.LoginErrorUnexpected(e),
                LoginSituation.CboApiNotAvailablePreAuthenticator
            )

            return@withContext ConnectLoginWithIdentifierStatus.InitSilentFallback(
                username = identifier,
                developerDetails = "An error occurred during login start: ${e.message}"
            )
        }

        val authenticatorResponse = try {
            val authenticatorRequest = GetPublicKeyCredentialOption(requestJson = assertionOptions)

            val response = authController.authorize(
                activityContext, listOf(authenticatorRequest), preferImmediatelyAvailable == true
            )

            when (val credential = response.credential) {
                is PublicKeyCredential -> authController.typeGetCredentialResponse(credential)
                else -> throw IllegalArgumentException(
                    "Credential must be of type PasskeyAssertionCredential. " + "Actual type: ${credential.javaClass.simpleName}"
                )
            }
        } catch (e: AuthorizationError) {
            return@withContext when (e) {
                AuthorizationError.Cancelled -> {
                    client.recordLoginEvent(
                        LoginPasskeyEvent.LoginError(e),
                        LoginSituation.ClientPasskeyOperationCancelled
                    )

                    ConnectLoginWithIdentifierStatus.InitRetry(
                        developerDetails = "User cancelled the operation. Consider allowing a retry."
                    )
                }

                AuthorizationError.NoCredentialsAvailable -> {
                    client.recordLoginEvent(
                        LoginPasskeyEvent.LoginNoCredentials,
                        LoginSituation.ClientPasskeyOperationCancelled
                    )

                    ConnectLoginWithIdentifierStatus.InitSilentFallback(
                        identifier,
                        "No local credentials available for login. User has to use conventional login."
                    )
                }

                else -> {
                    client.recordLoginEvent(
                        LoginPasskeyEvent.LoginErrorUnexpected(e),
                        LoginSituation.CboApiNotAvailablePostAuthenticator
                    )

                    ConnectLoginWithIdentifierStatus.Error(
                        error = LoginWithIdentifierError.UnHandledError,
                        triggerFallback = true,
                        developerDetails = "An error occurred during login finish: ${e.message}"
                    )
                }
            }
        }


        try {
            val finishRsp = client.loginFinish(
                authenticatorResponse = authenticatorResponse, isConditionalUI = false
            )

            finishRsp.fallbackOperationError?.let {
                handleFallbackOperationErrorForLoginWithIdentifier(it)?.let { return@withContext it }
            }

            finishRsp.passkeyOperation?.let {
                val lastLogin = LastLogin.from(it)
                clientStateService.setLastLogin(lastLogin)

                return@withContext ConnectLoginWithIdentifierStatus.Done(
                    finishRsp.signedPasskeyData, it.identifierValue
                )
            }

            // we don't need tracking for this case (this would be visible in the backend)
            return@withContext ConnectLoginWithIdentifierStatus.Error(
                error = LoginWithIdentifierError.CorbadoAPIError,
                triggerFallback = true,
                developerDetails = "The login finish call did not return a passkey operation. This is unexpected behavior. Please report this as a GitHub issue.",
            )
        } catch (e: Exception) {
            client.recordLoginEvent(
                LoginPasskeyEvent.LoginErrorUnexpected(e),
                LoginSituation.CboApiNotAvailablePostAuthenticator
            )

            return@withContext ConnectLoginWithIdentifierStatus.Error(
                username = identifier,
                error = LoginWithIdentifierError.UnHandledError,
                triggerFallback = true,
                developerDetails = "An unhandled error occurred. Please report this as a GitHub issue. Message: ${e.message}, Cause: ${e.cause}, Stacktrace: ${e.stackTraceToString()}",
            )
        }
    }

suspend fun Corbado.loginWithoutIdentifier(
    activityContext: Context, cuiChallenge: String, onStart: suspend () -> Unit = {}
): ConnectLoginWithoutIdentifierStatus = withContext(Dispatchers.IO) {
    val authenticatorRequest = try {
        val assertionOptions = authController.serializeGetCredentialRequest(
            CorbadoClient.RelyingPartyServerDeserializer.deserializeAssertion(cuiChallenge)
        )
        GetPublicKeyCredentialOption(requestJson = assertionOptions)
    } catch (e: Exception) {
        client.recordLoginEvent(
            LoginPasskeyEvent.LoginErrorUnexpected(e),
            LoginSituation.CboApiNotAvailablePreConditionalAuthenticator
        )

        return@withContext ConnectLoginWithoutIdentifierStatus.InitSilentFallback(
            developerDetails = "An unhandled error occurred. Please report this as a GitHub issue. Message: ${e.message}, Cause: ${e.cause}, Stacktrace: ${e.stackTraceToString()}"
        )
    }

    val authenticatorResponse = try {
        val response = authController.authorize(activityContext, listOf(authenticatorRequest), true)

        onStart()
        when (val credential = response.credential) {
            is PublicKeyCredential -> authController.typeGetCredentialResponse(credential)
            else -> throw IllegalArgumentException(
                "Credential must be of type PasskeyAssertionCredential. " + "Actual type: ${credential.javaClass.simpleName}"
            )
        }
    } catch (e: AuthorizationError) {
        return@withContext when (e) {
            AuthorizationError.Cancelled -> {
                client.recordLoginEvent(
                    LoginPasskeyEvent.LoginError(e),
                    LoginSituation.ClientPasskeyConditionalOperationCancelled
                )

                ConnectLoginWithoutIdentifierStatus.Ignore("User cancelled the operation.")
            }

            AuthorizationError.NoCredentialsAvailable -> {
                client.recordLoginEvent(
                    LoginPasskeyEvent.LoginNoCredentials,
                    LoginSituation.ClientPasskeyConditionalOperationCancelled
                )

                ConnectLoginWithoutIdentifierStatus.Ignore(
                    "No local credentials available for login."
                )
            }

            else -> {
                client.recordLoginEvent(
                    LoginPasskeyEvent.LoginErrorUnexpected(e),
                    LoginSituation.CboApiNotAvailablePostConditionalAuthenticator
                )

                ConnectLoginWithoutIdentifierStatus.Error(
                    error = LoginWithoutIdentifierError.UnHandledError,
                    triggerFallback = true,
                    developerDetails = "An unhandled error occurred. Please report this as a GitHub issue (AuthorizationError). Message: ${e.message}, Cause: ${e.cause}, Stacktrace: ${e.stackTraceToString()}",
                )
            }
        }
    }

    try {
        val finishRsp = client.loginFinish(
            authenticatorResponse = authenticatorResponse, isConditionalUI = true
        )

        finishRsp.fallbackOperationError?.let {
            handleFallbackOperationErrorForLoginWithoutIdentifier(it)?.let { return@withContext it }
        }

        finishRsp.passkeyOperation?.let {
            val lastLogin = LastLogin.from(it)
            clientStateService.setLastLogin(lastLogin)
            return@withContext ConnectLoginWithoutIdentifierStatus.Done(
                finishRsp.signedPasskeyData, it.identifierValue
            )
        }

        return@withContext ConnectLoginWithoutIdentifierStatus.Error(
            error = LoginWithoutIdentifierError.CorbadoAPIError,
            triggerFallback = true,
            developerDetails = "The login finish call did not return a passkey operation. This is unexpected behavior. Please report this as a GitHub issue.",
        )
    } catch (e: Exception) {
        client.recordLoginEvent(
            LoginPasskeyEvent.LoginErrorUnexpected(e),
            LoginSituation.CboApiNotAvailablePostConditionalAuthenticator
        )

        return@withContext ConnectLoginWithoutIdentifierStatus.Error(
            error = LoginWithoutIdentifierError.UnHandledError,
            triggerFallback = true,
            developerDetails = "An unhandled error occurred. Please report this as a GitHub issue (AuthorizationError). Message: ${e.message}, Cause: ${e.cause}, Stacktrace: ${e.stackTraceToString()}",
        )
    }
}

suspend fun Corbado.loginRecordExplicitAbort() = withContext(Dispatchers.IO) {
    client.recordLoginEvent(LoginPasskeyEvent.LoginExplicitAbort)
}

private fun handleFallbackOperationErrorForLoginWithIdentifier(
    fallbackOperationError: FallbackOperationError,
): ConnectLoginWithIdentifierStatus? {
    val error = fallbackOperationError.error
    val identifier = fallbackOperationError.identifier
    // The backend did not return a successful response, but it did not return an error code either.
    // We can either react with a silent fallback or an ignore.
    if (error == null) {
        return if (fallbackOperationError.initFallback) {
            ConnectLoginWithIdentifierStatus.InitSilentFallback(
                identifier,
                "A post-auth operation error occurred during a login call. A silent fallback is triggered because no error code was provided."
            )
        } else {
            return null
        }
    }

    when (error.code) {
        "no_cbo_user" -> return ConnectLoginWithIdentifierStatus.InitSilentFallback(
            identifier,
            "This user interacts with a passkey component for the very first time. There can't be a passkey yet, so we trigger a silent fallback."
        )

        "user_not_found" -> {
            return ConnectLoginWithIdentifierStatus.Error(
                LoginWithIdentifierError.UserNotFound,
                triggerFallback = false,
                "The user tried to log in with an identifier that does not match any account. The user can correct the identifier in the text field and try again.",
                identifier,
            )
        }

        "identifier_not_whitelisted" -> {
            return ConnectLoginWithIdentifierStatus.InitSilentFallback(
                identifier,
                "The project is currently in gradual rollout phase. The user tried to log in with an identifier that is not whitelisted yet. A silent fallback is triggered."
            )
        }

        "unexpected_error" -> {
            return ConnectLoginWithIdentifierStatus.Error(
                LoginWithIdentifierError.CorbadoAPIError,
                triggerFallback = true,
                "An unexpected error occurred during a login call. This is a generic error that is not handled by the SDK. Please report this as a GitHub issue.",
                identifier,
            )
        }

        else -> {
            val customError = LoginWithIdentifierError.CustomError(error.code, error.message)
            val developerDetails =
                "A login call returned an error that is not handled by the SDK. This is a custom error defined in one of your actions."
            return if (fallbackOperationError.initFallback) {
                ConnectLoginWithIdentifierStatus.Error(
                    customError,
                    true,
                    developerDetails,
                    identifier,
                )
            } else {
                ConnectLoginWithIdentifierStatus.Error(
                    customError,
                    false,
                    developerDetails,
                    identifier,
                )
            }
        }
    }
}

private fun handleFallbackOperationErrorForLoginWithoutIdentifier(
    fallbackOperationError: FallbackOperationError,
): ConnectLoginWithoutIdentifierStatus? {
    val error = fallbackOperationError.error
    // The backend did not return a successful response, but it did not return an error code either.
    // We can either react with a silent fallback or an ignore.
    if (error == null) {
        return if (fallbackOperationError.initFallback) {
            ConnectLoginWithoutIdentifierStatus.InitSilentFallback(
                fallbackOperationError.identifier,
                "A post-auth operation error occurred during a login finish call."
            )
        } else {
            ConnectLoginWithoutIdentifierStatus.Ignore(
                "A post-auth operation error occurred during a login finish call."
            )
        }
    }

    when (error.code) {
        "cui_credential_deleted" -> return ConnectLoginWithoutIdentifierStatus.Error(
            error = LoginWithoutIdentifierError.PasskeyDeletedOnServer(error.message),
            triggerFallback = true,
            developerDetails = "The user tried to log in with a passkey that was deleted on the server.",
        )

        "cui_alternative_project_id" -> {
            val regex =
                Regex("This passkey is linked to a (.+) account. Try again with your (.+) passkeys.")
            val matchResult = regex.find(error.message)
            var wrongProjectName: String? = null
            var correctProjectName: String? = null
            if (matchResult != null && matchResult.groupValues.size == 3) {
                wrongProjectName = matchResult.groupValues[1]
                correctProjectName = matchResult.groupValues[2]
            }

            return ConnectLoginWithoutIdentifierStatus.Error(
                error = LoginWithoutIdentifierError.ProjectIDMismatch(
                    error.message, wrongProjectName, correctProjectName
                ),
                triggerFallback = true,
                developerDetails = "You configured multiple projects with the same RPID. The user tried to log in with a passkey that is associated with a different project ID than the one configured in the SDK.",
            )
        }

        else -> {
            val customError = LoginWithoutIdentifierError.CustomError(error.code, error.message)
            val developerDetails =
                "The login finish call returned an error that is not handled by the SDK. This is a custom error defined in one of your actions."
            return if (fallbackOperationError.initFallback) {
                ConnectLoginWithoutIdentifierStatus.Error(
                    customError,
                    triggerFallback = true,
                    developerDetails,
                    fallbackOperationError.identifier,
                )
            } else {
                ConnectLoginWithoutIdentifierStatus.Error(
                    customError,
                    triggerFallback = false,
                    developerDetails,
                    fallbackOperationError.identifier,
                )
            }
        }
    }
}

private fun Corbado.getConnectLoginStepLoginInit(loginData: ConnectLoginInitData): ConnectLoginStep {
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
