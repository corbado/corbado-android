package io.corbado.connect.example.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amplifyframework.auth.AuthException
import com.amplifyframework.auth.result.AuthSignInResult
import com.amplifyframework.auth.result.step.AuthSignInStep
import com.amplifyframework.core.Amplify
import io.corbado.connect.example.di.CorbadoService
import io.corbado.connect.*
import io.corbado.connect.example.ui.Screen
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
    private val corbado = CorbadoService.getInstance(application)

    private val _status = MutableStateFlow(LoginStatus.Loading)
    val status: StateFlow<LoginStatus> = _status

    private val _navigationEvents = MutableSharedFlow<NavigationEvent>()
    val navigationEvents: SharedFlow<NavigationEvent> = _navigationEvents

    val email = MutableStateFlow("")
    val password = MutableStateFlow("")
    val primaryLoading = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)

    private var initialized = false

    fun loadInitialStep() {
        if (initialized) return
        initialized = true

        viewModelScope.launch {
            when (val nextStep = corbado.isLoginAllowed()) {
                is ConnectLoginStep.Done -> {
                    if (nextStep.value.conditionalUIChallenge != null) {
                        _status.value = LoginStatus.PasskeyTextField
                        val result = corbado.loginWithoutIdentifier(nextStep.value.conditionalUIChallenge)
                        completePasskeyLogin(result)
                    } else {
                        // This case is not handled in the iOS app, but it is a possible state.
                        // For now, we'll just go to the passkey text field.
                        _status.value = LoginStatus.PasskeyTextField
                    }
                }
                is ConnectLoginInitRsp.LoginNotAllowed -> {
                    _status.value = LoginStatus.FallbackFirst
                }
            }
        }
    }

    private suspend fun completePasskeyLogin(result: Result<ConnectLoginFinishRsp>) {
        primaryLoading.value = true
        result.onSuccess {
            try {
                val signInResult = Amplify.Auth.confirmSignIn(it.username, mapOf("session" to it.session))
                if (signInResult.isSignedIn) {
                    _navigationEvents.emit(NavigationEvent.NavigateTo(Screen.Profile.route))
                }
            } catch (error: AuthException) {
                errorMessage.value = error.message
            }
        }.onFailure {
            errorMessage.value = it.message
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
                    AuthSignInStep.CONFIRM_SIGN_IN_WITH_TOTP_MFA_CODE -> _status.value = LoginStatus.FallbackSecondTOTP
                    AuthSignInStep.DONE -> _navigationEvents.emit(NavigationEvent.NavigateTo(Screen.Profile.route))
                    else -> {
                        // Not handled in this example
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
            primaryLoading.value = false
        }
    }

    fun loginWithPasskeyOneTap() {
        viewModelScope.launch {
            primaryLoading.value = true
            val result = corbado.loginWithOneTap()
            completePasskeyLogin(result)
            primaryLoading.value = false
        }
    }

    fun verifyTOTP(code: String) {
        viewModelScope.launch {
            primaryLoading.value = true
            errorMessage.value = null

            try {
                val result = Amplify.Auth.confirmSignIn(code)
                if (result.isSignInComplete) {
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
                if (result.isSignInComplete) {
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