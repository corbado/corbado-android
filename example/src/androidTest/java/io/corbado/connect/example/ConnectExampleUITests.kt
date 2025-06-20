package io.corbado.connect.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.corbado.connect.example.pages.LoginScreen
import io.corbado.connect.example.pages.LoginStatus.*
import io.corbado.simplecredentialmanager.AuthorizationError
import io.corbado.simplecredentialmanager.mocks.ControlServer
import io.corbado.simplecredentialmanager.mocks.VirtualAuthorizationController
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * UI tests for the Connect Example app, mirroring the Swift ConnectExampleUITests.
 */
@RunWith(AndroidJUnit4::class)
class ConnectExampleUITests {
    
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()
    
    private lateinit var controlServer: ControlServer
    private lateinit var virtualAuthenticator: VirtualAuthorizationController
    
    @Before
    fun setUpWithError() {
        // Start control server for error simulation
        controlServer = ControlServer()
        controlServer.start()
        
        // Create virtual authenticator connected to control server
        virtualAuthenticator = VirtualAuthorizationController(controlServer.baseURL)
        
        // Inject virtual authenticator into the main app
        MainActivity.virtualAuthorizationController = virtualAuthenticator
        
        // Set test mode flags
        MainActivity.isUITestMode = true
        MainActivity.controlServerURL = controlServer.baseURL
    }
    
    @After
    fun tearDownWithError() {
        controlServer.stop()
        
        // Clean up static fields
        MainActivity.virtualAuthorizationController = null
        MainActivity.isUITestMode = false
        MainActivity.controlServerURL = null
    }
    
    /**
     * Test: Sign up → append passkey (cancelled first, then successful) → verify passkey count → 
     * sign out → login with one-tap → verify passkey count → try append again (should show error)
     * 
     * This mirrors the Swift testAppendAfterSignUp() test.
     */
    @Test
    fun testAppendAfterSignUp() = runTest {
        val initialScreen = startApp()
        val email = TestDataFactory.createEmail()
        
        // Set control server to simulate cancellation on first passkey creation attempt
        controlServer.createError = AuthorizationError.Cancelled

        val signUpScreen = initialScreen.navigateToSignUp()
        val postLoginScreen = signUpScreen.signUpWithValidData(
            email = email,
            phoneNumber = TestDataFactory.phoneNumber,
            password = TestDataFactory.password
        )

        // Clear the error for the second attempt
        controlServer.createError = null
        val profileScreen = postLoginScreen.append(expectAutoAppend = false)
        
        val passkeyCount = profileScreen.countNumberOfPasskeys()
        assertEquals(1, passkeyCount, "Should have 1 passkey after successful append")
        
        val loginScreen = profileScreen.signOut()
        assertTrue(loginScreen.awaitState(PasskeyOneTap), "Should show one-tap login after passkey creation")
        val profileScreen2 = loginScreen.loginWithOneTap()
        
        profileScreen2.appendPasskey()
        assertEquals("You already have a passkey that can be used on this device.", profileScreen2.getErrorMessage())
    }
    
    /**
     * Start the app and return the initial login screen.
     * This mirrors the Swift startApp() function.
     */
    private suspend fun startApp(
        filteredByGradualRollout: Boolean = false,
        filteredByMissingDeviceSupport: Boolean = false
    ): LoginScreen {
        // The app should already be started by the compose test rule
        // Create the login screen and wait for it to be visible
        val loginScreen = LoginScreen(composeTestRule)
        
        // Wait up to 5 seconds for the app to initialize and show the login screen
        waitForCondition(timeout = 5000L) {
            loginScreen.visible(timeout = 0.1) // Quick check without additional timeout
        }
        
        return loginScreen
    }
    
    /**
     * Wait for a condition to be true with timeout.
     */
    private suspend fun waitForCondition(timeout: Long = 10000L, condition: () -> Boolean) {
        val startTime = System.currentTimeMillis()
        while (!condition() && (System.currentTimeMillis() - startTime) < timeout) {
            kotlinx.coroutines.delay(100)
        }
        if (!condition()) {
            throw AssertionError("Condition was not met within ${timeout}ms")
        }
    }
} 