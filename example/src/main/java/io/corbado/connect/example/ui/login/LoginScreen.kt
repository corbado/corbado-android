package com.corbado.connect.example.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.corbado.connect.example.R
import com.corbado.connect.example.ui.Screen
import com.corbado.connect.example.ui.components.CorbadoPrimaryButton
import com.corbado.connect.example.ui.components.CorbadoSecondaryButton
import android.app.Activity

@Composable
fun LoginScreen(navController: NavController, loginViewModel: LoginViewModel = viewModel()) {
    val status by loginViewModel.status.collectAsState()
    val activity = LocalContext.current as? Activity

    LaunchedEffect(Unit) {
        activity?.let { loginViewModel.loadInitialStep(it) }

        loginViewModel.navigationEvents.collect { event ->
            when (event) {
                is NavigationEvent.NavigateTo -> {
                    navController.navigate(event.route) {
                        popUpTo(Screen.Login.route) {
                            inclusive = true
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("LoginScreen"),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            LoginStatus.Loading -> CircularProgressIndicator(modifier = Modifier.testTag("LoadingIndicator"))
            LoginStatus.FallbackFirst -> FallbackLoginView(loginViewModel, navController)
            LoginStatus.FallbackSecondTOTP -> FallbackTOTPView(loginViewModel)
            LoginStatus.FallbackSecondSMS -> FallbackSMSView(loginViewModel)
            LoginStatus.PasskeyTextField -> activity?.let {
                PasskeyTextFieldView(
                    loginViewModel,
                    navController,
                    it
                )
            }

            LoginStatus.PasskeyOneTap -> activity?.let {
                PasskeyOneTapView(
                    loginViewModel,
                    navController,
                    it
                )
            }

            LoginStatus.PasskeyErrorSoft -> activity?.let {
                PasskeyErrorSoftView(
                    loginViewModel,
                    it
                )
            }

            LoginStatus.PasskeyErrorHard -> activity?.let { PasskeyErrorHardView(loginViewModel, it) }
        }
    }
}

@Composable
fun FallbackLoginView(viewModel: LoginViewModel, navController: NavController) {
    val email by viewModel.email.collectAsState()
    val password by viewModel.password.collectAsState()
    val isLoading by viewModel.primaryLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Login", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = email,
            onValueChange = { viewModel.email.value = it },
            label = { Text("Email") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("EmailTextField")
        )

        OutlinedTextField(
            value = password,
            onValueChange = { viewModel.password.value = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("PasswordTextField")
        )

        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        CorbadoPrimaryButton(
            text = "Login",
            onClick = { viewModel.loginWithEmailAndPassword() },
            isLoading = isLoading,
            modifier = Modifier.testTag("LoginButton")
        )

        CorbadoSecondaryButton(
            text = "Sign Up",
            onClick = { navController.navigate(Screen.SignUp.route) },
            modifier = Modifier.testTag("SignUpButton")
        )
    }
}

@Composable
fun FallbackTOTPView(viewModel: LoginViewModel) {
    val isLoading by viewModel.primaryLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Enter TOTP Code", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("6-digit TOTP") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("TOTPTextField")
        )

        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        CorbadoPrimaryButton(
            text = "Submit",
            onClick = { viewModel.verifyTOTP(code) },
            isLoading = isLoading,
            modifier = Modifier.testTag("SubmitTOTPButton")
        )
    }
}

@Composable
fun FallbackSMSView(viewModel: LoginViewModel) {
    val isLoading by viewModel.primaryLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Enter SMS Code", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("6-digit code") },
            modifier = Modifier.fillMaxWidth()
        )

        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        CorbadoPrimaryButton(
            text = "Submit",
            onClick = { viewModel.verifySMS(code) },
            isLoading = isLoading
        )
    }
}

@Composable
fun PasskeyTextFieldView(
    viewModel: LoginViewModel,
    navController: NavController,
    activity: Activity
) {
    val email by viewModel.email.collectAsState()
    val isLoading by viewModel.primaryLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Login with Passkey", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = email,
            onValueChange = { viewModel.email.value = it },
            label = { Text("Email") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("EmailTextField")
        )

        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        CorbadoPrimaryButton(
            text = "Login with Passkey",
            onClick = { viewModel.loginWithPasskeyTextField(activity) },
            isLoading = isLoading,
            modifier = Modifier.testTag("LoginWithPasskeyButton")
        )

        CorbadoSecondaryButton(
            text = "Sign Up",
            onClick = { navController.navigate(Screen.SignUp.route) },
            modifier = Modifier.testTag("SignUpButton")
        )
    }
}

@Composable
fun PasskeyOneTapView(viewModel: LoginViewModel, navController: NavController, activity: Activity) {
    val email by viewModel.email.collectAsState()
    val isLoading by viewModel.primaryLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Login with Passkey", style = MaterialTheme.typography.headlineMedium)

        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        CorbadoPrimaryButton(
            text = "Login as ${truncateEmail(email, 30)}",
            onClick = { viewModel.loginWithPasskeyOneTap(activity) },
            isLoading = isLoading,
            modifier = Modifier.testTag("LoginOneTapButton")
        )

        CorbadoSecondaryButton(
            text = "Switch account",
            onClick = { viewModel.discardPasskeyOneTap() },
            modifier = Modifier.testTag("SwitchAccountButton")
        )

        CorbadoSecondaryButton(
            text = "Sign Up",
            onClick = { navController.navigate(Screen.SignUp.route) },
            modifier = Modifier.testTag("SignUpButton")
        )
    }
}

@Composable
fun PasskeyErrorSoftView(viewModel: LoginViewModel, activity: Activity) {
    val isLoading by viewModel.primaryLoading.collectAsState()

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Use your passkey to confirm it's really you",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Icon(
            painter = painterResource(id = R.drawable.ic_passkey_encourage),
            contentDescription = "Passkey Encourage",
            modifier = Modifier.size(250.dp),
            tint = Color.Unspecified
        )

        Text(
            text = "Your device will ask you for your fingerprint, face or screen lock.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        CorbadoPrimaryButton(
            text = "Continue",
            onClick = { viewModel.loginWithPasskeyOneTap(activity) },
            isLoading = isLoading,
            modifier = Modifier.testTag("LoginErrorSoft")
        )

        CorbadoSecondaryButton(
            text = "Use password instead",
            onClick = { viewModel.discardPasskeyLogin() }
        )
    }
}

@Composable
fun PasskeyErrorHardView(viewModel: LoginViewModel, activity: Activity) {
    val isLoading by viewModel.primaryLoading.collectAsState()

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Icon(
            painter = painterResource(id = R.drawable.ic_passkey_error),
            contentDescription = "Passkey Error",
            modifier = Modifier.size(120.dp),
            tint = Color.Unspecified
        )

        Text(
            text = "Login with passkeys was not possible.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        CorbadoPrimaryButton(
            text = "Try again",
            onClick = { viewModel.loginWithPasskeyOneTap(activity) },
            isLoading = isLoading
        )

        CorbadoSecondaryButton(
            text = "Skip passkey login",
            onClick = { viewModel.discardPasskeyLogin() }
        )
    }
}

private fun truncateEmail(email: String, maxLength: Int): String {
    return if (email.length > maxLength) {
        "${email.take(maxLength / 2)}...${email.takeLast(maxLength / 2)}"
    } else {
        email
    }
}