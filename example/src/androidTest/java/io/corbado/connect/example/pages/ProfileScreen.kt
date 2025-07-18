package com.corbado.connect.example.pages

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.text
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import com.corbado.connect.example.hasTestTagPrefix

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
            val passkeyNodes = composeTestRule.onAllNodes(hasTestTagPrefix("PasskeyListItem-"))
            passkeyNodes.fetchSemanticsNodes().size
        } catch (e: AssertionError) {
            0
        }
    }
    
    /**
     * Get the list of passkey IDs.
     */
    fun getPasskeyIds(): List<String> {
        val prefix = "PasskeyListItem-"

        return composeTestRule.onAllNodes(hasTestTagPrefix(prefix))
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag)?.removePrefix(prefix) }
    }
    
    /**
     * Append/create a new passkey.<^x
     */
    fun appendPasskey() {
        waitAndClick("CreatePasskeyButton")
    }
    
    /**
     * Delete a passkey by ID.
     */
    fun deletePasskey(passkeyId: String, complete: Boolean = false) {
        waitAndClick("DeletePasskeyButton-$passkeyId")

        if (complete) {
            waitAndClick("PasskeyListDeleteButtonConfirm")
        } else {
            waitAndClick("PasskeyListDeleteButtonCancel")
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
            composeTestRule.onNodeWithTag("CreatePasskeyButton").assertExists()
            true
        } catch (e: AssertionError) {
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
        return waitAndGetText("ListMessage")
    }
    
    /**
     * Reload the page/refresh data.
     */
    fun reloadPage(): ProfileScreen {
        waitAndClick("ReloadButton")

        return ProfileScreen(composeTestRule).also {
            it.visible()
        }
    }
} 