package com.corbado.connect.example.ui.home

import android.app.Application
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amplifyframework.auth.cognito.AWSCognitoAuthSession
import com.amplifyframework.kotlin.core.Amplify
import com.corbado.connect.core.AppendCompletionType
import com.corbado.connect.core.AppendSituationType
import com.corbado.connect.core.ConnectAppendStep
import com.corbado.connect.core.ConnectAppendStatus
import com.corbado.connect.core.ConnectTokenError
import com.corbado.connect.core.ConnectTokenType
import com.corbado.connect.core.completeAppend
import com.corbado.connect.core.isAppendAllowed
import com.corbado.connect.example.di.CorbadoService
import com.corbado.connect.example.ui.profile.AppBackend
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.time.Duration

sealed class HomeNavigationEvent {
    data class NavigateTo(val route: String) : HomeNavigationEvent()
}

data class HomeUiState(
    val showBottomSheet: Boolean = false,
    val isButton1PasskeyActive: Boolean = false,
    val isButton2PasskeyActive: Boolean = false,
    val isButton3PasskeyActive: Boolean = false,
    val notificationMessage: String? = null,
    val localDebounceDays: String = "0"
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        @VisibleForTesting
        var testLocalDebounceDays: String? = null
    }
    
    private val corbado = CorbadoService.getInstance(application)
    val uiState = MutableStateFlow(HomeUiState(localDebounceDays = testLocalDebounceDays ?: "0"))
    private var isAutoAppend = false
    
    private val _navigationEvents = MutableSharedFlow<HomeNavigationEvent>()
    val navigationEvents: SharedFlow<HomeNavigationEvent> = _navigationEvents
    
    fun onAppear(context: Context) {
        viewModelScope.launch {
            val debounceDays = uiState.value.localDebounceDays.toLongOrNull() ?: 0L
            val nextStep = corbado.isAppendAllowed(::connectTokenProvider, AppendSituationType("home", Duration.ofDays(debounceDays)))
            
            when (nextStep) {
                is ConnectAppendStep.AskUserForAppend -> {
                    val customData = nextStep.customData
                    val buttons = customData?.get("actions")?.split(",") ?: emptyList()
                    
                    if (buttons.contains("instant")) {
                        if (nextStep.autoAppend) {
                            completePasskeyAppend(context, autoAppend = true)
                        } else {
                            askForPasskeyAppend()
                        }
                        return@launch
                    }
                    
                    isAutoAppend = nextStep.autoAppend
                    val newState = uiState.value.copy(
                        isButton1PasskeyActive = buttons.contains("button1"),
                        isButton2PasskeyActive = buttons.contains("button2"),
                        isButton3PasskeyActive = buttons.contains("button3")
                    )
                    uiState.value = newState
                }
                
                is ConnectAppendStep.Skip -> {
                }
            }
        }
    }
    
    fun passkeyActiveButtonClicked(context: Context) {
        uiState.value = uiState.value.copy(
            isButton1PasskeyActive = false,
            isButton2PasskeyActive = false,
            isButton3PasskeyActive = false
        )
        
        viewModelScope.launch {
            if (isAutoAppend) {
                completePasskeyAppend(context, autoAppend = true)
            } else {
                askForPasskeyAppend()
            }
        }
    }
    
    fun completePasskeyAppend(context: Context, autoAppend: Boolean) {
        viewModelScope.launch {
            val completionType = if (autoAppend) AppendCompletionType.Auto else AppendCompletionType.Manual
            
            when (corbado.completeAppend(context, completionType)) {
                is ConnectAppendStatus.Cancelled -> {
                    if (autoAppend) {
                        askForPasskeyAppend()
                    }
                }
                else -> {
                }
            }
        }
    }
    
    fun dismissBottomSheet() {
        uiState.value = uiState.value.copy(showBottomSheet = false)
    }
    
    fun showNotification(message: String) {
        uiState.value = uiState.value.copy(notificationMessage = message)
    }
    
    fun dismissNotification() {
        uiState.value = uiState.value.copy(notificationMessage = null)
    }
    
    fun updateLocalDebounceDays(days: String) {
        testLocalDebounceDays = days
        uiState.value = uiState.value.copy(localDebounceDays = days)
    }
    
    private fun askForPasskeyAppend() {
        uiState.value = uiState.value.copy(showBottomSheet = true)
    }
    
    private suspend fun connectTokenProvider(connectTokenType: ConnectTokenType): String {
        val session = Amplify.Auth.fetchAuthSession() as AWSCognitoAuthSession
        val userPoolToken = session.userPoolTokensResult
        val idToken = userPoolToken.value?.idToken
        if (idToken == null) {
            throw ConnectTokenError("No id token found")
        }
        
        val result = AppBackend.getConnectToken(connectTokenType, idToken)
        if (result.isFailure) {
            throw ConnectTokenError("Could not get connect token")
        }
        
        return result.getOrThrow()
    }
}

