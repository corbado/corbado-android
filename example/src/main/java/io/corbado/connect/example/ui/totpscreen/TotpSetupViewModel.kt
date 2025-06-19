package io.corbado.connect.example.ui.totpscreen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.auth.result.TOTPSetupDetails
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
                val cognitoPlugin = Amplify.Auth.getPlugin("awsCognitoAuthPlugin") as AWSCognitoAuthPlugin
                cognitoPlugin.updateMFAPreference(totp = "PREFERRED")
                _navigationEvents.emit(NavigationEvent.NavigateTo("profile"))
            } catch (e: Exception) {
                errorMessage.value = "An unexpected error occurred during TOTP verification: ${e.message}"
            }

            isLoading.value = false
        }
    }
} 