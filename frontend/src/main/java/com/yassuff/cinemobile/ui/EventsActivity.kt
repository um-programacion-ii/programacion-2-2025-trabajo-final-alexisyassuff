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
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.List
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

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
                        // Ir a pantalla de ventas
                        val intent = Intent(ctx, SalesActivity::class.java)
                        ctx.startActivity(intent)
                    }) {
                        Icon(Icons.Filled.List, contentDescription = "Mis Ventas")
                    }
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
    val colorFondo = getColorForEvent("evento_${e.id}") // Genera colores según el evento
    val imagen = when ("evento_${e.id}") {
        "evento_1" -> "https://thumbs.dreamstime.com/z/icono-del-calendario-de-los-eventos-simple-vector-122490231.jpg?ct=jpeg"
        "evento_2" -> "https://thumbs.dreamstime.com/z/icono-del-calendario-de-los-eventos-simple-vector-122490231.jpg?ct=jpeg" 
        else -> e.image
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = colorFondo)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Renderizar la imagen (si no existe, usar imagen predeterminada)
            AsyncImage(
                model = imagen,
                contentDescription = "Imagen del evento",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp) // Ajustar el tamaño según necesidad
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = e.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = formatDateTime(e.dateTime), style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Precio: $${String.format("%.2f", e.price)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun getColorForEvent(evento: String): Color {
    return when (evento) {
        "evento_1" -> Color(0xFFFFCDD2) // Rojo pastel claro
        "evento_2" -> Color(0xFFFFF9C4) // Amarillo pastel claro
        "evento_3" -> Color(0xFFC8E6C9) // Verde pastel claro
        "evento_4" -> Color(0xFFE1F5FE) // Azul pastel claro
        "evento_5" -> Color(0xFFFFE0B2) // Naranja pastel claro
        else -> Color(0xFFF5F5F5)       // Gris claro como fallback
    }
}

private fun formatDateTime(dateTimeStr: String): String {
    return try {
        // Intentar parsear diferentes formatos de fecha
        val inputFormat = when {
            dateTimeStr.contains("T") -> SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            dateTimeStr.contains("-") -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            else -> return dateTimeStr // Si no coincide con ningún formato, devolver original
        }

        val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val date = inputFormat.parse(dateTimeStr)
        date?.let { outputFormat.format(it) } ?: dateTimeStr
    } catch (e: Exception) {
        dateTimeStr // Si hay error, devolver fecha original
    }
}