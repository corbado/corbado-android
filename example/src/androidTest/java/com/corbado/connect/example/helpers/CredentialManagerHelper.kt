package com.corbado.connect.example.helpers

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.delay

/**
 * Helper class for interacting with Android Credential Manager system overlays.
 */
class CredentialManagerHelper {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    suspend fun handlePasswordSaveDialog(timeoutMs: Long = 3000) {
        val dialogAppeared = waitForPasswordSaveDialog(timeoutMs)
        if (!dialogAppeared) {
            return
        }

        device.pressBack()
    }

    /**
     * Wait for password save dialog to appear.
     */
    private suspend fun waitForPasswordSaveDialog(timeoutMs: Long = 3000): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            // Look for common password save dialog indicators
            val indicators = listOf(
                "Save password",
            )

            for (text in indicators) {
                if (device.findObject(By.textContains(text)) != null) {
                    return true
                }
            }

            delay(300)
        }
        return false
    }
}