package com.example.kinetiq.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kinetiq.models.UserRole
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

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isLogin) "Welcome Back" else "Create Account",
            style = MaterialTheme.typography.headlineLarge
        )
        
        Spacer(Modifier.height(32.dp))
        
        if (!isLogin) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            
            Text("Select Role", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedRole == UserRole.PATIENT,
                    onClick = { selectedRole = UserRole.PATIENT }
                )
                Text("Patient")
                Spacer(Modifier.width(16.dp))
                RadioButton(
                    selected = selectedRole == UserRole.DOCTOR,
                    onClick = { selectedRole = UserRole.DOCTOR }
                )
                Text("Doctor")
            }
            Spacer(Modifier.height(16.dp))
        }
        
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(16.dp))
        
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    if (isLogin) {
                        viewModel.login(email, password)
                    } else {
                        viewModel.signUp(email, password, selectedRole, displayName)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isLogin) "Login" else "Sign Up")
            }
            
            TextButton(onClick = { 
                isLogin = !isLogin 
                viewModel.resetState()
            }) {
                Text(if (isLogin) "Need an account? Sign Up" else "Already have an account? Login")
            }
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
        }
    }
}
