package com.yassuff.cinemobile.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview

class EventDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val eventId = intent?.getLongExtra("eventId", -1) ?: -1L
        setContent {
            EventDetailScreen(eventId)
        }
    }
}

@Composable
fun EventDetailScreen(eventId: Long) {
    Text(text = "Detalle del evento: $eventId")
}

@Preview(showBackground = true)
@Composable
fun PreviewDetail() {
    EventDetailScreen(123)
}