package com.yassuff.cinemobile.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cine.shared.ApiClient
import com.cine.shared.Seat
import com.cine.shared.SessionManager
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.rememberCoroutineScope
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
class EventDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val eventId = intent?.getLongExtra("eventId", -1L) ?: -1L
        setContent {
            val vm: EventDetailViewModel = viewModel(factory = EventDetailViewModel.provideFactory(eventId))
            EventDetailScreen(vm = vm, eventId = eventId)
        }
    }
}

class EventDetailViewModel(private val eventId: Long) : ViewModel() {
    var seats by mutableStateOf<List<Seat>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        loadSeats()
    }

    fun loadSeats() {
        loading = true
        error = null
        viewModelScope.launch {
            try {
                seats = ApiClient.getSeats(eventId)
            } catch (ex: Exception) {
                ex.printStackTrace()
                error = ex.message ?: "Error cargando asientos"
            } finally {
                loading = false
            }
        }
    }

    companion object {
        // Factory simple para pasar eventId a viewModel() en compose
        fun provideFactory(eventId: Long): androidx.lifecycle.ViewModelProvider.Factory {
            return object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return EventDetailViewModel(eventId) as T
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EventDetailScreen(vm: EventDetailViewModel, eventId: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selected by remember { mutableStateOf<Seat?>(null) }
    var performingAction by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Detalle evento: $eventId") },
                actions = {
                    IconButton(onClick = {
                        SessionManager.clear()
                        val intent = android.content.Intent(ctx, LoginActivity::class.java)
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        ctx.startActivity(intent)
                    }) {
                        Icon(Icons.Filled.ExitToApp, contentDescription = "Logout")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                vm.loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                vm.error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = vm.error ?: "Error", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { vm.loadSeats() }) { Text("Reintentar") }
                    }
                }
                vm.seats.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No hay asientos para este evento")
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(vm.seats) { seat ->
                            SeatItem(seat = seat, onClick = { selected = seat })
                        }
                    }
                }
            }

            // Dialog con acciones estrictas por reglas
            if (selected != null) {
                val s = selected!!
                val mySession = SessionManager.getToken()
                val isMine = !s.holder.isNullOrBlank() && s.holder == mySession
                val isSold = s.status?.uppercase()?.contains("VEND") == true
                val isBlocked = s.status?.uppercase()?.contains("BLOQ") == true
                AlertDialog(
                    onDismissRequest = { if (!performingAction) selected = null },
                    title = { Text("Butaca ${s.seatId}", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text(text = "Estado: ${s.status}")
                            if (!s.holder.isNullOrBlank()) Text(text = "Usuario: ${s.holder}")
                            if (!s.updatedAt.isNullOrBlank()) Text(text = "Última actualización: ${formatDateTime(s.updatedAt!!)}")
                            Spacer(modifier = Modifier.height(8.dp))
                            if (performingAction) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Procesando...")
                                }
                            }
                        }
                    },
                    confirmButton = {
                        when {
                            // If sold -> only close
                            isSold -> {
                                TextButton(onClick = { if (!performingAction) selected = null }) { Text("Cerrar") }
                            }
                            // If libre -> Block and Purchase
                            !isBlocked -> {
                                Row {
                                    TextButton(onClick = {
                                        performingAction = true
                                        scope.launch {
                                            try {
                                                ApiClient.blockSeat(eventId, s.seatId)
                                                snackbarHostState.showSnackbar("Butaca bloqueada")
                                                vm.loadSeats()
                                                selected = null
                                            } catch (ex: Exception) {
                                                snackbarHostState.showSnackbar("Error al bloquear: ${ex.message}")
                                            } finally {
                                                performingAction = false
                                            }
                                        }
                                    }, enabled = !performingAction) { Text("Bloquear") }

                                    TextButton(onClick = {
                                        performingAction = true
                                        scope.launch {
                                            try {
                                                ApiClient.purchaseSeat(eventId, s.seatId)
                                                snackbarHostState.showSnackbar("Butaca vendida")
                                                vm.loadSeats()
                                                selected = null
                                            } catch (ex: Exception) {
                                                snackbarHostState.showSnackbar("Error al vender: ${ex.message}")
                                            } finally {
                                                performingAction = false
                                            }
                                        }
                                    }, enabled = !performingAction) { Text("Vender") }
                                }
                            }
                            // If blocked by me -> Unlock and Purchase
                            isBlocked && isMine -> {
                                Row {
                                    TextButton(onClick = {
                                        performingAction = true
                                        scope.launch {
                                            try {
                                                ApiClient.unlockSeat(eventId, s.seatId)
                                                snackbarHostState.showSnackbar("Butaca desbloqueada")
                                                vm.loadSeats()
                                                selected = null
                                            } catch (ex: Exception) {
                                                snackbarHostState.showSnackbar("Error al desbloquear: ${ex.message}")
                                            } finally {
                                                performingAction = false
                                            }
                                        }
                                    }, enabled = !performingAction) { Text("Desbloquear") }

                                    TextButton(onClick = {
                                        performingAction = true
                                        scope.launch {
                                            try {
                                                ApiClient.purchaseSeat(eventId, s.seatId)
                                                snackbarHostState.showSnackbar("Butaca vendida")
                                                vm.loadSeats()
                                                selected = null
                                            } catch (ex: Exception) {
                                                snackbarHostState.showSnackbar("Error al vender: ${ex.message}")
                                            } finally {
                                                performingAction = false
                                            }
                                        }
                                    }, enabled = !performingAction) { Text("Vender") }
                                }
                            }
                            // If blocked by other -> only close with message
                            isBlocked && !isMine -> {
                                TextButton(onClick = { if (!performingAction) selected = null }) {
                                    Text("Cerrar")
                                }
                            }
                            else -> {
                                TextButton(onClick = { if (!performingAction) selected = null }) { Text("Cerrar") }
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { if (!performingAction) selected = null }) { Text("Cancelar") }
                    }
                )
            }
        }
    }
}

@Composable
private fun SeatItem(seat: Seat, onClick: () -> Unit) {
    val color = seatStatusColor(seat.status)
    Card(
        modifier = Modifier
            .size(56.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = seat.seatId.take(6),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = if (isDarkTextNeeded(color)) Color.Black else Color.White
            )
        }
    }
}

private fun seatStatusColor(status: String?): Color {
    val s = status?.uppercase()?.trim() ?: ""
    return when {
        s.contains("LIBRE") || s.contains("AVAILABLE") || s.contains("FREE") -> Color(0xFF81C784) // verde
        s.contains("VEND") || s.contains("SOLD") || s.contains("VENDIDO") -> Color(0xFFF06292) // rosa/rojo
        s.contains("BLOQ") || s.contains("RESERV") || s.contains("BLOCK") -> Color(0xFFFFF176) // amarillo
        else -> Color(0xFF90A4AE) // gris por defecto
    }
}

private fun isDarkTextNeeded(bg: Color): Boolean {
    val luminance = (0.299 * bg.red + 0.587 * bg.green + 0.114 * bg.blue)
    return luminance > 0.6
}

private fun formatDateTime(dateTimeStr: String): String {
    return try {
        // Intentar parsear diferentes formatos de fecha ISO
        val inputFormat = when {
            dateTimeStr.contains("T") && dateTimeStr.contains("Z") -> 
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
            dateTimeStr.contains("T") -> SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            dateTimeStr.contains("-") -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            else -> return dateTimeStr
        }
        
        val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val date = inputFormat.parse(dateTimeStr)
        date?.let { outputFormat.format(it) } ?: dateTimeStr
    } catch (e: Exception) {
        dateTimeStr // Si hay error, devolver fecha original
    }
}
