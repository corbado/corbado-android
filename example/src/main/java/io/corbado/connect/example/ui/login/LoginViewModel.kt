package com.corbado.connect.example.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amplifyframework.auth.AuthException
import com.amplifyframework.auth.cognito.options.AWSCognitoAuthSignInOptions
import com.amplifyframework.auth.cognito.options.AuthFlowType
import com.amplifyframework.auth.result.step.AuthSignInStep
import com.corbado.connect.ConnectLoginStep
import com.corbado.connect.Corbado
import com.corbado.connect.clearOneTap
import com.corbado.connect.example.di.CorbadoService
import com.corbado.connect.example.ui.Screen
import com.corbado.connect.isLoginAllowed
import com.corbado.connect.loginWithOneTap
import com.corbado.connect.loginWithTextField
import com.corbado.connect.loginWithoutIdentifier
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.amplifyframework.kotlin.core.Amplify
import com.corbado.connect.ConnectLoginWithIdentifierStatus
import com.corbado.connect.ConnectLoginWithoutIdentifierStatus

sealed class NavigationEvent {
    data class NavigateTo(val route: String) : NavigationEvent()
}

enum class LoginStatus {
    Loading,
    FallbackFirst,
    FallbackSecondTOTP,
    FallbackSecondSMS,
    PasskeyTextField,
    PasskeyOneTap,
    PasskeyErrorSoft,
    PasskeyErrorHard,
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val corbado: Corbado = CorbadoService.getInstance(application)

    private val _status = MutableStateFlow<LoginStatus>(LoginStatus.Loading)
    val status: StateFlow<LoginStatus> = _status

    private val _navigationEvents = MutableSharedFlow<NavigationEvent>()
    val navigationEvents: SharedFlow<NavigationEvent> = _navigationEvents

    val email = MutableStateFlow("")
    val password = MutableStateFlow("")
    val primaryLoading = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)

    private var initialized = false
    private var retryCount = 0

    fun loadInitialStep(activityContext: android.content.Context) {
        if (initialized) return
        initialized = true

        viewModelScope.launch {
            _status.value = LoginStatus.Loading
            when (val nextStep = corbado.isLoginAllowed()) {
                is ConnectLoginStep.InitOneTap -> {
                    email.value = nextStep.username
                    _status.value = LoginStatus.PasskeyOneTap
                }

                is ConnectLoginStep.InitTextField -> {
                    _status.value = LoginStatus.PasskeyTextField
                    nextStep.challenge?.let {
                        val result = corbado.loginWithoutIdentifier(activityContext, it)
                        completePasskeyLoginWithoutIdentifier(result)
                    }
                }

                is ConnectLoginStep.InitFallback -> {
                    _status.value = LoginStatus.FallbackFirst
                    errorMessage.value = nextStep.cause?.message
                }
            }
        }
    }

    private suspend fun completePasskeyLoginWithoutIdentifier(result: ConnectLoginWithoutIdentifierStatus) {
        when (result) {
            is ConnectLoginWithoutIdentifierStatus.Done -> {
                try {
                    val options = AWSCognitoAuthSignInOptions.builder()
                        .authFlowType(AuthFlowType.CUSTOM_AUTH_WITHOUT_SRP)
                        .build()
                    val signInResult = Amplify.Auth.signIn(result.username, null, options)
                    if (signInResult.nextStep.signInStep == AuthSignInStep.CONFIRM_SIGN_IN_WITH_CUSTOM_CHALLENGE) {
                        val confirmResult = Amplify.Auth.confirmSignIn(result.session)
                        if (confirmResult.isSignedIn) {
                            _navigationEvents.emit(NavigationEvent.NavigateTo(Screen.Profile.route))
                        } else {
                            errorMessage.value = "Sign in not complete."
                        }
                    } else {
                        errorMessage.value = "Unexpected sign in step: ${signInResult.nextStep.signInStep}"
                    }
                } catch (error: AuthException) {
                    errorMessage.value = error.message
                }
            }

            is ConnectLoginWithoutIdentifierStatus.InitSilentFallback -> {
                result.username?.let { email.value = it }
                _status.value = LoginStatus.FallbackFirst
            }

            is ConnectLoginWithoutIdentifierStatus.Error -> {
                errorMessage.value = result.error.message
                if (result.triggerFallback) {
                    _status.value = LoginStatus.FallbackFirst
                } else {
                    _status.value = LoginStatus.PasskeyTextField
                }

                result.username?.let { email.value = it }
            }

            is ConnectLoginWithoutIdentifierStatus.Ignore -> {
                // No action needed, just ignore
            }
        }
        primaryLoading.value = false
    }

    private suspend fun completePasskeyLoginWithIdentifier(result: ConnectLoginWithIdentifierStatus) {
        primaryLoading.value = true

        when (result) {
            is ConnectLoginWithIdentifierStatus.Done -> {
                try {
                    val options = AWSCognitoAuthSignInOptions.builder()
                        .authFlowType(AuthFlowType.CUSTOM_AUTH_WITHOUT_SRP)
                        .build()
                    val signInResult = Amplify.Auth.signIn(result.username, null, options)
                    if (signInResult.nextStep.signInStep == AuthSignInStep.CONFIRM_SIGN_IN_WITH_CUSTOM_CHALLENGE) {
                        val confirmResult = Amplify.Auth.confirmSignIn(result.session)
                        if (confirmResult.isSignedIn) {
                            _navigationEvents.emit(NavigationEvent.NavigateTo(Screen.Profile.route))
                        } else {
                            errorMessage.value = "Sign in not complete."
                        }
                    } else {
                        errorMessage.value = "Unexpected sign in step: ${signInResult.nextStep.signInStep}"
                    }
                } catch (error: AuthException) {
                    errorMessage.value = error.message
                }
            }

            is ConnectLoginWithIdentifierStatus.Error -> {
                errorMessage.value = result.error.message
                if (result.triggerFallback) {
                    _status.value = LoginStatus.FallbackFirst
                } else {
                    _status.value = LoginStatus.PasskeyTextField
                }

                result.username?.let { email.value = it }
            }
            is ConnectLoginWithIdentifierStatus.InitRetry -> {
                retryCount++
                if (retryCount > 5) {
                    _status.value = LoginStatus.FallbackFirst
                } else if (retryCount > 1) {
                    _status.value = LoginStatus.PasskeyErrorHard
                } else {
                    _status.value = LoginStatus.PasskeyErrorSoft
                }
            }
            is ConnectLoginWithIdentifierStatus.InitSilentFallback -> {
                result.username?.let { email.value = it }
                _status.value = LoginStatus.FallbackFirst
            }
        }

        primaryLoading.value = false
    }

    fun loginWithEmailAndPassword() {
        viewModelScope.launch {
            if (email.value.isEmpty() || password.value.isEmpty()) {
                errorMessage.value = "Please enter email and password."
                return@launch
            }

            errorMessage.value = null
            primaryLoading.value = true

            try {
                val result = Amplify.Auth.signIn(email.value, password.value)
                when (result.nextStep.signInStep) {
                    AuthSignInStep.CONFIRM_SIGN_IN_WITH_SMS_MFA_CODE -> _status.value = LoginStatus.FallbackSecondSMS
                    AuthSignInStep.CONFIRM_SIGN_IN_WITH_TOTP_CODE -> _status.value = LoginStatus.FallbackSecondTOTP
                    AuthSignInStep.DONE -> _navigationEvents.emit(NavigationEvent.NavigateTo(Screen.Profile.route))
                    else -> {
                        errorMessage.value = "Unexpected sign in step: ${result.nextStep.signInStep}"
                    }
                }
            } catch (error: AuthException) {
                errorMessage.value = error.message
            }

            primaryLoading.value = false
        }
    }

    fun loginWithPasskeyTextField(activityContext: android.content.Context) {
        viewModelScope.launch {
            primaryLoading.value = true
            val result = corbado.loginWithTextField(activityContext, email.value)
            completePasskeyLoginWithIdentifier(result)
        }
    }

    fun loginWithPasskeyOneTap(activityContext: android.content.Context) {
        viewModelScope.launch {
            primaryLoading.value = true
            val result = corbado.loginWithOneTap(activityContext)
            completePasskeyLoginWithIdentifier(result)
        }
    }

    fun verifyTOTP(code: String) {
        viewModelScope.launch {
            primaryLoading.value = true
            errorMessage.value = null

            try {
                val result = Amplify.Auth.confirmSignIn(code)
                if (result.isSignedIn) {
                    _navigationEvents.emit(NavigationEvent.NavigateTo(Screen.PostLogin.route))
                }
            } catch (error: AuthException) {
                errorMessage.value = error.message
            }

            primaryLoading.value = false
        }
    }

    fun verifySMS(code: String) {
        viewModelScope.launch {
            primaryLoading.value = true
            errorMessage.value = null

            try {
                val result = Amplify.Auth.confirmSignIn(code)
                if (result.isSignedIn) {
                    _navigationEvents.emit(NavigationEvent.NavigateTo(Screen.PostLogin.route))
                }
            } catch (error: AuthException) {
                errorMessage.value = error.message
            }

            primaryLoading.value = false
        }
    }

    fun discardPasskeyOneTap() {
        viewModelScope.launch {
            corbado.clearOneTap()
            email.value = ""
            _status.value = LoginStatus.PasskeyTextField
        }
    }

    fun discardPasskeyLogin() {
        _status.value = LoginStatus.FallbackFirst
    }
} 