package io.corbado.connect.example.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.corbado.connect.example.ui.Screen

/**
 * Page object for the Login Screen, mirroring the Swift LoginScreen implementation.
 */
class LoginScreen(composeTestRule: ComposeTestRule) : BaseScreen(composeTestRule) {
    
    override fun visible(timeout: Double): Boolean {
        return awaitVisible("LoginScreen", timeout) && awaitInvisible("LoadingIndicator", timeout)
    }
    
    /**
     * Navigate to the sign up screen by clicking the Sign Up button.
     */
    fun navigateToSignUp(): SignUpScreen {
        waitAndClick("SignUpButton")
        return SignUpScreen(composeTestRule).also { it.visible() }
    }

    /**
     * Perform login with email and password (fallback login).
     */
    fun loginWithIdentifierAndPasswordIdentifierFirst(email: String, password: String) {
        waitAndType("EmailTextField", email)
        waitAndClick("LoginWithPasskeyButton")
        waitAndType("PasswordTextField", password)
        waitAndClick("LoginButton")
    }

    /**
     * Perform login with email and password (fallback login).
     */
    fun loginWithIdentifierAndPassword(email: String, password: String) {
        waitAndType("EmailTextField", email)
        waitAndType("PasswordTextField", password)
        waitAndClick("LoginButton")
    }
    
    /**
     * Perform login with passkey using identifier (email).
     */
    fun loginWithIdentifier(email: String): ProfileScreen {
        waitAndType("EmailTextField", email)
        waitAndClick("LoginWithPasskeyButton")
        return ProfileScreen(composeTestRule).also { it.visible() }
    }
    
    /**
     * Perform one-tap login with passkey.
     */
    fun loginWithOneTap(): ProfileScreen {
        waitAndClick("LoginOneTapButton")
        return ProfileScreen(composeTestRule).also { it.visible() }
    }
    
    /**
     * Switch account (discard one-tap).
     */
    fun switchAccount() {
        waitAndClick("SwitchAccountButton")
    }
    
    /**
     * Wait for a specific login status to be displayed.
     * This is a simplified version - in a real implementation you'd check for specific UI states.
     */
    fun awaitState(loginStatus: LoginStatus, timeout: Double = 5.0): Boolean {
        return try {
            waitForCondition((timeout * 1000).toLong()) {
                when (loginStatus) {
                    LoginStatus.PasskeyOneTap -> {
                        try {
                            composeTestRule.onNodeWithTag("LoginOneTapButton").assertIsDisplayed()
                            true
                        } catch (e: AssertionError) {
                            false
                        }
                    }
                    LoginStatus.PasskeyTextField -> {
                        try {
                            composeTestRule.onNodeWithTag("LoginWithPasskeyButton").assertIsDisplayed()
                            true
                        } catch (e: AssertionError) {
                            false
                        }
                    }
                    LoginStatus.FallbackFirst -> {
                        try {
                            composeTestRule.onNodeWithTag("LoginButton").assertIsDisplayed()
                            true
                        } catch (e: AssertionError) {
                            false
                        }
                    }
                    LoginStatus.PasskeyErrorSoft -> {
                        try {
                            composeTestRule.onNodeWithTag("LoginErrorSoft").assertIsDisplayed()
                            true
                        } catch (e: AssertionError) {
                            false
                        }
                    }
                    LoginStatus.FallbackSecondTOTP -> {
                        try {
                            composeTestRule.onNodeWithTag("SubmitTOTPButton").assertIsDisplayed()
                            true
                        } catch (e: AssertionError) {
                            false
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Wait for a specific login status with optional error message.
     */
    fun awaitState(loginStatus: LoginStatus, errorMessage: String, timeout: Double = 5.0): Boolean {
        return awaitState(loginStatus, timeout) && try {
            composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Attempt login with identifier but expect it to fail.
     */
    fun loginWithIdentifierButNoSuccess(email: String) {
        composeTestRule.onNodeWithTag("EmailTextField").performTextInput(email)
        composeTestRule.onNodeWithTag("LoginWithPasskeyButton").performClick()
        // Don't navigate since we expect this to fail
    }
    
    /**
     * Try to login with one-tap and expect cancellation.
     */
    fun loginWithOneTapAndCancel() {
        composeTestRule.onNodeWithTag("LoginOneTapButton").performClick()
        // The virtual authenticator should handle the cancellation
    }
    
    /**
     * Continue after passkey error soft state.
     */
    fun loginOnPasskeyErrorSoft(): PostLoginScreen {
        waitAndClick("LoginErrorSoft")
        return PostLoginScreen(composeTestRule).also { it.visible() }
    }
    
    /**
     * Complete login with TOTP code.
     */
    fun completeLoginWithTOTP(code: String): PostLoginScreen {
        waitAndType("TOTPTextField", code)
        waitAndClick("SubmitTOTPButton")
        return PostLoginScreen(composeTestRule).also { it.visible() }
    }
    
    /**
     * Login with Conditional UI (CUI).
     * This triggers conditional UI login which uses passkey automatically.
     */
    fun loginWithCUI(): ProfileScreen {
        // Conditional UI is triggered automatically when the conditional UI challenge is present
        // The user doesn't need to explicitly click anything - it's handled by the system
        // We just wait for the login to complete and navigate to profile
        return ProfileScreen(composeTestRule).also { it.visible() }
    }
}

/**
 * Enum representing different login states.
 * This mirrors the LoginStatus enum from the main app.
 */
enum class LoginStatus {
    PasskeyOneTap,
    PasskeyTextField,
    FallbackFirst,
    FallbackSecondTOTP,
    PasskeyErrorSoft
} 