package com.corbado.connect.example.pages

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput

/**
 * Page object for the Sign Up Screen.
 */
class SignUpScreen(composeTestRule: ComposeTestRule) : BaseScreen(composeTestRule) {
    
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
     * This is a suspend function as it mirrors the Swift async implementation.
     */
    suspend fun signUpWithValidData(email: String, phoneNumber: String, password: String): PostLoginScreen {
        composeTestRule.onNodeWithTag("SignUpEmailTextField").performTextInput(email)
        composeTestRule.onNodeWithTag("SignUpPhoneTextField").performTextInput(phoneNumber)
        composeTestRule.onNodeWithTag("SignUpPasswordTextField").performTextInput(password)
        composeTestRule.onNodeWithTag("SignUpSubmitButton").performClick()
        
        // Wait for navigation to PostLoginScreen
        val screen = PostLoginScreen(composeTestRule).also {
            it.visible(timeout = 5.0)
        }

        return screen
    }
} 