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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium)

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Email:", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.weight(1f))
                Text(email, style = MaterialTheme.typography.bodyLarge)
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Phone:", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.weight(1f))
                Text(phoneNumber, style = MaterialTheme.typography.bodyLarge)
            }

            Button(onClick = { profileViewModel.signOut() }, modifier = Modifier.fillMaxWidth()) {
                Text("Logout")
            }

            Text("Passkeys", style = MaterialTheme.typography.headlineSmall)

            errorMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            listMessage?.let {
                Text(it)
            } ?: run {
                LazyColumn {
                    items(passkeys) { passkey ->
                        var showDialog by remember { mutableStateOf(false) }

                        if (showDialog) {
                            AlertDialog(
                                onDismissRequest = { showDialog = false },
                                title = { Text("Delete Passkey") },
                                text = { Text("Are you sure you want to delete this passkey?") },
                                confirmButton = {
                                    Button(onClick = {
                                        profileViewModel.deletePasskey(passkey.id)
                                        showDialog = false
                                    }) {
                                        Text("Delete")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        ListItem(
                            headlineContent = { Text(passkey.id) },
                            supportingContent = { Text(passkey.created) },
                            trailingContent = {
                                IconButton(onClick = { showDialog = true }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        )
                    }
                }
            }

            if (passkeyAppendAllowed) {
                Button(
                    onClick = { profileViewModel.appendPasskey() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create passkey")
                }
            }

            Button(
                onClick = { profileViewModel.fetchUserData() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reload")
            }
        }
    }
} 