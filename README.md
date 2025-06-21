# Corbado Android SDK

[![Platform](https://img.shields.io/badge/Platform-Android-brightgreen.svg)](https://www.android.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=26)

The Corbado Android SDK provides a simple and powerful way to integrate passwordless authentication using passkeys into your native Android applications. It is designed to offer a seamless, secure, and user-friendly login experience, leveraging the native capabilities of the Android platform through the `CredentialManager` API.

This SDK simplifies the entire authentication flow, from user registration and login to passkey management, allowing you to build modern, secure applications with minimal effort.

## ✨ Features

- **Passwordless Authentication**: End-to-end passkey authentication flows (registration and login).
- **Simple Integration**: A clean, modern, and Kotlin-idiomatic API.
- **Native Experience**: Deep integration with Android's `CredentialManager` for a consistent and secure user experience.
- **UI Components**: A set of ready-to-use Jetpack Compose UI components for building login and signup screens quickly (available in the `example` app).
- **Customizable**: Flexible architecture that allows for customization of the UI and authentication flows.

## 🏗️ Architecture

The Corbado Android SDK is structured into several modules:

- **`:sdk`**: The core module that contains the main public-facing API (`Corbado` class). It orchestrates the authentication flows and manages user state.
- **`:api`**: An auto-generated API client (via OpenAPI Generator) that uses **OkHttp 4** for HTTP and **Moshi** for JSON, handling low-level communication with the Corbado backend.
- **`:example`**: A complete sample application demonstrating how to use the SDK in a real-world scenario. It includes UI components, ViewModels, and navigation.
- **`simple-credential-manager`**: A library dependency that provides a clean, protocol-based wrapper around Android's `CredentialManager` API, simplifying passkey operations.

## 🚀 Getting Started

### 1. Add the Dependency

*Note: The Corbado Android SDK is not yet distributed through a public package repository. To use it, you currently need to clone this repository and the `simple-credential-manager` repository and include them as local modules in your Gradle build.*

We are working on making distribution easier in the future. The following instructions are a placeholder for when the package is published.

### 2. Initialize the SDK

The main entry point to the SDK is the `Corbado` class. We recommend managing the `Corbado` instance as a singleton to ensure consistency across your app. You can create a simple service object or use a dependency injection framework like Hilt or Koin.

Here is an example of a simple service locator:

```kotlin
import android.content.Context
import io.corbado.connect.Corbado

object CorbadoService {
    private var instance: Corbado? = null

    fun getInstance(context: Context): Corbado {
        if (instance == null) {
            // Replace "pro-..." with your project ID from the Corbado developer panel
            instance = Corbado(
                projectId = "pro-xxxxxxxxxxxxxxxxxxxx",
                context = context.applicationContext
            )
        }
        return instance!!
    }
}
```

You can then access the `Corbado` instance from your ViewModels, activities, or composable functions like this:

```kotlin
val corbado = CorbadoService.getInstance(context)
```

### 3. Usage Example

Here is a basic example of how to initiate a passkey login flow within a Jetpack Compose screen.

```kotlin
import androidx.compose.runtime.*
import io.corbado.connect.Corbado
import io.corbado.connect.ConnectLoginStep
import io.corbado.connect.ConnectLoginStatus
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(corbado: Corbado) {
    val coroutineScope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var oneTapAvailable by remember { mutableStateOf(false) }
    var showEmailInput by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }

    // Shows how to start the passkey login flow
    LaunchedEffect(Unit) {
        // First, check what the next login step should be
        when (val nextStep = corbado.isLoginAllowed()) {
            is ConnectLoginStep.InitOneTap -> {
                // A passkey is available for the user. Signal the UI to show a "Login with passkey" button.
                oneTapAvailable = true
                email = nextStep.username // Pre-fill email if available
            }
            is ConnectLoginStep.InitTextField -> {
                // No passkey found yet, show a text field for the user to enter their email.
                showEmailInput = true
            }
            is ConnectLoginStep.InitFallback -> {
                // Some error occurred, perhaps show a generic login screen.
                message = nextStep.error?.message ?: "Something went wrong."
            }
        }
    }
    
    fun handleLoginResult(result: ConnectLoginStatus) {
        when (result) {
            is ConnectLoginStatus.Done -> {
                // Passkey login was successful. The result contains the username and a session token.
                // You would typically use this session to authenticate with your own backend.
                // There is no `getCurrentUser()` method; the session token is the proof of authentication.
                message = "Login successful for ${result.username}. Session token: ${result.session}"
                oneTapAvailable = false
                showEmailInput = false
            }
            is ConnectLoginStatus.InitFallback -> {
                // The user has no passkey, fall back to another login method.
                message = "No passkey found. Please use another login method."
                oneTapAvailable = false
                showEmailInput = true
            }
            is ConnectLoginStatus.InitRetry -> {
                // A recoverable error occurred (e.g., user canceled the dialog).
                // Allow the user to try again.
                message = "Passkey login was canceled. Please try again."
                oneTapAvailable = true
            }
            else -> {
                // Handle other states if needed
                message = "Login failed."
            }
        }
    }

    // This is triggered by a "Login with Passkey" button
    val onLoginWithOneTap: () -> Unit = {
        coroutineScope.launch {
            val result = corbado.loginWithOneTap()
            handleLoginResult(result)
        }
    }

    // This would be triggered by a button click after the user enters their email
    val onLoginWithEmail: () -> Unit = {
        coroutineScope.launch {
            try {
                // Use the email from the text field to look for a passkey
                val result = corbado.loginWithTextField(email)
                handleLoginResult(result)
            } catch (e: Exception) {
                message = "Login failed: ${e.message}"
            }
        }
    }
    
    // Example of how your UI might be structured
    Column {
        if (oneTapAvailable) {
            // Button(onClick = onLoginWithOneTap) { Text("Login with Passkey") }
        }
        if (showEmailInput) {
            // TextField(value = email, onValueChange = { email = it })
            // Button(onClick = onLoginWithEmail) { Text("Continue with Email") }
        }
        // Text(text = message)
    }
}
```

### 4. Creating a Passkey

After a user has signed up or logged in using a fallback method (password + MFA), you can prompt them to create a passkey for future passwordless logins. In the SDK this is a two-step "append" flow:

1. Check if appending is allowed (`isAppendAllowed`).  
2. If the SDK asks for it, call `completeAppend()` to open the system dialog and finish creation.

```kotlin
import androidx.compose.runtime.*
import io.corbado.connect.*
import kotlinx.coroutines.launch

@Composable
fun PostLoginScreen(corbado: Corbado) {
    val coroutineScope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }

    // A suspend lambda that returns the *connect token* you obtained from your backend.
    // For demo purposes we use a hard-coded string.
    val connectTokenProvider: suspend () -> String = {
        "con-xxxxxxxxxxxxxxxxxxxx"
    }

    val onCreatePasskey: () -> Unit = {
        coroutineScope.launch {
            // Step 1 – ask if we may append a passkey
            when (val step = corbado.isAppendAllowed(connectTokenProvider)) {
                is ConnectAppendStep.AskUserForAppend -> {
                    // Show a button or start automatically depending on `step.autoAppend`.
                    val status = corbado.completeAppend()   // Step 2 – run system dialog
                    when (status) {
                        is ConnectAppendStatus.Completed ->
                            message = "Passkey created successfully!"
                        ConnectAppendStatus.Cancelled ->
                            message = "Passkey creation was cancelled."
                        ConnectAppendStatus.ExcludeCredentialsMatch ->
                            message = "A matching passkey already exists on this device."
                        ConnectAppendStatus.Error ->
                            message = "An error occurred while creating the passkey."
                    }
                }
                ConnectAppendStep.Skip ->
                    message = "Passkey creation is currently not allowed."
            }
        }
    }

    // Your UI – e.g. Button(onClick = onCreatePasskey) { Text("Create Passkey") }
}
```

## 📱 Example App

The `:example` module provides a comprehensive, hands-on demonstration of the SDK's capabilities. We highly recommend exploring it to see best practices for:

-   Building login, sign-up, and profile screens with Jetpack Compose.
-   Handling navigation in an authentication-based app.
-   Managing user sessions and passkeys.
-   Implementing ViewModel patterns with the Corbado SDK.

To run the example app, simply open the project in Android Studio and run the `example` configuration.

## ✅ Requirements

-   Android API Level 26 + (Android 8.0)
-   Jetpack Compose
-   Kotlin 2.1 (or newer)
