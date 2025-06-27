package io.corbado.connect.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.corbado.connect.example.pages.LoginScreen
import io.corbado.connect.example.pages.LoginStatus.FallbackFirst
import io.corbado.connect.example.pages.LoginStatus.FallbackSecondTOTP
import io.corbado.connect.example.pages.LoginStatus.PasskeyErrorSoft
import io.corbado.connect.example.pages.LoginStatus.PasskeyOneTap
import io.corbado.connect.example.pages.LoginStatus.PasskeyTextField
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

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

        // Default to cancelled for empty allow credentials (otherwise textField logins would be triggered instantly)
        controlServer.authorizeWithEmptyAllowCredentials = AuthorizationError.Cancelled
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

        composeTestRule.activity.resetCorbadoInstance()

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
    fun testAppendAfterSignUpSkipped() = runTest(timeout = 2.minutes) {
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

        controlServer.authorizeWithEmptyAllowCredentials = null

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

        assertTrue(initialScreen.awaitState(PasskeyTextField, errorMessage = "No account matches that email."), "Should show error for non-existing email")
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

        controlServer.authorizeWithEmptyAllowCredentials = null

        val profileScreen = initialScreen.navigateToSignUp()
            .signUpWithValidData(email = email, phoneNumber = TestDataFactory.phoneNumber, password = TestDataFactory.password)
            .append(expectAutoAppend = true)

        val passkeyId = profileScreen.getPasskeyIds()[0]
        profileScreen.deletePasskey(passkeyId, complete = true)
        waitForCondition { profileScreen.countNumberOfPasskeys() == 0 }

        val loginScreen = profileScreen.signOut()
        assertTrue(loginScreen.awaitState(FallbackFirst, errorMessage = "You previously deleted this passkey. Use your password to log in instead."))
    }

    @Test()
    fun testLoginErrorStatesNetworkBlocking() = runTest(timeout = 2.minutes) {
        val initialScreen = startApp()
        val email = TestDataFactory.createEmail()

        val profileScreen = initialScreen.navigateToSignUp()
            .signUpWithValidData(email = email, phoneNumber = TestDataFactory.phoneNumber, password = TestDataFactory.password)
            .append(expectAutoAppend = true)

        // Block login-init
        blockCorbadoEndpoint(CorbadoEndpoint.LoginInit)
        val loginScreen = profileScreen.signOut()
        assertTrue(loginScreen.awaitState(FallbackFirst), "Login must init in fallback automatically")

        // Block login-start
        blockCorbadoEndpoint(CorbadoEndpoint.LoginStart)
        loginScreen.navigateToSignUp().navigateToLogin()
        assertTrue(initialScreen.awaitState(PasskeyOneTap))
        loginScreen.switchAccount()
        loginScreen.loginWithIdentifierButNoSuccess(email)
        assertTrue(initialScreen.awaitState(FallbackFirst))

        loginScreen.navigateToSignUp().navigateToLogin()

        // Block login-finish
        blockCorbadoEndpoint(CorbadoEndpoint.LoginFinish)
        loginScreen.loginWithIdentifierButNoSuccess(email)
        assertTrue(initialScreen.awaitState(FallbackFirst, errorMessage = "Passkey error. Use password to log in.", timeout = 5.0))
        unblockCorbadoEndpoint()

        // Successful login
        loginScreen.navigateToSignUp().navigateToLogin()
        loginScreen.loginWithIdentifier(email)
    }

    @Test()
    fun testLoginErrorStatesPasskeyAppendBlocked() = runTest(timeout = 2.minutes) {
        val initialScreen = startApp()
        val authenticatorApp = AuthenticatorApp()
        val email = TestDataFactory.createEmail()

        // Block append-init
        blockCorbadoEndpoint(CorbadoEndpoint.AppendInit)

        val signUpScreen = initialScreen.navigateToSignUp()
        val postLoginScreen = signUpScreen.signUpWithValidData(
            email = email,
            phoneNumber = TestDataFactory.phoneNumber,
            password = TestDataFactory.password
        )

        val (profileScreen, setupKey) = postLoginScreen.autoSkipAfterSignUp().setupTOTP(authenticatorApp = authenticatorApp)
        val loginScreen2 = profileScreen.signOut()

        loginScreen2.loginWithIdentifierAndPasswordIdentifierFirst(email, TestDataFactory.password)
        val code = authenticatorApp.getCode(setupKey)

        // block append-start
        blockCorbadoEndpoint(CorbadoEndpoint.AppendStart)
        val profileScreen2 = loginScreen2.completeLoginWithTOTP(code!!).autoSkip()

        val loginScreen3 = profileScreen2.signOut()
        loginScreen3.loginWithIdentifierAndPasswordIdentifierFirst(email, TestDataFactory.password)
        val code2 = authenticatorApp.getCode(setupKey)

        // block append-finish
        blockCorbadoEndpoint(CorbadoEndpoint.AppendFinish)
        loginScreen3.completeLoginWithTOTP(code2!!).autoSkip()
    }

    fun testManageErrorStatesNetworkBlocking() = runTest {
        val initialScreen = startApp()
        val email = TestDataFactory.createEmail()

        val profileScreen = initialScreen.navigateToSignUp()
            .signUpWithValidData(email = email, phoneNumber = TestDataFactory.phoneNumber, password = TestDataFactory.password)
            .append(expectAutoAppend = true)

        waitForCondition { profileScreen.countNumberOfPasskeys() == 1 }

        // Block manage-init
        blockCorbadoEndpoint(CorbadoEndpoint.ManageInit)
        val profileScreen2 = profileScreen.reloadPage()
        assertEquals(profileScreen2.getErrorMessage(), "Unable to access passkeys. Check your connection and try again.")
        assertEquals(profileScreen2.getListMessage(), "We were unable to show your list of passkeys due to an error. Try again later.")

        // Block manage-list
        blockCorbadoEndpoint(CorbadoEndpoint.ManageList)
        val profileScreen3 = profileScreen2.reloadPage()
        assertEquals(profileScreen3.getErrorMessage(), "Unable to access passkeys. Check your connection and try again.")
        assertEquals(profileScreen3.getListMessage(), "We were unable to show your list of passkeys due to an error. Try again later.")

        // Block append-start
        blockCorbadoEndpoint(CorbadoEndpoint.AppendStart)
        val profileScreen4 = profileScreen3.reloadPage()
        assertEquals(profileScreen4.countNumberOfPasskeys(), 1, "Should still have 1 passkey after reload")
        profileScreen4.appendPasskey()
        assertEquals(profileScreen4.getErrorMessage(), "Passkey creation failed. Please try again later.")
        assertEquals(profileScreen4.countNumberOfPasskeys(), 1, "Should still have 1 passkey after failed append")

        // Block append-finish (we have to delete the passkey first, otherwise we get an excludeCredentials error)
        profileScreen4.deletePasskey(passkeyId = profileScreen4.getPasskeyIds()[0], complete = true)
        waitForCondition { profileScreen4.countNumberOfPasskeys() == 0 }
        blockCorbadoEndpoint(CorbadoEndpoint.AppendFinish)
        val profileScreen5 = profileScreen4.reloadPage()
        profileScreen5.appendPasskey()
        assertEquals(profileScreen5.getErrorMessage(), "Passkey creation failed. Please try again later.")
        assertEquals(profileScreen5.getListMessage(), "There is currently no passkey saved for this account.")

        // Block manage-delete
        blockCorbadoEndpoint(CorbadoEndpoint.ManageDelete)
        // Append passkey (to prepare for manage-delete)
        profileScreen5.appendPasskey()
        waitForCondition { profileScreen5.countNumberOfPasskeys() == 1 }
        profileScreen5.deletePasskey(passkeyId = profileScreen5.getPasskeyIds()[0], complete = true)
        waitForCondition { profileScreen4.countNumberOfPasskeys() == 1 }
        assertEquals(profileScreen5.getErrorMessage(), "Passkey deletion failed. Please try again later.")
        assertEquals(profileScreen5.getListMessage(), null, "List message should be cleared after deletion error")
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

    private fun blockCorbadoEndpoint(endpoint: CorbadoEndpoint) {
        composeTestRule.activity.blockCorbadoEndpoints(listOf(endpoint.path))
    }

    private fun unblockCorbadoEndpoint() {
        composeTestRule.activity.blockCorbadoEndpoints(emptyList())
    }
} 