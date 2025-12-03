package com.yassuff.cinemobile.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cine.shared.ApiClient
import com.cine.shared.Seat
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel

class EventDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val eventId = intent?.getLongExtra("eventId", 1L) ?: 1L
        setContent {
            val vm: EventDetailViewModel = viewModel(factory = EventDetailViewModelFactory(eventId))
            EventDetailScreen(vm)
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

    private val _selected = mutableStateListOf<String>()
    val selected: List<String> get() = _selected

    init { loadSeats() }

    fun loadSeats() {
        loading = true; error = null
        viewModelScope.launch {
            try {
                seats = ApiClient.getSeats(eventId)
            } catch (ex: Exception) {
                ex.printStackTrace()
                error = ex.message ?: "Error al cargar asientos"
            } finally {
                loading = false
            }
        }
    }

    fun toggle(seatId: String) {
        if (_selected.contains(seatId)) _selected.remove(seatId) else _selected.add(seatId)
    }
}

class EventDetailViewModelFactory(private val eventId: Long) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return EventDetailViewModel(eventId) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(vm: EventDetailViewModel) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column {
            TopAppBar(title = { Text("Detalle evento") })
            if (vm.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.padding(8.dp)) {
                    items(vm.seats) { seat ->
                        val status = seat.status.uppercase()
                        val color = when {
                            vm.selected.contains(seat.seatId) -> Color(0xFF4CAF50)
                            status == "VENDIDO" -> Color.Gray
                            status == "BLOQUEADO" -> Color.Yellow
                            else -> Color.LightGray
                        }
                        Box(modifier = Modifier
                            .padding(6.dp)
                            .size(80.dp)
                            .background(color)
                            .clickable(enabled = status == "LIBRE" || vm.selected.contains(seat.seatId)) { vm.toggle(seat.seatId) }) {
                            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Text(text = seat.seatId)
                                Text(text = seat.status, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            vm.error?.let { Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
        }
    }
}
