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
fun AuthScreen(
    onAuthSuccess: (UserRole) -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var isLogin by remember { mutableStateOf(true) }
    
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.PATIENT) }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess && state.userRole != null) {
            onAuthSuccess(state.userRole!!)
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
                text = if (isLogin) "Welcome Back" else "Create Account",
                style = MaterialTheme.typography.headlineLarge,
                color = MooveOnBackground,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = if (isLogin) "Sign in to continue your recovery" else "Join Moove for a better recovery",
                style = MaterialTheme.typography.bodyMedium,
                color = MooveOnSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(Modifier.height(40.dp))
            
            if (!isLogin) {
                MooveTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = "Full Name",
                    placeholder = "John Doe"
                )
                Spacer(Modifier.height(16.dp))
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Select Role", 
                        style = MaterialTheme.typography.titleMedium,
                        color = MooveOnBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedRole == UserRole.PATIENT,
                            onClick = { selectedRole = UserRole.PATIENT },
                            colors = RadioButtonDefaults.colors(selectedColor = MoovePrimary)
                        )
                        Text("Patient", style = MaterialTheme.typography.bodyMedium, color = MooveOnBackground)
                        Spacer(Modifier.width(16.dp))
                        RadioButton(
                            selected = selectedRole == UserRole.DOCTOR,
                            onClick = { selectedRole = UserRole.DOCTOR },
                            colors = RadioButtonDefaults.colors(selectedColor = MoovePrimary)
                        )
                        Text("Doctor", style = MaterialTheme.typography.bodyMedium, color = MooveOnBackground)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            
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

            if (isLogin) {
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
            }

            Spacer(Modifier.height(32.dp))

            if (state.isLoading) {
                CircularProgressIndicator(color = MoovePrimary)
            } else {
                MoovePrimaryButton(
                    onClick = {
                        if (isLogin) {
                            viewModel.login(email, password)
                        } else {
                            viewModel.signUp(email, password, selectedRole, displayName)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isLogin) "Login" else "Sign Up", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
                Spacer(Modifier.height(8.dp))
                
                TextButton(onClick = { 
                    isLogin = !isLogin 
                    viewModel.resetState()
                }) {
                    Text(
                        if (isLogin) "Need an account? Sign Up" else "Already have an account? Login",
                        color = MooveOnSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
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
