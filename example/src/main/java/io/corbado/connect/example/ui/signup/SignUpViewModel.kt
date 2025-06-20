package io.corbado.connect.example.ui.signup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amplifyframework.auth.AuthUserAttribute
import com.amplifyframework.auth.AuthUserAttributeKey
import com.amplifyframework.auth.options.AuthSignUpOptions
import com.amplifyframework.kotlin.core.Amplify
import io.corbado.connect.example.ui.Screen
import io.corbado.connect.example.ui.login.NavigationEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.util.UUID

class SignUpViewModel(application: Application) : AndroidViewModel(application) {
    val email = MutableStateFlow("")
    val phoneNumber = MutableStateFlow("")
    val password = MutableStateFlow("")
    val primaryLoading = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)

    private val _navigationEvents = MutableSharedFlow<NavigationEvent>()
    val navigationEvents: SharedFlow<NavigationEvent> = _navigationEvents

    fun signUp() {
        viewModelScope.launch {
            primaryLoading.value = true
            errorMessage.value = null

            try {
                val userAttributes = listOf(
                    AuthUserAttribute(AuthUserAttributeKey.email(), email.value),
                    AuthUserAttribute(AuthUserAttributeKey.phoneNumber(), phoneNumber.value)
                )

                val options = AuthSignUpOptions.builder()
                    .userAttributes(userAttributes)
                    .build()

                Amplify.Auth.signUp(UUID.randomUUID().toString(), password.value, options)

                val result = Amplify.Auth.signIn(email.value, password.value)
                if (result.isSignedIn) {
                    _navigationEvents.emit(NavigationEvent.NavigateTo(Screen.PostLogin.route))
                } else {
                    // This case is not handled in this example
                }

            } catch (error: Exception) {
                errorMessage.value = error.message
            }

            primaryLoading.value = false
        }
    }

    fun autoFill() {
        val randomChars = (1..10)
            .map { ('a'..'z') + ('A'..'Z') + ('0'..'9') }
            .map { it.random() }
            .joinToString("")

        email.value = "test@example.com"
        phoneNumber.value = "+4915112345678"
        password.value = "asdfasdf"
    }
} 