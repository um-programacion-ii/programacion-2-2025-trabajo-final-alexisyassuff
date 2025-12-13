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
                title = { Text("Mis Ventas") },
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
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(6.dp)
        .clickable { onClick() }) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Venta",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = if (sale.resultado) Icons.Filled.CheckCircle else Icons.Filled.Close,
                    contentDescription = if (sale.resultado) "Exitosa" else "Fallida",
                    tint = if (sale.resultado) Color.Green else Color.Red
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Evento: ${sale.eventoId}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Fecha: ${formatDateTime(sale.fechaVenta)}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Precio: $${String.format("%.2f", sale.precioVenta)}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Asientos: ${sale.cantidadAsientos}", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = sale.descripcion,
                style = MaterialTheme.typography.bodySmall,
                color = if (sale.resultado) Color.Green else Color.Red
            )
        }
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
