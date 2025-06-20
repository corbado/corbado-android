package io.corbado.connect.example.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.corbado.connect.example.ui.Screen

@Composable
fun LoginScreen(navController: NavController, loginViewModel: LoginViewModel = viewModel()) {
    val status by loginViewModel.status.collectAsState()

    LaunchedEffect(Unit) {
        loginViewModel.loadInitialStep()

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

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (status) {
            LoginStatus.Loading -> CircularProgressIndicator()
            LoginStatus.FallbackFirst -> FallbackLoginView(loginViewModel, navController)
            LoginStatus.FallbackSecondTOTP -> FallbackTOTPView(loginViewModel)
            LoginStatus.FallbackSecondSMS -> FallbackSMSView(loginViewModel)
            LoginStatus.PasskeyTextField -> PasskeyTextFieldView(loginViewModel, navController)
            LoginStatus.PasskeyOneTap -> PasskeyOneTapView(loginViewModel, navController)
            LoginStatus.PasskeyErrorSoft -> PasskeyErrorSoftView(loginViewModel)
            LoginStatus.PasskeyErrorHard -> PasskeyErrorHardView(loginViewModel)
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
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = password,
            onValueChange = { viewModel.password.value = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = { viewModel.loginWithEmailAndPassword() },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Login")
            }
        }

        TextButton(onClick = { navController.navigate(Screen.SignUp.route) }) {
            Text("Don't have an account? Sign Up")
        }
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
            modifier = Modifier.fillMaxWidth()
        )

        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = { viewModel.verifyTOTP(code) },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Submit")
            }
        }
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

        Button(
            onClick = { viewModel.verifySMS(code) },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Submit")
            }
        }
    }
}

@Composable
fun PasskeyTextFieldView(viewModel: LoginViewModel, navController: NavController) {
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
            modifier = Modifier.fillMaxWidth()
        )

        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = { viewModel.loginWithPasskeyTextField() },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Login with Passkey")
            }
        }

        TextButton(onClick = { navController.navigate(Screen.SignUp.route) }) {
            Text("Don't have an account? Sign Up")
        }
    }
}

@Composable
fun PasskeyOneTapView(viewModel: LoginViewModel, navController: NavController) {
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

        Button(
            onClick = { viewModel.loginWithPasskeyOneTap() },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Login as $email")
            }
        }

        Button(
            onClick = { viewModel.discardPasskeyOneTap() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Switch account")
        }

        TextButton(onClick = { navController.navigate(Screen.SignUp.route) }) {
            Text("Don't have an account? Sign Up")
        }
    }
}

@Composable
fun PasskeyErrorSoftView(viewModel: LoginViewModel) {
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

        // Placeholder for the passkey image
        Box(modifier = Modifier.size(120.dp))

        Text(
            text = "Your device will ask you for your fingerprint, face or screen lock.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = { viewModel.loginWithPasskeyOneTap() },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Continue")
            }
        }

        TextButton(onClick = { viewModel.discardPasskeyLogin() }) {
            Text("Use password instead")
        }
    }
}

@Composable
fun PasskeyErrorHardView(viewModel: LoginViewModel) {
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

        // Placeholder for the passkey error image
        Box(modifier = Modifier.size(120.dp))

        Text(
            text = "Login with passkeys was not possible.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = { viewModel.loginWithPasskeyOneTap() },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Try again")
            }
        }

        TextButton(onClick = { viewModel.discardPasskeyLogin() }) {
            Text("Skip passkey login")
        }
    }
} 