package com.yassuff.cinemobile.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cine.shared.ApiClient
import com.cine.shared.EventSummary
import com.cine.shared.SessionManager
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.lifecycle.viewmodel.compose.viewModel

class EventsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: EventsViewModel = viewModel()
            EventsScreen(vm) { eventId ->
                val i = Intent(this, EventDetailActivity::class.java)
                i.putExtra("eventId", eventId)
                startActivity(i)
            }
        }
    }
}

class EventsViewModel : ViewModel() {
    var events by mutableStateOf<List<EventSummary>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init { load() }

    fun load() {
        loading = true
        error = null
        viewModelScope.launch {
            try {
                events = ApiClient.getEvents()
            } catch (ex: Exception) {
                ex.printStackTrace()
                // si la excepción es de sesión, podrías propagarla para redirigir a login
                error = ex.message ?: "Error cargando eventos"
            } finally {
                loading = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(vm: EventsViewModel, onEventClick: (Long) -> Unit) {
    val ctx = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize()) {
        Column {
            TopAppBar(
                title = { Text("Eventos") },
                actions = {
                    IconButton(onClick = {
                        // logout: limpiar sesión y volver a LoginActivity
                        SessionManager.clear()
                        val intent = Intent(ctx, LoginActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        ctx.startActivity(intent)
                    }) {
                        Icon(Icons.Filled.ExitToApp, contentDescription = "Logout")
                    }
                }
            )

            if (vm.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                if (vm.events.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("No hay eventos para mostrar")
                    }
                } else {
                    LazyColumn(modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)) {
                        items(vm.events) { e ->
                            EventCard(e, onClick = { onEventClick(e.id) })
                        }
                    }
                }
            }

            vm.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun EventCard(e: EventSummary, onClick: () -> Unit) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(6.dp)
        .clickable { onClick() }) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = e.title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = e.dateTime, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(6.dp))
            // Ajusta el nombre del campo si tu modelo usa otra propiedad
            Text(text = "Disponibles: ${e.availableSeats}")
        }
    }
}