package io.corbado.connect.example.pages

import androidx.compose.ui.test.junit4.ComposeTestRule
import io.corbado.connect.example.AuthenticatorApp

/**
 * Page object for the TOTP Setup Screen.
 */
class TotpSetupScreen(composeTestRule: ComposeTestRule) : BaseScreen(composeTestRule) {
    
    override fun visible(timeout: Double): Boolean {
        // Check if the TOTP setup screen is visible by looking for TOTP-related elements
        return awaitVisible("TotpSetupScreen", timeout) && awaitInvisible("LoadingIndicator", timeout)
    }
    
    /**
     * Setup TOTP and return profile screen with setup key.
     * This simulates the TOTP setup flow by extracting the setup key and completing the setup.
     */
    suspend fun setupTOTP(authenticatorApp: AuthenticatorApp): Pair<ProfileScreen, String> {
        // Extract the setup key from the UI (in real implementation this would be from QR or text)
        val secret = waitAndGetText("SetupKeyText") ?:
            throw IllegalStateException("Setup key not found in TOTP setup screen")
        
        // Register the setup key with the authenticator app and get the first TOTP code
        val totpCode = authenticatorApp.addBySecret(secret)
            ?: throw IllegalStateException("Failed to register setup key with authenticator app")
        
        // Enter the TOTP code and complete setup
        waitAndType("TotpCodeTextField", totpCode)
        waitAndClick("CompleteButton")
        
        val profileScreen = ProfileScreen(composeTestRule).also {
            it.visible()
        }
        
        return Pair(profileScreen, secret)
    }
} 