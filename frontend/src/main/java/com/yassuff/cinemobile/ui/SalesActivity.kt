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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cine.shared.ApiClient
import com.cine.shared.Sale
import com.cine.shared.SessionManager
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

class SalesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: SalesViewModel = viewModel()
            SalesScreen(vm) {
                finish()
            }
        }
    }
}

class SalesViewModel : ViewModel() {
    var sales by mutableStateOf<List<Sale>>(emptyList())
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
                sales = ApiClient.getSales()
            } catch (ex: Exception) {
                ex.printStackTrace()
                error = ex.message ?: "Error cargando ventas"
            } finally {
                loading = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(vm: SalesViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize()) {
        Column {
            TopAppBar(
                title = { Text("Ventas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // logout: limpiar sesión y volver a LoginActivity
                        SessionManager.clear()
                        val intent = Intent(ctx, LoginActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        ctx.startActivity(intent)
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Logout")
                    }
                }
            )

            if (vm.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                if (vm.sales.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No hay ventas para mostrar")
                    }
                } else {
                    LazyColumn(modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)) {
                        items(vm.sales) { sale ->
                            SaleCard(sale, onClick = { 
                                // TODO: Navigate to sale detail
                            })
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
private fun SaleCard(sale: Sale, onClick: () -> Unit) {
    val colorFondo = getColorForEvent(sale.evento) // Obtenemos el color de fondo según el evento
    val iconTint = Color(0xFF4CAF50) // Verde para el check

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = colorFondo)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Venta realizada", // Mostramos el asiento como identificador
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Venta realizada correctamente",
                    tint = iconTint
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Evento: ${sale.evento}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Fecha: ${formatDateTime(sale.fechaVenta)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Asiento: ${sale.asiento}",
                style = MaterialTheme.typography.bodySmall
            )

            sale.comprador?.let { comprador ->
                Text(
                    text = "Comprador: $comprador",
                    style = MaterialTheme.typography.bodySmall
                )
            }
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
