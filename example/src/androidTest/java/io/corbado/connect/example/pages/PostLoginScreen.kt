package io.corbado.connect.example.pages

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick

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
        if (expectAutoAppend) {
            // In auto-append scenario, passkey creation happens automatically
            // We just need to wait for it to complete and navigate
            waitForCondition(10000) {
                try {
                    // Check if we can see the create passkey button (meaning auto-append failed)
                    composeTestRule.onNodeWithTag("CreatePasskeyButton").assertExists()
                    false // Auto-append failed, manual action needed
                } catch (e: Exception) {
                    // Button not visible, auto-append might have succeeded
                    true
                }
            }
        }
        
        // Try to create passkey if button is available
        try {
            composeTestRule.onNodeWithTag("CreatePasskeyButton").performClick()
        } catch (e: Exception) {
            // Button might not be available if auto-append succeeded
        }

        waitAndClick("ContinueButton")
        
        return ProfileScreen(composeTestRule).also {
            it.visible()
        }
    }
    
    /**
     * Skip passkey creation after sign up.
     */
    fun skipAfterSignUp(): TotpSetupScreen {
        composeTestRule.onNodeWithTag("SkipPasskeyButton").performClick()
        return TotpSetupScreen(composeTestRule)
    }
    
    /**
     * Auto-skip passkey creation (for gradual rollout scenarios).
     */
    fun autoSkipAfterSignUp(): TotpSetupScreen {
        // In auto-skip scenarios, the navigation happens automatically
        // We just return the next screen
        return TotpSetupScreen(composeTestRule)
    }
    
    /**
     * Auto-skip to profile (when passkey creation is not required).
     */
    fun autoSkip(): ProfileScreen {
        return ProfileScreen(composeTestRule)
    }
    
    /**
     * Skip to profile screen.
     */
    fun skip(): ProfileScreen {
        composeTestRule.onNodeWithTag("SkipPasskeyButton").performClick()
        return ProfileScreen(composeTestRule)
    }
} 