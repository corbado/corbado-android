package com.corbado.connect.example.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.corbado.connect.example.R
import com.corbado.connect.example.ui.components.CorbadoPrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    homeViewModel: HomeViewModel = viewModel()
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        homeViewModel.onAppear(context)
    }

    uiState.notificationMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { homeViewModel.dismissNotification() },
            title = { Text("Dummy notification") },
            text = { Text(message) },
            confirmButton = {
                TextButton(
                    onClick = { homeViewModel.dismissNotification() },
                    modifier = Modifier.testTag("NotificationDialog.okButton")
                ) {
                    Text("OK")
                }
            }
        )
    }

    if (uiState.showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { homeViewModel.dismissBottomSheet() },
            modifier = Modifier.testTag("BottomSheet")
        ) {
            PasskeyAppendBottomSheet(homeViewModel)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("HomeScreen"),
        verticalArrangement = Arrangement.spacedBy(30.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.testTag("homeScreen.backButton")
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Text(
                text = "Home",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.testTag("homeScreen.headline")
            )
        }

        Text(
            text = "Welcome to the CorbadoConnect Android demo app",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("homeScreen.subtitle")
        )

        Divider()

        OutlinedTextField(
            value = uiState.localDebounceDays,
            onValueChange = { homeViewModel.updateLocalDebounceDays(it) },
            label = { Text("Local Debounce (days)") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("homeScreen.localDebounceTextField"),
            singleLine = true
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            DummyButton(
                number = 1,
                isPasskeyActive = uiState.isButton1PasskeyActive,
                onClick = { number ->
                    if (uiState.isButton1PasskeyActive) {
                        homeViewModel.passkeyActiveButtonClicked(context)
                    } else {
                        homeViewModel.showNotification("Button $number clicked")
                    }
                },
                modifier = Modifier.weight(1f)
            )

            DummyButton(
                number = 2,
                isPasskeyActive = uiState.isButton2PasskeyActive,
                onClick = { number ->
                    if (uiState.isButton2PasskeyActive) {
                        homeViewModel.passkeyActiveButtonClicked(context)
                    } else {
                        homeViewModel.showNotification("Button $number clicked")
                    }
                },
                modifier = Modifier.weight(1f)
            )

            DummyButton(
                number = 3,
                isPasskeyActive = uiState.isButton3PasskeyActive,
                onClick = { number ->
                    if (uiState.isButton3PasskeyActive) {
                        homeViewModel.passkeyActiveButtonClicked(context)
                    } else {
                        homeViewModel.showNotification("Button $number clicked")
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun DummyButton(
    number: Int,
    isPasskeyActive: Boolean,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = { onClick(number) },
        modifier = modifier
            .testTag("homeScreen.dummyButton$number"),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
fun PasskeyAppendBottomSheet(viewModel: HomeViewModel) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Simplify your sign in",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag("bottomSheet.title")
                )

                Text(
                    text = "Create a passkey",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("bottomSheet.subtitle")
                )

                Text(
                    text = "Sign in easily now with your fingerprint, face, or PIN. Sync across your devices.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("bottomSheet.description")
                )
            }

            Icon(
                painter = painterResource(id = R.drawable.ic_passkey_encourage),
                contentDescription = "Passkey Append",
                modifier = Modifier.size(100.dp),
                tint = Color.Unspecified
            )
        }

        CorbadoPrimaryButton(
            text = "Continue",
            onClick = {
                viewModel.dismissBottomSheet()
                viewModel.completePasskeyAppend(context, autoAppend = false)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bottomSheet.continueButton")
        )
    }
}

