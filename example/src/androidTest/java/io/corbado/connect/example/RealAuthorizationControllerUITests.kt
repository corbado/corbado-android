package io.corbado.connect.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.corbado.connect.example.helpers.CredentialManagerHelper
import io.corbado.connect.example.pages.LoginScreen
import io.corbado.connect.example.pages.LoginStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * UI tests that use RealAuthorizationController and interact with system Credential Manager overlays.
 */
@RunWith(AndroidJUnit4::class)
class RealAuthorizationControllerUITests {
    
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()
    
    private lateinit var credentialManagerHelper: CredentialManagerHelper
    
    @Before
    fun setUp() {
        // Clear any existing state
        tearDown()
        
        // Use real authorization controller instead of virtual
        MainActivity.virtualAuthorizationController = null // This will use RealAuthorizationController
        MainActivity.isUITestMode = true
        
        credentialManagerHelper = CredentialManagerHelper()
        
        // Clear app state
        clearAppState()
    }
    
    @After
    fun tearDown() {
        // Clean up static fields
        MainActivity.virtualAuthorizationController = null
        MainActivity.isUITestMode = false
        MainActivity.controlServerURL = null
        
        composeTestRule.activity.resetCorbadoInstance()
        clearAppState()
    }
    
    /**
     * Test: Sign up → create real passkey → sign out → login with real passkey
     */
    @Test
    fun testRealPasskeyCreationAndAuthentication() = runTest {
        val initialScreen = startApp()
        val email = TestDataFactory.createEmail()

        initialScreen.cancelRealOverlayLogin()

        // Sign up with real passkey creation
        val signUpScreen = initialScreen.navigateToSignUp()
        val postLoginScreen = signUpScreen.signUpWithValidData(
            email = email,
            phoneNumber = TestDataFactory.phoneNumber,
            password = TestDataFactory.password
        )
        
        // Handle real passkey creation dialog
        val profileScreen = postLoginScreen.appendWithRealPasskey()
        
        // Verify passkey was created (this will show in your app's UI)
        assertEquals(1, profileScreen.countNumberOfPasskeys(), "Should have 1 passkey after creation")
        
        // Sign out and test authentication
        val loginScreen = profileScreen.signOut()
        assertTrue(loginScreen.awaitState(LoginStatus.PasskeyOneTap), "Should show one-tap login")
        
        // Authenticate with real passkey
        val profileScreen2 = loginScreen.loginWithRealPasskey()
        assertEquals(1, profileScreen2.countNumberOfPasskeys(), "Should still have 1 passkey after login")
    }
    
    /**
     * Test: Real passkey creation cancellation
     */
    @Test
    fun testRealPasskeyCreationCancellation() = runTest {
        val initialScreen = startApp()
        val email = TestDataFactory.createEmail()
        
        val signUpScreen = initialScreen.navigateToSignUp()
        val postLoginScreen = signUpScreen.signUpWithValidData(
            email = email,
            phoneNumber = TestDataFactory.phoneNumber,
            password = TestDataFactory.password
        )
        
        // Cancel passkey creation
        val totpSetupScreen = postLoginScreen.cancelPasskeyCreation()
        
        // Should proceed to TOTP setup since passkey was cancelled
        val (profileScreen, _) = totpSetupScreen.setupTOTP(AuthenticatorApp())
        assertEquals(0, profileScreen.countNumberOfPasskeys(), "Should have 0 passkeys after cancellation")
    }
    
    /**
     * Test: Real passkey authentication cancellation
     */
    @Test
    fun testRealPasskeyAuthenticationCancellation() = runTest {
        val initialScreen = startApp()
        val email = TestDataFactory.createEmail()
        
        // First create a passkey
        val signUpScreen = initialScreen.navigateToSignUp()
        val profileScreen = signUpScreen.signUpWithValidData(
            email = email,
            phoneNumber = TestDataFactory.phoneNumber,
            password = TestDataFactory.password
        ).appendWithRealPasskey()
        
        val loginScreen = profileScreen.signOut()
        assertTrue(loginScreen.awaitState(LoginStatus.PasskeyOneTap), "Should show one-tap login")
        
        // Cancel authentication
        loginScreen.cancelRealPasskeyAuthentication()
        assertTrue(loginScreen.awaitState(LoginStatus.PasskeyErrorSoft), "Should show soft error after cancellation")
        
        // Should be able to retry
        val profileScreen2 = loginScreen.retryAfterPasskeyError()
        assertEquals(1, profileScreen2.countNumberOfPasskeys(), "Should have 1 passkey after retry")
    }
    
    private fun clearAppState() {
        val context = composeTestRule.activity.applicationContext
        val sharedPrefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().apply()
        
        try {
            runBlocking {
                try {
                    com.amplifyframework.kotlin.core.Amplify.Auth.signOut()
                } catch (e: Exception) {
                    // Ignore errors during cleanup
                }
            }
        } catch (_: Exception) {
            // Amplify might not be initialized, ignore
        }
        
        composeTestRule.activity.resetCorbadoInstance()
    }
    
    private suspend fun startApp(): LoginScreen {
        val loginScreen = LoginScreen(composeTestRule)

        waitForCondition(timeout = 5000L) {
            if (loginScreen.visible(timeout = 0.1) == true) {
                true
            }

            false
        }

        return loginScreen
    }

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