package io.corbado.connect.example.pages

import androidx.compose.ui.semantics.text
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals

/**
 * Page object for the Profile Screen.
 */
class ProfileScreen(composeTestRule: ComposeTestRule) : BaseScreen(composeTestRule) {
    
    override fun visible(timeout: Double): Boolean {
        return awaitVisible("ProfileScreen", timeout) && awaitInvisible("LoadingIndicator", timeout)
    }
    
    /**
     * Count the number of passkeys displayed.
     */
    fun countNumberOfPasskeys(): Int {
        return try {
            // This is a simplified implementation - in reality you'd need to count
            // the actual passkey items in the list
            // For now, we'll use a placeholder approach
            val passkeyNodes = composeTestRule.onAllNodesWithText("Passkey", substring = true)
            passkeyNodes.fetchSemanticsNodes().size
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Get the list of passkey IDs.
     */
    fun getPasskeyIds(): List<String> {
        // This is a placeholder implementation
        // In reality, you'd need to extract the actual passkey IDs from the UI
        return emptyList()
    }
    
    /**
     * Append/create a new passkey.
     */
    fun appendPasskey() {
        waitAndClick("CreatePasskeyButton")
    }
    
    /**
     * Delete a passkey by ID.
     */
    fun deletePasskey(passkeyId: String, complete: Boolean = false) {
        // This is a placeholder implementation
        // In reality, you'd need to find the specific passkey in the list and delete it
        // For now, we'll simulate the action
        if (complete) {
            // Simulate confirming the deletion
        }
    }
    
    /**
     * Sign out from the profile.
     */
    fun signOut(): LoginScreen {
        waitAndClick("LogoutButton", timeout = 0.1)
        return LoginScreen(composeTestRule).also {
            it.visible()
        }
    }
    
    /**
     * Check if passkey append is possible (button is visible).
     */
    fun passkeyAppendPossible(): Boolean {
        return try {
            composeTestRule.onNodeWithText("Create passkey").assertExists()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get error message if displayed.
     */
    fun getErrorMessage(): String? {
        return waitAndGetText("ErrorMessage")
    }
    
    /**
     * Get list message if displayed.
     */
    fun getListMessage(): String? {
        return try {
            composeTestRule.onNodeWithText("We were unable to show your list of passkeys due to an error. Try again later.").assertExists()
            "We were unable to show your list of passkeys due to an error. Try again later."
        } catch (e: Exception) {
            try {
                composeTestRule.onNodeWithText("There is currently no passkey saved for this account.").assertExists()
                "There is currently no passkey saved for this account."
            } catch (e2: Exception) {
                null
            }
        }
    }
    
    /**
     * Reload the page/refresh data.
     */
    fun reloadPage() {
        composeTestRule.onNodeWithText("Reload").performClick()
    }
} 