package io.corbado.connect.example.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.corbado.connect.Passkey
import io.corbado.connect.example.ui.components.CorbadoPrimaryButton
import io.corbado.connect.example.ui.components.CorbadoSecondaryButton
import io.corbado.connect.example.ui.login.NavigationEvent

@Composable
fun ProfileScreen(navController: NavController, profileViewModel: ProfileViewModel = viewModel()) {
    val isLoading by profileViewModel.isLoading.collectAsState()
    val email by profileViewModel.email.collectAsState()
    val phoneNumber by profileViewModel.phoneNumber.collectAsState()
    val passkeys by profileViewModel.passkeys.collectAsState()
    val passkeyAppendAllowed by profileViewModel.passkeyAppendAllowed.collectAsState()
    val listMessage by profileViewModel.listMessage.collectAsState()
    val errorMessage by profileViewModel.errorMessage.collectAsState()

    LaunchedEffect(Unit) {
        profileViewModel.fetchUserData()

        profileViewModel.navigationEvents.collect { event ->
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
            .testTag("ProfileScreen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(30.dp)
    ) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium)

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.testTag("LoadingIndicator"))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("General", style = MaterialTheme.typography.headlineSmall)

                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Email", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (email.length > 30) {
                            "${email.take(15)}...${email.takeLast(15)}"
                        } else {
                            email
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Phone", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(phoneNumber, style = MaterialTheme.typography.bodyLarge)
                }

                CorbadoSecondaryButton(
                    text = "Logout",
                    modifier = Modifier.testTag("LogoutButton"),
                    onClick = { profileViewModel.signOut() }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Passkeys", style = MaterialTheme.typography.headlineSmall)

                errorMessage?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("ErrorMessage"))
                }

                listMessage?.let {
                    Text(it)
                } ?: run {
                    PasskeyList(passkeys, onDeleteClick = { passkeyId ->
                        profileViewModel.deletePasskey(passkeyId)
                    })
                }

                if (passkeyAppendAllowed) {
                    CorbadoPrimaryButton(
                        text = "Create passkey",
                        modifier = Modifier.testTag("CreatePasskeyButton"),
                        onClick = { profileViewModel.appendPasskey() }
                    )
                }

                CorbadoSecondaryButton(
                    text = "Reload",
                    onClick = { profileViewModel.fetchUserData() }
                )
            }
        }
    }
}

@Composable
private fun PasskeyList(
    passkeys: List<Passkey>,
    onDeleteClick: (passkeyId: String) -> Unit
) {

    LazyColumn {
        items(items = passkeys) { passkey: Passkey ->
            var showDialog by remember { mutableStateOf(false) }

            if (showDialog) {
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { Text("Delete Passkey") },
                    text = { Text("Are you sure you want to delete this passkey?") },
                    confirmButton = {
                        CorbadoPrimaryButton(
                            text = "Delete",
                            onClick = {
                                onDeleteClick(passkey.id)
                                showDialog = false
                            },
                            modifier = Modifier.testTag("PasskeyListDeleteButtonConfirm")
                        )
                    },
                    dismissButton = {
                        CorbadoSecondaryButton(
                            text = "Cancel",
                            onClick = { showDialog = false },
                            modifier = Modifier.testTag("PasskeyListDeleteButtonCancel")
                        )
                    }
                )
            }

            ListItem(
                headlineContent = { Text(passkey.id) },
                supportingContent = { Text(passkey.createdMs.toString()) },
                trailingContent = {
                    IconButton(onClick = { showDialog = true }, modifier = Modifier.testTag("DeletePasskeyButton-${passkey.id}")) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                },
                modifier = Modifier.testTag("PasskeyListItem-${passkey.id}")
            )
        }
    }
}