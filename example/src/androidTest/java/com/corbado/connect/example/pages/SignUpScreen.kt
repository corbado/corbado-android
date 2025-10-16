package com.corbado.connect.example.pages

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.corbado.connect.example.helpers.CredentialManagerHelper
import kotlinx.coroutines.runBlocking

/**
 * Page object for the Sign Up Screen.
 */
class SignUpScreen(composeTestRule: ComposeTestRule) : BaseScreen(composeTestRule) {

    private val credentialManagerHelper = CredentialManagerHelper()

    override fun visible(timeout: Double): Boolean {
        return awaitVisible("SignUpScreen", timeout)
    }

    /**
     * Navigate back to the login screen.
     */
    fun navigateToLogin(): LoginScreen {
        composeTestRule.onNodeWithText("Already have an account? Log In").performClick()
        return LoginScreen(composeTestRule).also { it.visible() }
    }

    /**
     * Perform sign up with valid data.
     * Attempts to handle password save dialog if it appears (optional).
     */
    suspend fun signUpWithValidData(
        email: String,
        phoneNumber: String,
        password: String
    ): PostLoginScreen {
        composeTestRule.onNodeWithTag("SignUpEmailTextField").performTextInput(email)
        composeTestRule.onNodeWithTag("SignUpPhoneTextField").performTextInput(phoneNumber)
        composeTestRule.onNodeWithTag("SignUpPasswordTextField").performTextInput(password)
        composeTestRule.onNodeWithTag("SignUpSubmitButton").performClick()

        credentialManagerHelper.handlePasswordSaveDialog()

        return PostLoginScreen(composeTestRule).also {
            it.visible(timeout = 5.0)
        }
    }
} 