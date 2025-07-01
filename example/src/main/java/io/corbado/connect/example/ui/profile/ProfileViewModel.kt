package com.corbado.connect.example.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amplifyframework.auth.AuthUserAttributeKey
import com.amplifyframework.auth.cognito.AWSCognitoAuthSession
import com.amplifyframework.kotlin.core.Amplify
import com.corbado.connect.core.ConnectManageStatus
import com.corbado.connect.core.ConnectManageStep
import com.corbado.connect.core.ConnectTokenError
import com.corbado.connect.core.ConnectTokenType
import com.corbado.connect.core.Passkey
import com.corbado.connect.core.completePasskeyListAppend
import com.corbado.connect.core.deletePasskey
import com.corbado.connect.example.di.CorbadoService
import com.corbado.connect.example.ui.login.NavigationEvent
import com.corbado.connect.core.isManageAppendAllowed
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val corbado = CorbadoService.getInstance(application)
    val isLoading = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)
    val listMessage = MutableStateFlow<String?>(null)
    val email = MutableStateFlow("")
    val phoneNumber = MutableStateFlow("")
    val passkeys = MutableStateFlow<List<Passkey>>(emptyList())
    val passkeyAppendAllowed = MutableStateFlow(false)

    private val _navigationEvents = MutableSharedFlow<NavigationEvent>()
    val navigationEvents: SharedFlow<NavigationEvent> = _navigationEvents

    fun fetchUserData() {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            passkeys.value = emptyList()

            try {
                val attributes = Amplify.Auth.fetchUserAttributes()
                attributes.forEach {
                    when (it.key) {
                        AuthUserAttributeKey.email() -> email.value = it.value
                        AuthUserAttributeKey.phoneNumber() -> phoneNumber.value = it.value
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                errorMessage.value = "Could not fetch user attributes: ${e.message}"
            }

            when (val manageStep = corbado.isManageAppendAllowed(::connectTokenProvider)) {
                is ConnectManageStep.Allowed -> {
                    passkeys.value = manageStep.passkeys
                    passkeyAppendAllowed.value = true
                }

                is ConnectManageStep.NotAllowed -> {
                    passkeys.value = manageStep.passkeys
                    passkeyAppendAllowed.value = false
                }

                is ConnectManageStep.Error -> {
                    errorMessage.value = "Unable to access passkeys. Check your connection and try again."
                }
            }

            if (errorMessage.value != null) {
                listMessage.value = "We were unable to show your list of passkeys due to an error. Try again later."
            } else if (passkeys.value.isEmpty()) {
                listMessage.value = "There is currently no passkey saved for this account."
            } else {
                listMessage.value = null
            }

            isLoading.value = false
        }
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

    fun appendPasskey(activityContext: android.content.Context) {
        viewModelScope.launch {
            when (val appendStatus = corbado.completePasskeyListAppend(activityContext, ::connectTokenProvider)) {
                is ConnectManageStatus.Done -> {
                    passkeys.value = appendStatus.passkeys
                    listMessage.value = if (appendStatus.passkeys.isEmpty()) "There is currently no passkey saved for this account." else null
                }
                is ConnectManageStatus.PasskeyOperationCancelled -> {
                    errorMessage.value = "Passkey append cancelled."
                }
                is ConnectManageStatus.Error -> {
                    errorMessage.value = "Passkey creation failed. Please try again later."
                }
                is ConnectManageStatus.PasskeyOperationExcludeCredentialsMatch -> {
                    errorMessage.value = "You already have a passkey that can be used on this device."
                }
            }
        }
    }

    fun deletePasskey(passkeyId: String) {
        viewModelScope.launch {
            when (val deleteStatus = corbado.deletePasskey(::connectTokenProvider, passkeyId)) {
                is ConnectManageStatus.Done -> {
                    passkeys.value = deleteStatus.passkeys
                    listMessage.value = if (deleteStatus.passkeys.isEmpty()) "There is currently no passkey saved for this account." else null
                }
                else -> {
                    errorMessage.value = "Passkey deletion failed. Please try again later."
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            Amplify.Auth.signOut()
            _navigationEvents.emit(NavigationEvent.NavigateTo("login"))
        }
    }
} 