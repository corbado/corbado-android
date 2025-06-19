package io.corbado.connect.example.ui.signup

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.corbado.connect.example.ui.Screen
import io.corbado.connect.example.ui.login.NavigationEvent

@Composable
fun SignUpScreen(navController: NavController, signUpViewModel: SignUpViewModel = viewModel()) {
    val email by signUpViewModel.email.collectAsState()
    val phoneNumber by signUpViewModel.phoneNumber.collectAsState()
    val password by signUpViewModel.password.collectAsState()
    val isLoading by signUpViewModel.primaryLoading.collectAsState()
    val errorMessage by signUpViewModel.errorMessage.collectAsState()

    LaunchedEffect(Unit) {
        signUpViewModel.navigationEvents.collect { event ->
            when (event) {
                is NavigationEvent.NavigateTo -> {
                    navController.navigate(event.route)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Create an account", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = email,
            onValueChange = { signUpViewModel.email.value = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { signUpViewModel.phoneNumber.value = it },
            label = { Text("Phone Number") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = password,
            onValueChange = { signUpViewModel.password.value = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = { signUpViewModel.signUp() },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Sign Up")
            }
        }

        Button(
            onClick = { signUpViewModel.autoFill() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("AutoFill")
        }

        TextButton(onClick = { navController.navigate(Screen.Login.route) }) {
            Text("Already have an account? Log In")
        }
    }
} 