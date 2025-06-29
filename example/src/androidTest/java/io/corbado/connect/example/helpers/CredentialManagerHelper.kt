package com.corbado.connect.example.helpers

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import kotlinx.coroutines.delay

/**
 * Helper class for interacting with Android Credential Manager system overlays.
 */
class CredentialManagerHelper {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val timeout = 10000L // 10 seconds

    /**
     * Wait for and interact with passkey creation dialog.
     * @param action The action to perform: "create", "cancel", or "allow"
     */
    suspend fun handlePasskeyCreationDialog(action: String = "create"): Boolean {
        return when (action) {
            "create", "allow" -> clickPasskeyDialogButton("Allow", "Continue", "Create", "Save", "Yes")
            "cancel" -> clickPasskeyDialogButton("Cancel", "Not now", "No thanks")
            else -> false
        }
    }

    /**
     * Wait for and interact with passkey authentication dialog.
     * @param action The action to perform: "authenticate", "cancel", or "use_different"
     */
    suspend fun handlePasskeyAuthenticationDialog(action: String = "authenticate"): Boolean {
        return when (action) {
            "authenticate" -> {
                // First try to find and click on a passkey if multiple are shown
                selectPasskeyIfAvailable()
                // Then confirm the authentication
                clickPasskeyDialogButton("Continue", "Use passkey", "Confirm", "OK", "Yes")
            }
            "cancel" -> clickPasskeyDialogButton("Cancel", "Not now", "No")
            "use_different" -> clickPasskeyDialogButton("Use different account", "Switch account")
            else -> false
        }
    }

    /**
     * Wait for credential manager overlay to appear.
     */
    suspend fun waitForCredentialManagerOverlay(): Boolean {
        return waitForAnyText(
            "Sign-in options"
        )
    }

    /**
     * Check if credential manager overlay is currently visible.
     */
    fun isCredentialManagerOverlayVisible(): Boolean {
        return device.findObject(By.textContains("passkey")) != null ||
               device.findObject(By.textContains("Passkey")) != null ||
               device.findObject(By.pkg("com.google.android.gms")) != null
    }

    /**
     * Dismiss any credential manager overlays by pressing back or cancel.
     */
    suspend fun dismissCredentialManagerOverlay(): Boolean {
        return if (isCredentialManagerOverlayVisible()) {
            // Try cancel button first
            if (clickPasskeyDialogButton("Cancel", "Not now", "No")) {
                true
            } else {
                // Fall back to back button
                device.pressBack()
                delay(500)
                !isCredentialManagerOverlayVisible()
            }
        } else {
            true
        }
    }

    /**
     * Select a specific passkey from the list if multiple are available.
     */
    private suspend fun selectPasskeyIfAvailable(): Boolean {
        // Look for passkey items in the list
        val passkeyItems = device.findObjects(By.textContains("passkey"))
        if (passkeyItems.isNotEmpty()) {
            passkeyItems.first().click()
            delay(500)
            return true
        }
        return false
    }

    /**
     * Click any of the provided button texts that are found.
     */
    private suspend fun clickPasskeyDialogButton(vararg buttonTexts: String): Boolean {
        for (text in buttonTexts) {
            val button = device.findObject(By.text(text).clickable(true))
            if (button != null) {
                button.click()
                delay(1000) // Wait for dialog to process
                return true
            }
        }
        return false
    }

    /**
     * Wait for any of the provided texts to appear.
     */
    private suspend fun waitForAnyText(vararg texts: String): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeout) {
            for (text in texts) {
                if (device.findObject(By.textContains(text)) != null) {
                    return true
                }
            }
            delay(500)
        }
        return false
    }

    /**
     * Wait for credential manager overlay to disappear.
     */
    suspend fun waitForCredentialManagerOverlayToDisappear(): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeout) {
            if (!isCredentialManagerOverlayVisible()) {
                return true
            }
            delay(500)
        }
        return false
    }
} 