package com.unnamed.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.unnamed.app.UnnamedApp
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen() {
    val app = LocalContext.current.applicationContext as UnnamedApp
    val sessionsFlow = remember { app.repository.observeSessions() }
    val sessions by sessionsFlow.collectAsState(initial = emptyList())

    if (sessions.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("No sessions yet.", style = MaterialTheme.typography.bodyLarge)
            Text("Log a set on the Log tab and it'll show up here.",
                style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sessions) { s ->
            Card(Modifier.fillMaxSize()) {
                Column(Modifier.padding(16.dp)) {
                    Text(s.title ?: "Workout", style = MaterialTheme.typography.titleMedium)
                    Text(
                        DateFormat.getDateTimeInstance().format(Date(s.sessionDate)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
