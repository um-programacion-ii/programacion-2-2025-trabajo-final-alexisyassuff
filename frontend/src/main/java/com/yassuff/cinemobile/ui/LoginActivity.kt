package com.yassuff.cinemobile.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cine.shared.ApiClient
import com.cine.shared.SessionManager
import com.cine.shared.AuthResponse
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = SessionManager.getToken()
        if (!token.isNullOrEmpty()) {
            startActivity(Intent(this, EventsActivity::class.java))
            finish()
            return
        }

        setContent {
            val vm: LoginViewModel = viewModel()
            LoginScreen(vm) { success ->
                if (success) {
                    startActivity(Intent(this, EventsActivity::class.java))
                    finish()
                }
            }
        }
    }
}

class LoginViewModel : ViewModel() {
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun login(username: String, password: String, onResult: (Boolean) -> Unit) {
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val resp: AuthResponse = ApiClient.login(username, password)
                SessionManager.saveToken(resp.idToken)
                onResult(true)
            } catch (ex: Exception) {
                ex.printStackTrace()
                error = ex.message ?: "Error desconocido"
                onResult(false)
            } finally {
                loading = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(vm: LoginViewModel, onLoginSuccess: (Boolean) -> Unit) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.Center) {
            Text(text = "Cine - Login", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Usuario") })
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = pass, 
                onValueChange = { pass = it }, 
                label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { vm.login(user.trim(), pass.trim(), onLoginSuccess) }, enabled = !vm.loading) {
                Text(if (vm.loading) "Ingresando..." else "Ingresar")
            }
            vm.error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
