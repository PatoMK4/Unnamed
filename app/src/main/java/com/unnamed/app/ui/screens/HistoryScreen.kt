package com.unnamed.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unnamed.app.UnnamedApp
import com.unnamed.app.data.analytics.SessionSummary
import com.unnamed.app.ui.components.StatChip
import com.unnamed.app.ui.format.Format

@Composable
fun HistoryScreen(onOpenSession: (String) -> Unit = {}) {
    val app = LocalContext.current.applicationContext as UnnamedApp
    val historyFlow = remember { app.stats.observeHistory() }
    val sessions by historyFlow.collectAsState(initial = emptyList())

    if (sessions.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("No sessions yet.", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Log a set on the Log tab and it'll show up here.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sessions, key = { it.session.id }) { summary ->
            SessionCard(summary, onClick = { onOpenSession(summary.session.id) })
        }
    }
}

@Composable
private fun SessionCard(summary: SessionSummary, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                summary.session.title ?: summary.topExerciseName ?: "Workout",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                Format.dateTime(summary.session.sessionDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (summary.isEmpty) {
                Text(
                    "No sets logged",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                return@Column
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatChip("Volume", Format.volume(summary.totalVolume))
                StatChip("Sets", summary.workingSets.toString())
                StatChip("Exercises", summary.exerciseCount.toString())
                Format.duration(summary.durationMin)?.let { StatChip("Time", it) }
            }
        }
    }
}
