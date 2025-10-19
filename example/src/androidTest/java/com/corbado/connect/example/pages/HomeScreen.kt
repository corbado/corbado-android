package com.corbado.connect.example.pages

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.corbado.simplecredentialmanager.AuthorizationError
import com.corbado.simplecredentialmanager.mocks.ControlServer

/**
 * Page object for the Profile Screen.
 */
class HomeScreen(composeTestRule: ComposeTestRule) : BaseScreen(composeTestRule) {

    override fun visible(timeout: Double): Boolean {
        return awaitVisible("HomeScreen", timeout) && awaitInvisible("LoadingIndicator", timeout)
    }

    fun navigateBackToProfileScreen(): ProfileScreen {
        waitAndClick("homeScreen.backButton")
        return ProfileScreen(composeTestRule).also { it.visible() }
    }

    fun acceptInstantAppend(automatic: Boolean, controlServer: ControlServer) {
        if (automatic) {
            controlServer.createError = null

            // we have to wait a bit for the append to be fetched from the backend
            Thread.sleep(3000)

            // nothing to do, it happens automatically
            return
        } else {
            controlServer.createError = AuthorizationError.Cancelled

            // we have to wait a bit for the append to be fetched from the backend
            Thread.sleep(2000)

            controlServer.createError = null
            waitAndClick("bottomSheet.continueButton")

            Thread.sleep(2000)
        }
    }

    fun acceptAutomaticAppend() {
        // nothing to do, it happens automatically
        Thread.sleep(3000) // wait for the automatic process to complete
    }

    fun acceptBottomSheetAppend() {
        Thread.sleep(1000)
        waitAndClick("bottomSheet.continueButton")
        Thread.sleep(2000)
    }

    fun declineAutomaticAppend(controlServer: ControlServer) {
        controlServer.createError = AuthorizationError.Cancelled
        Thread.sleep(3000)
        controlServer.createError = null
    }

    fun declineBottomSheet() {
        waitAndGetText("bottomSheet.continueButton")
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
    }

    fun clickButton1(expectDummyDialog: Boolean) {
        waitAndClick("homeScreen.dummyButton1")

        if (expectDummyDialog) {
            waitAndClick("NotificationDialog.okButton")
            Thread.sleep(2000)
        }
    }

    fun setLocalDebounceDays(days: String) {
        waitAndSetText("homeScreen.localDebounceTextField", days)
    }
}