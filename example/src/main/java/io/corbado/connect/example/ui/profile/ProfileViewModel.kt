package io.corbado.connect.example.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amplifyframework.auth.AuthUserAttributeKey
import com.amplifyframework.kotlin.core.Amplify
import io.corbado.connect.Corbado
import io.corbado.connect.data.ConnectManageInitRsp
import io.corbado.connect.data.ConnectTokenError
import io.corbado.connect.data.models.PasskeyInfo
import io.corbado.connect.example.di.CorbadoService
import io.corbado.connect.example.ui.login.NavigationEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.amplifyframework.auth.cognito.AWSCognitoAuthSession

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val corbado = CorbadoService.getInstance(application)
    val isLoading = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)
    val listMessage = MutableStateFlow<String?>(null)
    val email = MutableStateFlow("")
    val phoneNumber = MutableStateFlow("")
    val passkeys = MutableStateFlow<List<PasskeyInfo>>(emptyList())
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

            val manageStep = corbado.isManageAppendAllowed(::connectTokenProvider)
            when (manageStep) {
                is ConnectManageInitRsp.Allowed -> {
                    passkeys.value = manageStep.value
                    passkeyAppendAllowed.value = true
                }

                is ConnectManageInitRsp.NotAllowed -> {
                    passkeys.value = manageStep.value
                    passkeyAppendAllowed.value = false
                }

                is ConnectManageInitRsp.Error -> {
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

    private suspend fun connectTokenProvider(connectTokenType: Corbado.ConnectTokenType): Result<String> {
        val session = Amplify.Auth.fetchAuthSession()
        val idToken = (session as? AWSCognitoAuthSession)?.identityId?.getOrNull()

        if (idToken == null) {
            return Result.failure(ConnectTokenError("No id token found"))
        }

        return AppBackend.getConnectToken(connectTokenType, idToken)
    }

    fun appendPasskey() {
        viewModelScope.launch {
            val appendStatus = corbado.completePasskeyListAppend(::connectTokenProvider)
            appendStatus.onSuccess {
                passkeys.value = it
                listMessage.value = if (it.isEmpty()) "There is currently no passkey saved for this account." else null
            }.onFailure {
                errorMessage.value = "Passkey creation failed. Please try again later."
            }
        }
    }

    fun deletePasskey(passkeyId: String) {
        viewModelScope.launch {
            val deleteStatus = corbado.deletePasskey(::connectTokenProvider, passkeyId)
            deleteStatus.onSuccess {
                passkeys.value = it
                listMessage.value = if (it.isEmpty()) "There is currently no passkey saved for this account." else null
            }.onFailure {
                errorMessage.value = "Passkey deletion failed. Please try again later."
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