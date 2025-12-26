@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EventDetailScreen(vm: EventDetailViewModel, eventId: Long) {
val ctx = LocalContext.current
val scope = rememberCoroutineScope()
val snackbarHostState = remember { SnackbarHostState() }

    var selectedSeats by remember { mutableStateOf<Set<Seat>>(emptySet()) }
    var performingAction by remember { mutableStateOf(false) }
    var showMultiDialog by remember { mutableStateOf(false) }

    // Variables para datos del comprador
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
                                                        try {
                                                            val seatState = vm.getSeatState(eventId, seat.seatId)
                                                            val comprador = seatState["comprador"] as? Map<*, *>
                                                            val nombre = comprador?.get("persona") as? String ?: "desconocido"
                                                            val fecha = comprador?.get("fechaVenta") as? String ?: ""
                                                            "Asiento vendido a $nombre ${if (fecha.isNotBlank()) "el $fecha" else ""}"
                                                        } catch (ex: Exception) {
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

            // Dialog para acciones múltiples (BLOQUEO)
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
                                                    if (seatIds.isNotEmpty()) {
                                                        ApiClient.blockSeats(eventId, seatIds)
                                                        seatIds.forEach { seatId ->
                                                            vm.markSeatAsBlocked(seatId)
                                                        }
                                                        snackbarHostState.showSnackbar("${seatIds.size} asientos bloqueados")
                                                        vm.loadSeats()
                                                        selectedSeats = emptySet()
                                                    }
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

        // Diálogo para capturar datos del comprador (VENTA)
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
                                        val selectedBlockedSeats = selectedSeats.filter { seat ->
                                            seat.status?.uppercase()?.contains("BLOQ") == true &&
                                                    seat.holder == mySession
                                        }
                                        val seatIds = selectedBlockedSeats.map { it.seatId }

                                        if (seatIds.isNotEmpty()) {
                                            ApiClient.purchaseSeatsWithBuyer(eventId, seatIds, buyerPersona)
                                            snackbarHostState.showSnackbar("${seatIds.size} asientos vendidos a $buyerPersona")
                                            vm.loadSeats()
                                            selectedSeats = emptySet()
                                            showBuyerDialog = false
                                            buyerPersona = ""
                                        }
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
