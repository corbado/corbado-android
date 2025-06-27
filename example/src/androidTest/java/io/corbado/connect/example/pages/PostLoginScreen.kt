package io.corbado.connect.example.pages

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.corbado.connect.example.helpers.CredentialManagerHelper

/**
 * Page object for the Post Login Screen (passkey creation screen).
 */
class PostLoginScreen(composeTestRule: ComposeTestRule) : BaseScreen(composeTestRule) {
    
    override fun visible(timeout: Double): Boolean {
        return awaitVisible("PostLoginScreen", timeout) && awaitInvisible("LoadingIndicator", timeout)
    }
    
    /**
     * Attempt to append/create a passkey.
     * @param expectAutoAppend Whether to expect automatic passkey append after signup
     */
    fun append(expectAutoAppend: Boolean): ProfileScreen {
        if (!expectAutoAppend) {
            waitAndClick("CreatePasskeyButton")
        }

        waitAndClick("ContinueButton")
        
        return ProfileScreen(composeTestRule).also { it.visible() }
    }
    
    /**
     * Skip passkey creation after sign up.
     */
    fun skipAfterSignUp(): TotpSetupScreen {
        composeTestRule.onNodeWithTag("SkipPasskeyButton").performClick()
        return TotpSetupScreen(composeTestRule).also { it.visible() }
    }
    
    /**
     * Auto-skip passkey creation (for gradual rollout scenarios).
     */
    fun autoSkipAfterSignUp(): TotpSetupScreen {
        // In auto-skip scenarios, the navigation happens automatically
        // We just return the next screen
        return TotpSetupScreen(composeTestRule).also { it.visible() }
    }
    
    /**
     * Auto-skip to profile (when passkey creation is not required).
     */
    fun autoSkip(): ProfileScreen {
        return ProfileScreen(composeTestRule).also { it.visible() }
    }
    
    /**
     * Skip to profile screen.
     */
    fun skip(): ProfileScreen {
        composeTestRule.onNodeWithTag("SkipPasskeyButton").performClick()
        return ProfileScreen(composeTestRule).also { it.visible() }
    }
    
    /**
     * Append/create a passkey using real Credential Manager.
     */
    suspend fun appendWithRealPasskey(): ProfileScreen {
        val credentialManagerHelper = CredentialManagerHelper()
        
        waitAndClick("CreatePasskeyButton")
        
        // Wait for and handle the credential manager overlay
        if (credentialManagerHelper.waitForCredentialManagerOverlay()) {
            credentialManagerHelper.handlePasskeyCreationDialog("create")
        }
        
        waitAndClick("ContinueButton")
        return ProfileScreen(composeTestRule).also { it.visible() }
    }

    /**
     * Cancel passkey creation using real Credential Manager.
     */
    suspend fun cancelPasskeyCreation(): TotpSetupScreen {
        val credentialManagerHelper = CredentialManagerHelper()
        
        waitAndClick("CreatePasskeyButton")
        
        // Wait for and cancel the credential manager overlay
        if (credentialManagerHelper.waitForCredentialManagerOverlay()) {
            credentialManagerHelper.handlePasskeyCreationDialog("cancel")
        }
        
        // Should navigate to TOTP setup after cancellation
        return TotpSetupScreen(composeTestRule).also { it.visible() }
    }
} 