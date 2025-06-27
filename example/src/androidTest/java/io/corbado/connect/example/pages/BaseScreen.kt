package io.corbado.connect.example.pages

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Base class for all page objects, providing common functionality.
 */
abstract class BaseScreen(protected val composeTestRule: ComposeTestRule) {
    
    /**
     * Wait for a condition to be true with timeout.
     */
    protected fun waitForCondition(
        timeout: Long = 10000L,
        condition: () -> Boolean
    ) {
        runBlocking {
            withTimeout(timeout) {
                while (!condition()) {
                    delay(100)
                }
            }
        }
    }
    
    /**
     * Check if this screen is visible by looking for a unique identifier.
     */
    abstract fun visible(timeout: Double = 5.0): Boolean
    
    /**
     * Wait for screen to be visible.
     */
    protected fun awaitVisible(tag: String, timeout: Double = 5.0): Boolean {
        return try {
            waitForCondition((timeout * 1000).toLong()) {
                try {
                    composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
                    true
                } catch (_: AssertionError) {
                    false
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Wait for screen to be invisible.
     */
    protected fun awaitInvisible(tag: String, timeout: Double = 5.0): Boolean {
        return try {
            waitForCondition((timeout * 1000).toLong()) {
                try {
                    composeTestRule.onNodeWithTag(tag).assertIsNotDisplayed()
                    true
                } catch (_: AssertionError) {
                    false
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
    
    /**
     * Wait for a UI element with the given test tag to be displayed, then click it.
     * 
     * @param tag The test tag of the element to wait for and click
     * @param timeout Timeout in seconds (default: 5.0)
     * @return true if the element was found and clicked, false if timeout occurred
     */
    protected fun waitAndClick(tag: String, timeout: Double = 5.0): Boolean {
        return try {
            waitForCondition((timeout * 1000).toLong()) {
                try {
                    composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
                    true
                } catch (_: AssertionError) {
                    false
                }
            }
            // Element is now visible, click it
            composeTestRule.onNodeWithTag(tag).performClick()
            true
        } catch (_: Exception) {
            false
        }
    }
    
    protected fun waitAndGetText(tag: String, timeout: Double = 5.0): String? {
        return try {
            waitForCondition((timeout * 1000).toLong()) {
                try {
                    composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
                    true
                } catch (_: AssertionError) {
                    false
                }
            }

            // Element is now visible, extract its text content
            val semanticsNode = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode()
            semanticsNode.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
        } catch (_: Exception) {
            null
        }
    }
    
    /**
     * Wait for a UI element with the given test tag to be displayed, then input text into it.
     * 
     * @param tag The test tag of the text field to wait for and input text into
     * @param text The text to input
     * @param timeout Timeout in seconds (default: 5.0)
     * @return true if the element was found and text was input, false if timeout occurred
     */
    protected fun waitAndType(tag: String, text: String, timeout: Double = 5.0): Boolean {
        return try {
            waitForCondition((timeout * 1000).toLong()) {
                try {
                    composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
                    true
                } catch (_: AssertionError) {
                    false
                }
            }
            // Element is now visible, input text
            composeTestRule.onNodeWithTag(tag).performTextInput(text)
            true
        } catch (_: Exception) {
            false
        }
    }
}