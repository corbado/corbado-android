package io.corbado.connect.example.ui.postlogin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amplifyframework.auth.cognito.AWSCognitoAuthSession
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.core.Amplify
import com.amplifyframework.kotlin.core.Amplify
import io.corbado.connect.Corbado
import io.corbado.connect.data.ConnectAppendInitRsp
import io.corbado.connect.data.ConnectTokenError
import io.corbado.connect.example.di.CorbadoService
import io.corbado.connect.example.ui.login.NavigationEvent
import io.corbado.connect.example.ui.profile.AppBackend
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

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
                is ConnectAppendInitRsp.AskUserForAppend -> {
                    state.value = PostLoginStatus.PasskeyAppend
                    if (nextStep.value.autoAppend) {
                        createPasskey()
                    }
                }
                is ConnectAppendInitRsp.Skip -> {
                    skipPasskeyCreation()
                }
            }
        }
    }

    private suspend fun connectTokenProvider(connectTokenType: Corbado.ConnectTokenType): Result<String> {
        val session = Amplify.Auth.fetchAuthSession()
        val idToken = (session as? AWSCognitoAuthSession)?.identityId?.getOrNull()

        if (idToken == null) {
            return Result.failure(ConnectTokenError("No id token found"))
        }

        return AppBackend.getConnectToken(connectTokenType, idToken)
    }

    fun createPasskey() {
        viewModelScope.launch {
            primaryLoading.value = true
            val rsp = corbado.completeAppend()
            rsp.onSuccess {
                state.value = PostLoginStatus.PasskeyAppended
            }.onFailure {
                errorMessage.value = "You have cancelled setting up your passkey. Please try again."
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
        return try {
            val cognitoPlugin = Amplify.Auth.getPlugin("awsCognitoAuthPlugin") as AWSCognitoAuthPlugin
            val preference = cognitoPlugin.fetchMFAPreference()
            preference.totp != null
        } catch (e: Exception) {
            false
        }
    }

    fun navigateAfterPasskeyAppend() {
        viewModelScope.launch {
            _navigationEvents.emit(NavigationEvent.NavigateTo("profile"))
        }
    }
} 