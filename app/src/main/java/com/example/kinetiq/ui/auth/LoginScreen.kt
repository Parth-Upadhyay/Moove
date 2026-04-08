package com.example.kinetiq.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kinetiq.models.UserRole
import com.example.kinetiq.ui.theme.*
import com.example.kinetiq.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: (UserRole) -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess && state.userRole != null) {
            onLoginSuccess(state.userRole!!)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MooveBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Welcome Back", 
                style = MaterialTheme.typography.headlineLarge,
                color = MooveOnBackground,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                "Sign in to continue your recovery journey",
                style = MaterialTheme.typography.bodyMedium,
                color = MooveOnSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(Modifier.height(40.dp))
            
            MooveTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                placeholder = "example@moove.com"
            )
            
            Spacer(Modifier.height(16.dp))
            
            MooveTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                placeholder = "••••••••",
                singleLine = true
            )

            TextButton(
                onClick = { 
                    if (email.isNotBlank()) {
                        viewModel.resetPassword(email)
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Forgot Password?", color = MoovePrimary, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(32.dp))

            if (state.isLoading) {
                CircularProgressIndicator(color = MoovePrimary)
            } else {
                MoovePrimaryButton(
                    onClick = { viewModel.login(email, password) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Login", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            state.message?.let {
                Text(it, color = MoovePrimary, modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodySmall)
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
