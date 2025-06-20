package io.corbado.connect.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.corbado.connect.example.pages.LoginScreen
import io.corbado.connect.example.pages.LoginStatus.*
import io.corbado.simplecredentialmanager.AuthorizationError
import io.corbado.simplecredentialmanager.mocks.ControlServer
import io.corbado.simplecredentialmanager.mocks.VirtualAuthorizationController
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
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
        // Clear any existing state first
        tearDownWithError()

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

        // Reset control server state
        controlServer.createError = null
        controlServer.authorizeError = null
    }
    
    @After
    fun tearDownWithError() {
        // Stop control server if it exists
        if (::controlServer.isInitialized) {
            controlServer.stop()
        }
        
        // Clean up ALL static fields in MainActivity
        MainActivity.virtualAuthorizationController = null
        MainActivity.isUITestMode = false
        MainActivity.controlServerURL = null

        // Clear any app-level state (you might need to add more here based on your app)
        clearAppState()
    }

    /**
     * Clear any persistent app state that might affect tests.
     * Add more cleanup here as needed based on your app's state management.
     */
    private fun clearAppState() {
        // Clear shared preferences
        val context = composeTestRule.activity.applicationContext
        val sharedPrefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().apply()

        // Clear any authentication state
        try {
            // If you're using Amplify Auth, clear its state
            runBlocking {
                try {
                    com.amplifyframework.kotlin.core.Amplify.Auth.signOut()
                } catch (e: Exception) {
                    // Ignore errors during cleanup
                }
            }
        } catch (e: Exception) {
            // Amplify might not be initialized, ignore
        }

        // Add any other state cleanup here (databases, caches, etc.)
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
     * Test: Sign up → skip passkey creation → setup TOTP → sign out → login with email/password → 
     * enter TOTP → skip again → create passkey → sign out → login with one-tap → delete passkey → 
     * sign out → login again with email/password and TOTP
     * 
     * This mirrors the Swift testAppendAfterSignUpSkipped() test.
     */
    @Test
    fun testAppendAfterSignUpSkipped() = runTest {
        val initialScreen = startApp()
        val authenticatorApp = AuthenticatorApp()
        val email = TestDataFactory.createEmail()

        controlServer.createError = AuthorizationError.Cancelled

        val signUpScreen = initialScreen.navigateToSignUp()
        val postLoginScreen = signUpScreen.signUpWithValidData(
            email = email,
            phoneNumber = TestDataFactory.phoneNumber,
            password = TestDataFactory.password
        )
        val totpSetupScreen = postLoginScreen.skipAfterSignUp()

        val (profileScreen, setupKey) = totpSetupScreen.setupTOTP(authenticatorApp)

        assertEquals(0, profileScreen.countNumberOfPasskeys(), "Should have 0 passkeys after skipping")

        val loginScreen = profileScreen.signOut()
        assertTrue(loginScreen.awaitState(PasskeyTextField), "Should show text field login")
        loginScreen.loginWithIdentifierAndPasswordIdentifierFirst(email, TestDataFactory.password)
        assertTrue(loginScreen.awaitState(FallbackSecondTOTP), "Should show TOTP prompt")
        val code = authenticatorApp.getCode(setupKey)
        val profileScreen2 = loginScreen.completeLoginWithTOTP(code!!).skip()

        controlServer.createError = null
        assertTrue(profileScreen2.countNumberOfPasskeys() == 0, "Should still have 0 passkeys")
        profileScreen2.appendPasskey()
        waitForCondition { profileScreen2.countNumberOfPasskeys() == 1 }
        val passkeyId = profileScreen2.getPasskeyIds()[0]
        profileScreen2.signOut()

        assertTrue(loginScreen.awaitState(PasskeyOneTap), "Should show one-tap login")
        val profileScreen3 = loginScreen.loginWithOneTap()

        controlServer.authorizeError = AuthorizationError.Cancelled
        waitForCondition { profileScreen3.countNumberOfPasskeys() == 1 }
        profileScreen3.deletePasskey(passkeyId, complete = true)
        waitForCondition { profileScreen3.countNumberOfPasskeys() == 0 }
        val loginScreen2 = profileScreen3.signOut()

        assertTrue(loginScreen2.awaitState(PasskeyTextField), "Should show text field login")
        loginScreen2.loginWithIdentifierAndPasswordIdentifierFirst(email, TestDataFactory.password)
        assertTrue(loginScreen2.awaitState(FallbackSecondTOTP), "Should show TOTP prompt")
        val code2 = authenticatorApp.getCode(setupKey)
        loginScreen2.completeLoginWithTOTP(code2!!).autoSkip()
    }

    /**
     * Test: Sign up → create passkey → sign out → switch account → login with identifier
     * 
     * This mirrors the Swift testLoginWithTextField() test.
     */
    @Test
    fun testLoginWithTextField() = runTest {
        val initialScreen = startApp()
        val email = TestDataFactory.createEmail()
        
        val signUpScreen = initialScreen.navigateToSignUp()
        val profileScreen = signUpScreen.signUpWithValidData(
            email = email,
            phoneNumber = TestDataFactory.phoneNumber,
            password = TestDataFactory.password
        ).append(expectAutoAppend = true)
        val loginScreen = profileScreen.signOut()
        
        loginScreen.switchAccount()
        val profileScreen2 = loginScreen.loginWithIdentifier(email)
        waitForCondition { profileScreen2.countNumberOfPasskeys() == 1 }
    }
    
    /**
     * Test: Sign up → create passkey → sign out → login with one-tap (cancel first, then succeed)
     * 
     * This mirrors the Swift testLoginWithOneTap() test.
     */
    @Test
    fun testLoginWithOneTap() = runTest {
        val initialScreen = startApp()
        val email = TestDataFactory.createEmail()
        
        val signUpScreen = initialScreen.navigateToSignUp()
        val loginScreen = signUpScreen.signUpWithValidData(
            email = email,
            phoneNumber = TestDataFactory.phoneNumber,
            password = TestDataFactory.password
        ).append(expectAutoAppend = true).signOut()
        assertTrue(loginScreen.awaitState(PasskeyOneTap), "Should show one-tap login")
        
        controlServer.authorizeError = AuthorizationError.Cancelled
        loginScreen.loginWithOneTapAndCancel()
        assertTrue(loginScreen.awaitState(PasskeyErrorSoft), "Should show soft error after cancel")
        
        controlServer.authorizeError = null
        
        val profileScreen2 = loginScreen.loginOnPasskeyErrorSoft().autoSkip()
        waitForCondition { profileScreen2.countNumberOfPasskeys() == 1 }
    }
    
    /**
     * Test: Sign up → create passkey → sign out → switch account → navigate to signup then back → 
     * login with overlay
     * 
     * This mirrors the Swift testLoginWithConditionalUI() test.
     */
    @Test
    fun testLoginWithOverlay() = runTest {
        val initialScreen = startApp()
        val email = TestDataFactory.createEmail()
        
        // First create an account with passkey
        val signUpScreen = initialScreen.navigateToSignUp()

        val loginScreen = signUpScreen.signUpWithValidData(
            email = email,
            phoneNumber = TestDataFactory.phoneNumber,
            password = TestDataFactory.password
        ).append(expectAutoAppend = true).signOut()
        
        // To get CUI offered, we need to remove OneTap first
        loginScreen.switchAccount()
        val loginScreen2 = loginScreen.navigateToSignUp().navigateToLogin()
        
        val profileScreen = loginScreen2.loginWithCUI()
        waitForCondition { profileScreen.countNumberOfPasskeys() == 1 }
    }

    @Test
    fun testLoginErrorStates() = runTest {
        val initialScreen = startApp()
        val nonExistingEmail = "integration-test+0000000000@corbado.com"

        assertTrue(initialScreen.awaitState(PasskeyTextField), "Login must init in passkey text field")
        initialScreen.loginWithIdentifierButNoSuccess(nonExistingEmail)

        assertTrue(initialScreen.awaitState(PasskeyTextField, errorMessage = "There is no account registered to that email address."), "Should show error for non-existing email")
    }

    @Test
    fun testLoginErrorStatesGradualRollout() = runTest {
        val initialScreen = startApp(allowedByGradualRollout = false)
        val authenticatorApp = AuthenticatorApp()
        val email = TestDataFactory.createEmail()

        assertTrue(initialScreen.awaitState(FallbackFirst), "Should show fallback login immediately in gradual rollout")
        val signUpScreen = initialScreen.navigateToSignUp()
        // append must be skipped automatically
        val tOTPSetupScreen = signUpScreen.signUpWithValidData(
            email = email,
            phoneNumber = TestDataFactory.phoneNumber,
            password = TestDataFactory.password
        ).autoSkipAfterSignUp()

        val (profileScreen, setupKey) = tOTPSetupScreen.setupTOTP(authenticatorApp)
        assertFalse(profileScreen.passkeyAppendPossible(), "Passkey append button must be hidden in gradual rollout")

        val loginScreen2 = profileScreen.signOut()
        loginScreen2.loginWithIdentifierAndPassword(email, TestDataFactory.password)
        val code = authenticatorApp.getCode(setupKey)
        loginScreen2.completeLoginWithTOTP(code!!).autoSkip()
    }

    @Test
    fun testLoginErrorStatesPasskeyDeletedClientSide() = runTest {
        val initialScreen = startApp()
        val email = TestDataFactory.createEmail()

        val profileScreen = initialScreen.navigateToSignUp()
            .signUpWithValidData(email = email, phoneNumber = TestDataFactory.phoneNumber, password = TestDataFactory.password)
            .append(expectAutoAppend = true)

        val passkeyId = profileScreen.getPasskeyIds()[0]
        profileScreen.deletePasskey(passkeyId, complete = true)
        waitForCondition { profileScreen.countNumberOfPasskeys() == 0 }

        val loginScreen = profileScreen.signOut()
        assertTrue(loginScreen.awaitState(FallbackFirst, errorMessage = "You previously deleted this passkey. Use your password to log in instead."))
    }

    /*
    func testLoginErrorStatesNetworkBlocking() async throws {
        let initialScreen = try startApp()
        let email = TestDataFactory.createEmail()

        let profileScreen = try await initialScreen
            .navigateToSignUp()
            .signUpWithValidData(email: email, phoneNumber: TestDataFactory.phoneNumber, password: TestDataFactory.password)
            .append(expectAutoAppend: true)

        // block login-init
        initialScreen.block(blockedUrl: "/connect/login/init")
        let loginScreen = profileScreen.signOut()
        XCTAssertTrue(loginScreen.awaitState(loginStatus: .fallbackFirst), "Login must init in fallback automatically")

        // block login-start
        initialScreen.block(blockedUrl: "/connect/login/start")
        loginScreen.navigateToSignUp().navigateToLogin()
        XCTAssertTrue(initialScreen.awaitState(loginStatus: .passkeyOneTap))
        loginScreen.switchAccount()
        loginScreen.loginWithIdentifierButNoSuccess(email: email)
        XCTAssertTrue(initialScreen.awaitState(loginStatus: .fallbackFirst))

        loginScreen.navigateToSignUp().navigateToLogin()

        // block login-finish
        initialScreen.block(blockedUrl: "/connect/login/finish")
        loginScreen.loginWithIdentifierButNoSuccess(email: email)
        XCTAssertTrue(initialScreen.awaitState(loginStatus: .fallbackFirst, errorMessage: "Passkey error. Use password to log in.", timeout: 5.0))
        initialScreen.unblock()

        // successfull login
        loginScreen.navigateToSignUp().navigateToLogin()
        let profileScreen2 = loginScreen.loginWithIdentifier(email: email).autoSkip()
        XCTAssertTrue(profileScreen2.visible(timeout: 10.0))
    }
     */

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

    /**
     * Start the app and return the initial login screen.
     * This mirrors the Swift startApp() function.
     */
    private suspend fun startApp(
        allowedByGradualRollout: Boolean = true
    ): LoginScreen {
        composeTestRule.activity.setGradualRollout(allowedByGradualRollout)

        // The app should already be started by the compose test rule
        // Create the login screen and wait for it to be visible
        val loginScreen = LoginScreen(composeTestRule)

        // Wait up to 5 seconds for the app to initialize and show the login screen
        waitForCondition(timeout = 5000L) {
            loginScreen.visible(timeout = 0.1) // Quick check without additional timeout
        }

        return loginScreen
    }
} 