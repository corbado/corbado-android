package com.corbado.connect.example.ui.signup

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.corbado.connect.example.ui.Screen
import com.corbado.connect.example.ui.components.CorbadoPrimaryButton
import com.corbado.connect.example.ui.components.CorbadoSecondaryButton
import com.corbado.connect.example.ui.login.NavigationEvent

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
            .padding(16.dp)
            .testTag("SignUpScreen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Create an account", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = email,
            onValueChange = { signUpViewModel.email.value = it },
            label = { Text("Email") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("SignUpEmailTextField")
        )

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { signUpViewModel.phoneNumber.value = it },
            label = { Text("Phone Number") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("SignUpPhoneTextField")
        )

        OutlinedTextField(
            value = password,
            onValueChange = { signUpViewModel.password.value = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("SignUpPasswordTextField")
        )

        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        CorbadoPrimaryButton(
            text = "Sign Up",
            onClick = { signUpViewModel.signUp() },
            isLoading = isLoading,
            modifier = Modifier.testTag("SignUpSubmitButton")
        )

        CorbadoSecondaryButton(
            text = "AutoFill",
            onClick = { signUpViewModel.autoFill() }
        )

        CorbadoSecondaryButton(
            text = "Already have an account? Log In",
            onClick = { navController.navigate(Screen.Login.route) }
        )
    }
} 