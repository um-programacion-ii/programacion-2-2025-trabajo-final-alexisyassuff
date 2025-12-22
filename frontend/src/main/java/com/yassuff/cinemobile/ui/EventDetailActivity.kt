package com.yassuff.cinemobile.ui
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
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
    
    suspend fun getSeatState(eventId: Long, seatId: String): Map<String, Any?> {
        return withContext(Dispatchers.IO) {
            com.cine.shared.ApiClient.getSeatState(eventId, seatId)
        }
    }
    
    var seatLockTimestamps by mutableStateOf<Map<String, Long>>(emptyMap())
        private set

    init {
        loadSeats()
    }

    fun loadSeats() {
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val newSeats = ApiClient.getSeats(eventId)
                val mySession = SessionManager.getToken()
                val currentTime = System.currentTimeMillis()
                
                // Limpiar timestamps expirados del SessionManager
                SessionManager.clearExpiredTimestamps(eventId)
                
                // Actualizar timestamps usando persistencia del SessionManager
                val newTimestamps = seatLockTimestamps.toMutableMap()
                
                newSeats.forEach { seat ->
                    val isMyBlocked = seat.status?.uppercase()?.contains("BLOQ") == true && 
                                     seat.holder == mySession
                    
                    if (isMyBlocked) {
                        // Verificar si ya tenemos el timestamp persistido
                        val persistedTimestamp = SessionManager.getSeatTimestamp(eventId, seat.seatId)
                        
                        if (persistedTimestamp != null) {
                            // Usar timestamp persistido
                            newTimestamps[seat.seatId] = persistedTimestamp
                        } else if (!seatLockTimestamps.containsKey(seat.seatId)) {
                            // Nuevo bloqueo detectado - guardar timestamp persistente
                            SessionManager.saveSeatTimestamp(eventId, seat.seatId, currentTime)
                            newTimestamps[seat.seatId] = currentTime
                        }
                    } else {
                        // Asiento no bloqueado por mí - limpiar
                        if (seatLockTimestamps.containsKey(seat.seatId)) {
                            SessionManager.removeSeatTimestamp(eventId, seat.seatId)
                            newTimestamps.remove(seat.seatId)
                        }
                    }
                }
                
                seatLockTimestamps = newTimestamps
                seats = newSeats
            } catch (ex: Exception) {
                ex.printStackTrace()
                error = ex.message ?: "Error cargando asientos"
            } finally {
                loading = false
            }
        }
    }

    fun markSeatAsBlocked(seatId: String) {
        val currentTime = System.currentTimeMillis()
        // Guardar timestamp persistente
        SessionManager.saveSeatTimestamp(eventId, seatId, currentTime)
        
        val newTimestamps = seatLockTimestamps.toMutableMap()
        newTimestamps[seatId] = currentTime
        seatLockTimestamps = newTimestamps
    }

    fun removeSeatTimestamp(seatId: String) {
        // Remover timestamp persistente
        SessionManager.removeSeatTimestamp(eventId, seatId)
        
        val newTimestamps = seatLockTimestamps.toMutableMap()
        newTimestamps.remove(seatId)
        seatLockTimestamps = newTimestamps
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

    var selectedSeats by remember { mutableStateOf<Set<Seat>>(emptySet()) }
    var performingAction by remember { mutableStateOf(false) }
    var showMultiDialog by remember { mutableStateOf(false) }

    // Nuevas variables para datos del comprador
    var showBuyerDialog by remember { mutableStateOf(false) }
    var buyerPersona by remember { mutableStateOf("") }

    // Temporizador para actualizar los tiempos restantes cada segundo
    LaunchedEffect(vm.seatLockTimestamps) {
        while (vm.seatLockTimestamps.isNotEmpty()) {
            delay(1000) // Actualizar cada segundo
            val currentTime = System.currentTimeMillis()
            val expiredSeats = vm.seatLockTimestamps.filter { (_, timestamp) ->
                (currentTime - timestamp) >= 300_000 // 5 minutos = 300,000 ms
            }

            // Remover timestamps de asientos expirados
            expiredSeats.keys.forEach { seatId ->
                vm.removeSeatTimestamp(seatId)
            }

            // Si hay asientos que expiraron, recargar la vista
            if (expiredSeats.isNotEmpty()) {
                vm.loadSeats()
            }
        }
    }

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
                            SeatItem(
                                seat = seat,
                                isSelected = selectedSeats.contains(seat),
                                lockTimestamp = vm.seatLockTimestamps[seat.seatId],
                                onClick = {
                                    val status = seat.status?.uppercase()?.trim() ?: ""
                                    val isAvailable = status.contains("LIBRE") || status.contains("AVAILABLE") || status.contains("FREE")
                                    val mySession = SessionManager.getToken()
                                    val isMyBlocked = status.contains("BLOQ") && !seat.holder.isNullOrBlank() && seat.holder == mySession

                                    when {
                                        // Si es libre o bloqueado por mí, permitir selección múltiple
                                        isAvailable || isMyBlocked -> {
                                            selectedSeats = if (selectedSeats.contains(seat)) {
                                                selectedSeats - seat // Deseleccionar
                                            } else if (selectedSeats.size < 4) {
                                                selectedSeats + seat // Seleccionar (máximo 4)
                                            } else {
                                                selectedSeats // No permitir más de 4
                                            }
                                        }
                                        // Para otros estados (vendido, bloqueado por otro), mostrar info
                                        else -> {
                                            scope.launch {
                                                val msg = when {
                                                    status.contains("VEND") -> {
                                                        // Pedir detalle puntual del asiento al ViewModel (suspend)
                                                        try {
                                                            val seatState = vm.getSeatState(eventId, seat.seatId) // suspend function in VM
                                                            val comprador = seatState["comprador"] as? Map<*, *>
                                                            val nombre = comprador?.get("persona") as? String ?: "desconocido"
                                                            val fecha = comprador?.get("fechaVenta") as? String ?: ""
                                                            "Asiento vendido a $nombre ${if (fecha.isNotBlank()) "el $fecha" else ""}"
                                                        } catch (ex: Exception) {
                                                            // Fallback si no se pudo obtener detalle
                                                            "Asiento vendido (datos no disponibles)"
                                                        }
                                                    }
                                                    status.contains("BLOQ") -> "Asiento bloqueado por otro usuario: ${seat.holder ?: "desconocido"}"
                                                    else -> "Asiento no disponible: $status"
                                                }
                                                snackbarHostState.showSnackbar(msg)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Botón flotante para mostrar opciones cuando hay asientos seleccionados
            if (selectedSeats.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showMultiDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Text("${selectedSeats.size}")
                }
            }

            // Dialog para acciones múltiples
            if (showMultiDialog && selectedSeats.isNotEmpty()) {
                val libreSeats = selectedSeats.filter {
                    val status = it.status?.uppercase()?.trim() ?: ""
                    status.contains("LIBRE") || status.contains("AVAILABLE") || status.contains("FREE")
                }
                val myBlockedSeats = selectedSeats.filter {
                    val status = it.status?.uppercase()?.trim() ?: ""
                    val mySession = SessionManager.getToken()
                    status.contains("BLOQ") && it.holder == mySession
                }

                AlertDialog(
                    onDismissRequest = {
                        if (!performingAction) {
                            showMultiDialog = false
                        }
                    },
                    title = {
                        Text(
                            "Asientos seleccionados (${selectedSeats.size})",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column {
                            Text("Asientos: ${selectedSeats.map { it.seatId }.joinToString(", ")}")
                            if (libreSeats.isNotEmpty() && myBlockedSeats.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("• Libres: ${libreSeats.size}", style = MaterialTheme.typography.bodySmall)
                                Text("• Bloqueados por ti: ${myBlockedSeats.size}", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (performingAction) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Procesando...")
                                }
                            } else {
                                Text("¿Qué deseas hacer?", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    confirmButton = {
                        if (!performingAction) {
                            Column {
                                // Mostrar bloquear solo si hay asientos libres
                                if (libreSeats.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = {
                                            performingAction = true
                                            scope.launch {
                                                try {
                                                    val seatIds = libreSeats.map { it.seatId }
                                                    ApiClient.blockMultipleSeats(eventId, seatIds)

                                                    // Marcar timestamps de bloqueo
                                                    seatIds.forEach { seatId ->
                                                        vm.markSeatAsBlocked(seatId)
                                                    }

                                                    snackbarHostState.showSnackbar("${seatIds.size} asientos bloqueados")
                                                    vm.loadSeats()
                                                    selectedSeats = emptySet()
                                                    showMultiDialog = false
                                                } catch (ex: Exception) {
                                                    snackbarHostState.showSnackbar("Error al bloquear: ${ex.message}")
                                                } finally {
                                                    performingAction = false
                                                }
                                            }
                                        }) { Text("Bloquear libres (${libreSeats.size})") }
                                    }
                                }

                                // Mostrar desbloquear solo si hay asientos bloqueados por mí
                                if (myBlockedSeats.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = {
                                            performingAction = true
                                            scope.launch {
                                                try {
                                                    val seatIds = myBlockedSeats.map { it.seatId }
                                                    ApiClient.unblockMultipleSeats(eventId, seatIds)

                                                    // Remover timestamps de desbloqueo
                                                    seatIds.forEach { seatId ->
                                                        vm.removeSeatTimestamp(seatId)
                                                    }

                                                    snackbarHostState.showSnackbar("${seatIds.size} asientos desbloqueados")
                                                    vm.loadSeats()
                                                    selectedSeats = emptySet()
                                                    showMultiDialog = false
                                                } catch (ex: Exception) {
                                                    snackbarHostState.showSnackbar("Error al desbloquear: ${ex.message}")
                                                } finally {
                                                    performingAction = false
                                                }
                                            }
                                        }) { Text("Desbloquear míos (${myBlockedSeats.size})") }
                                    }
                                }

                                // Botón de vender solo si hay asientos bloqueados por mí
                                if (myBlockedSeats.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = {
                                            showMultiDialog = false
                                            showBuyerDialog = true
                                        }) { Text("Vender bloqueados (${myBlockedSeats.size})") }
                                    }
                                }
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            if (!performingAction) {
                                showMultiDialog = false
                            }
                        }) {
                            Text("Cancelar")
                        }
                    }
                )
            }
        }

        // Diálogo para capturar datos del comprador
        if (showBuyerDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!performingAction) {
                        showBuyerDialog = false
                        buyerPersona = ""
                    }
                },
                title = { Text("Datos del Comprador") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Complete los datos para finalizar la venta:")

                        OutlinedTextField(
                            value = buyerPersona,
                            onValueChange = { buyerPersona = it },
                            label = { Text("Nombre y Apellido") },
                            placeholder = { Text("Ej: Juan Pérez") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !performingAction,
                            singleLine = true
                        )

                        val mySession = SessionManager.getToken()
                        // Mostrar solo los asientos SELECCIONADOS que están bloqueados por mí
                        val selectedBlockedSeats = selectedSeats.filter { seat ->
                            seat.status?.uppercase()?.contains("BLOQ") == true &&
                                    seat.holder == mySession
                        }

                        if (selectedBlockedSeats.isNotEmpty()) {
                            Text(
                                "Asientos a vender: ${selectedBlockedSeats.map { it.seatId }.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (buyerPersona.isNotBlank()) {
                                performingAction = true
                                scope.launch {
                                    try {
                                        val mySession = SessionManager.getToken()
                                        // CORRECCIÓN: Usar solo los asientos SELECCIONADOS que están bloqueados por mí
                                        val selectedBlockedSeats = selectedSeats.filter { seat ->
                                            seat.status?.uppercase()?.contains("BLOQ") == true &&
                                                    seat.holder == mySession
                                        }
                                        val seatIds = selectedBlockedSeats.map { it.seatId }

                                        // Llamar al nuevo método con datos del comprador
                                        ApiClient.purchaseMultipleSeatsWithBuyer(eventId, seatIds, buyerPersona)

                                        snackbarHostState.showSnackbar("${seatIds.size} asientos vendidos a $buyerPersona")
                                        vm.loadSeats()
                                        selectedSeats = emptySet()
                                        showBuyerDialog = false
                                        buyerPersona = ""
                                    } catch (ex: Exception) {
                                        snackbarHostState.showSnackbar("Error al vender: ${ex.message}")
                                    } finally {
                                        performingAction = false
                                    }
                                }
                            }
                        },
                        enabled = !performingAction && buyerPersona.isNotBlank()
                    ) {
                        if (performingAction) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text("Confirmar Venta")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        if (!performingAction) {
                            showBuyerDialog = false
                            buyerPersona = ""
                        }
                    }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
private fun SeatItem(seat: Seat, isSelected: Boolean = false, lockTimestamp: Long? = null, onClick: () -> Unit) {
    val baseColor = seatStatusColor(seat.status)
    val color = if (isSelected) Color(0xFF2196F3) else baseColor // Azul si está seleccionado
    
    // Calcular tiempo restante si es un bloqueo mío con timestamp
    var timeRemaining by remember { mutableStateOf<Int?>(null) }
    
    LaunchedEffect(lockTimestamp) {
        if (lockTimestamp != null) {
            while (true) {
                val currentTime = System.currentTimeMillis()
                val elapsedSeconds = ((currentTime - lockTimestamp) / 1000).toInt()
                val remainingSeconds = 300 - elapsedSeconds // 5 minutos = 300 segundos
                
                if (remainingSeconds <= 0) {
                    timeRemaining = null
                    break
                } else {
                    timeRemaining = remainingSeconds
                }
                
                delay(1000)
            }
        } else {
            timeRemaining = null
        }
    }
    
    Card(
        modifier = Modifier
            .size(56.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = color),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF1976D2)) else null
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = seat.seatId.take(6),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = if (isDarkTextNeeded(color)) Color.Black else Color.White,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                
                // Mostrar temporizador solo si hay timestamp y es mi bloqueo
                timeRemaining?.let { remaining ->
                    val mySession = SessionManager.getToken()
                    val isMyBlock = seat.status?.uppercase()?.contains("BLOQ") == true && 
                                   seat.holder == mySession
                    
                    if (isMyBlock) {
                        val minutes = remaining / 60
                        val seconds = remaining % 60
                        Text(
                            text = String.format("%d:%02d", minutes, seconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (remaining < 60) Color.Red else 
                                   if (isDarkTextNeeded(color)) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
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
