package io.corbado.connect.example.ui.totpscreen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amplifyframework.auth.TOTPSetupDetails
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.auth.cognito.MFAPreference
import com.amplifyframework.kotlin.core.Amplify
import io.corbado.connect.example.ui.login.NavigationEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class TotpSetupViewModel(application: Application) : AndroidViewModel(application) {
    val isLoading = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)
    val setupDetails = MutableStateFlow<TOTPSetupDetails?>(null)
    val totpCode = MutableStateFlow("")

    private val _navigationEvents = MutableSharedFlow<NavigationEvent>()
    val navigationEvents: SharedFlow<NavigationEvent> = _navigationEvents

    fun initSetupTOTP() {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null

            try {
                setupDetails.value = Amplify.Auth.setUpTOTP()
            } catch (e: Exception) {
                errorMessage.value = "TOTP could not be initialized. Try again later."
            }
            isLoading.value = false
        }
    }

    fun completeSetupTOTP() {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null

            try {
                Amplify.Auth.verifyTOTPSetup(totpCode.value)
                val plugin =
                    com.amplifyframework.core.Amplify.Auth.getPlugin("awsCognitoAuthPlugin") as AWSCognitoAuthPlugin

                plugin.updateMFAPreference(
                    totp = MFAPreference.PREFERRED, sms = MFAPreference.NOT_PREFERRED, email = MFAPreference.NOT_PREFERRED,
                    onSuccess = {
                        viewModelScope.launch {
                            _navigationEvents.emit(NavigationEvent.NavigateTo("profile"))
                        }
                    },
                    onError = { error ->
                        errorMessage.value = "Failed to update MFA preference: ${error.message}"
                    }
                )
            } catch (e: Exception) {
                errorMessage.value =
                    "An unexpected error occurred during TOTP verification: ${e.message}"
            }

            isLoading.value = false
        }
    }
} 