package io.corbado.connect.example.ui.postlogin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.auth.cognito.AWSCognitoAuthSession
import com.amplifyframework.kotlin.core.Amplify
import io.corbado.connect.ConnectAppendStep
import io.corbado.connect.ConnectAppendStatus
import io.corbado.connect.ConnectTokenError
import io.corbado.connect.ConnectTokenType
import io.corbado.connect.completeAppend
import io.corbado.connect.example.di.CorbadoService
import io.corbado.connect.example.ui.login.NavigationEvent
import io.corbado.connect.example.ui.profile.AppBackend
import io.corbado.connect.isAppendAllowed
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class PostLoginStatus {
    Loading,
    PasskeyAppend,
    PasskeyAppended
}

class PostLoginViewModel(application: Application) : AndroidViewModel(application) {
    private val corbado = CorbadoService.getInstance(application)
    val state = MutableStateFlow(PostLoginStatus.Loading)
    val primaryLoading = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)

    private val _navigationEvents = MutableSharedFlow<NavigationEvent>()
    val navigationEvents: SharedFlow<NavigationEvent> = _navigationEvents

    private var initialized = false

    fun loadInitialStep() {
        if (initialized) return
        initialized = true

        viewModelScope.launch {
            val nextStep = corbado.isAppendAllowed(::connectTokenProvider)
            when (nextStep) {
                is ConnectAppendStep.AskUserForAppend -> {
                    state.value = PostLoginStatus.PasskeyAppend
                    if (nextStep.autoAppend) {
                        createPasskey()
                    }
                }

                is ConnectAppendStep.Skip -> {
                    skipPasskeyCreation()
                }
            }
        }
    }

    private suspend fun connectTokenProvider(): String {
        val session = Amplify.Auth.fetchAuthSession() as AWSCognitoAuthSession
        val userPoolToken = session.userPoolTokensResult
        val idToken = userPoolToken.value?.idToken
        if (idToken == null) {
            throw ConnectTokenError("No id token found")
        }

        val result = AppBackend.getConnectToken(ConnectTokenType.PasskeyAppend, idToken)
        if (result.isFailure) {
            throw ConnectTokenError("Could not get connect token")
        }

        return result.getOrThrow()
    }

    fun createPasskey() {
        viewModelScope.launch {
            primaryLoading.value = true
            when(corbado.completeAppend()) {
                ConnectAppendStatus.Completed() -> state.value = PostLoginStatus.PasskeyAppended
                ConnectAppendStatus.Cancelled -> errorMessage.value = "You have cancelled setting up your passkey. Please try again."
                else -> skipPasskeyCreation()
            }
            primaryLoading.value = false
        }
    }

    fun skipPasskeyCreation() {
        viewModelScope.launch {
            val hasMFA = hasMFA()
            if (hasMFA) {
                _navigationEvents.emit(NavigationEvent.NavigateTo("profile"))
            } else {
                _navigationEvents.emit(NavigationEvent.NavigateTo("totpSetup"))
            }
        }
    }

    private suspend fun hasMFA(): Boolean {
        return suspendCoroutine { continuation ->
            val plugin = com.amplifyframework.core.Amplify.Auth.getPlugin("awsCognitoAuthPlugin") as AWSCognitoAuthPlugin
            plugin.fetchMFAPreference(
                { continuation.resume(it.enabled?.isNotEmpty() == true) },
                { continuation.resume(false) }
            )
        }
    }

    fun navigateAfterPasskeyAppend() {
        viewModelScope.launch {
            _navigationEvents.emit(NavigationEvent.NavigateTo("profile"))
        }
    }
} 