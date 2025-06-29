package com.corbado.connect.example.ui.totpscreen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lightspark.composeqr.QrCodeView
import com.corbado.connect.example.ui.components.CorbadoPrimaryButton
import com.corbado.connect.example.ui.login.NavigationEvent

@Composable
fun TotpSetupScreen(
    navController: NavController,
    totpSetupViewModel: TotpSetupViewModel = viewModel()
) {
    val isLoading by totpSetupViewModel.isLoading.collectAsState()
    val errorMessage by totpSetupViewModel.errorMessage.collectAsState()
    val setupDetails by totpSetupViewModel.setupDetails.collectAsState()
    val totpCode by totpSetupViewModel.totpCode.collectAsState()

    LaunchedEffect(Unit) {
        totpSetupViewModel.initSetupTOTP()

        totpSetupViewModel.navigationEvents.collect { event ->
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
            .testTag("TotpSetupScreen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("MFA Setup", style = MaterialTheme.typography.headlineMedium)

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.testTag("LoadingIndicator"))
        } else if (setupDetails != null) {
            val details = setupDetails!!
            QrCodeView(
                data = details.getSetupURI("Corbado Android").toString(),
                modifier = Modifier.size(200.dp)
            )

            Row {
                Text("Setup Key:")
                Text(details.sharedSecret, modifier = Modifier.testTag("SetupKeyText"))
            }

            OutlinedTextField(
                value = totpCode,
                onValueChange = { totpSetupViewModel.totpCode.value = it },
                label = { Text("Enter 6-digit code") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("TotpCodeTextField")
            )

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            CorbadoPrimaryButton(
                text = "Complete",
                onClick = { totpSetupViewModel.completeSetupTOTP() },
                isLoading = isLoading,
                modifier = Modifier.testTag("CompleteButton")
            )
        }
    }
} 