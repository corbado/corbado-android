package io.corbado.connect.example.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amplifyframework.auth.AuthException
import com.amplifyframework.auth.cognito.options.AWSCognitoAuthSignInOptions
import com.amplifyframework.auth.cognito.options.AuthFlowType
import com.amplifyframework.auth.result.step.AuthSignInStep
import io.corbado.connect.ConnectLoginStatus
import io.corbado.connect.ConnectLoginStep
import io.corbado.connect.Corbado
import io.corbado.connect.clearOneTap
import io.corbado.connect.example.di.CorbadoService
import io.corbado.connect.example.ui.Screen
import io.corbado.connect.isLoginAllowed
import io.corbado.connect.loginWithOneTap
import io.corbado.connect.loginWithTextField
import io.corbado.connect.loginWithoutIdentifier
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.amplifyframework.kotlin.core.Amplify

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

    fun loadInitialStep() {
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
                        val result = corbado.loginWithoutIdentifier(it)
                        completePasskeyLogin(result)
                    }
                }

                is ConnectLoginStep.InitFallback -> {
                    _status.value = LoginStatus.FallbackFirst
                    errorMessage.value = nextStep.error?.message
                }
            }
        }
    }

    private suspend fun completePasskeyLogin(result: ConnectLoginStatus) {
        primaryLoading.value = true
        when (result) {
            is ConnectLoginStatus.Done -> {
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

            is ConnectLoginStatus.InitFallback -> {
                errorMessage.value = result.error?.message
                result.username?.let { email.value = it }
                _status.value = LoginStatus.FallbackFirst
            }

            is ConnectLoginStatus.InitRetry -> {
                retryCount++
                if (retryCount > 5) {
                    _status.value = LoginStatus.FallbackFirst
                } else if (retryCount > 1) {
                    _status.value = LoginStatus.PasskeyErrorHard
                } else {
                    _status.value = LoginStatus.PasskeyErrorSoft
                }
            }

            is ConnectLoginStatus.InitTextField -> {
                errorMessage.value = result.error?.message
                _status.value = LoginStatus.PasskeyTextField
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

    fun loginWithPasskeyTextField() {
        viewModelScope.launch {
            primaryLoading.value = true
            val result = corbado.loginWithTextField(email.value)
            completePasskeyLogin(result)
        }
    }

    fun loginWithPasskeyOneTap() {
        viewModelScope.launch {
            primaryLoading.value = true
            val result = corbado.loginWithOneTap()
            completePasskeyLogin(result)
        }
    }

    fun verifyTOTP(code: String) {
        viewModelScope.launch {
            primaryLoading.value = true
            errorMessage.value = null

            try {
                val result = Amplify.Auth.confirmSignIn(code)
                if (result.isSignedIn) {
                    _navigationEvents.emit(NavigationEvent.NavigateTo(Screen.Profile.route))
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
                    _navigationEvents.emit(NavigationEvent.NavigateTo(Screen.Profile.route))
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