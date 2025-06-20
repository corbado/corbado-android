package io.corbado.connect.example.ui.postlogin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.corbado.connect.example.R
import io.corbado.connect.example.ui.login.NavigationEvent

@Composable
fun PostLoginScreen(
    navController: NavController,
    postLoginViewModel: PostLoginViewModel = viewModel()
) {
    val state by postLoginViewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        postLoginViewModel.loadInitialStep()

        postLoginViewModel.navigationEvents.collect { event ->
            when (event) {
                is NavigationEvent.NavigateTo -> {
                    navController.navigate(event.route)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val s = state) {
            PostLoginStatus.Loading -> CircularProgressIndicator()
            PostLoginStatus.PasskeyAppend -> PasskeyAppendView(postLoginViewModel)
            is PostLoginStatus.PasskeyAppended -> PasskeyAppendedView(postLoginViewModel, s.aaguidName)
        }
    }
}

@Composable
fun PasskeyAppendView(viewModel: PostLoginViewModel) {
    val isLoading by viewModel.primaryLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Simplify your Sign In",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            "Create a passkey",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Icon(
            painter = painterResource(id = R.drawable.ic_passkey_encourage),
            contentDescription = "Passkey Encourage",
            modifier = Modifier.size(250.dp),
            tint = Color.Unspecified
        )

        Text(
            "Sign in easily now with your fingerprint, face, or PIN. Sync across your devices.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = { viewModel.createPasskey() },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Create passkey")
            }
        }

        TextButton(onClick = { viewModel.skipPasskeyCreation() }) {
            Text("Skip")
        }
    }
}

@Composable
fun PasskeyAppendedView(viewModel: PostLoginViewModel, aaguidName: String?) {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_passkey_success),
            contentDescription = "Passkey Success",
            modifier = Modifier.size(120.dp),
        )
        Text("Passkey Created Successfully", style = MaterialTheme.typography.headlineMedium)
        if (aaguidName != null) {
            Text("Your passkey is stored in $aaguidName.")
        }
        Button(onClick = { viewModel.navigateAfterPasskeyAppend() }) {
            Text("Continue")
        }
    }
} 