package com.unnamed.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unnamed.app.UnnamedApp
import com.unnamed.app.data.analytics.ExerciseGroup
import com.unnamed.app.data.analytics.SessionDetail
import com.unnamed.app.data.local.entity.Note
import com.unnamed.app.ui.components.BarItem
import com.unnamed.app.ui.components.HorizontalBarChart
import com.unnamed.app.ui.components.SectionCard
import com.unnamed.app.ui.components.StatChip
import com.unnamed.app.ui.format.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(sessionId: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as UnnamedApp
    val flow = remember(sessionId) { app.stats.observeSession(sessionId) }
    val detail by flow.collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(detail?.summary?.session?.title ?: "Session")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val d = detail
        if (d == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("Loading…", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }
        SessionDetailBody(d, Modifier.padding(padding))
    }
}

@Composable
private fun SessionDetailBody(detail: SessionDetail, modifier: Modifier = Modifier) {
    val summary = detail.summary
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        item {
            Text(
                Format.dateTime(summary.session.sessionDate),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Top-line stats.
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatChip("Volume", Format.volume(summary.totalVolume))
                StatChip("Work sets", summary.workingSets.toString())
                StatChip("Exercises", summary.exerciseCount.toString())
                Format.duration(summary.durationMin)?.let { StatChip("Time", it) }
                summary.session.overallFatigue?.let { StatChip("Fatigue", "$it/5") }
            }
        }

        // Muscle breakdown.
        if (summary.muscleSets.isNotEmpty()) {
            item {
                SectionCard("Muscles worked") {
                    HorizontalBarChart(
                        summary.muscleSets.map {
                            BarItem(
                                label = Format.label(it.muscle),
                                value = it.sets.toFloat(),
                                valueLabel = Format.number(it.sets),
                            )
                        },
                    )
                }
            }
        }

        // Per-exercise breakdown.
        items(summary.exercises, key = { it.exerciseId ?: it.name }) { group ->
            ExerciseSection(group)
        }

        // Notes.
        if (detail.notes.isNotEmpty()) {
            item {
                SectionCard("Notes") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        detail.notes.forEach { NoteRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseSection(group: ExerciseGroup) {
    SectionCard(group.name) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                group.topE1rm?.let { StatChip("Top e1RM", Format.e1rm(it)) }
                StatChip("Volume", Format.volume(group.totalVolume))
                StatChip("Sets", group.workingSets.toString())
            }
            group.sets.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Set ${m.set.setIndex}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(52.dp),
                    )
                    Text(
                        Format.setLine(m.set.reps, m.loadKg),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    val trailing = buildList {
                        m.e1rm?.let { add("e1RM ${Format.number(it)}") }
                        m.set.rpe?.let { add("RPE ${Format.number(it)}") }
                        if (m.set.setType != "working") add(m.set.setType)
                    }.joinToString(" · ")
                    if (trailing.isNotEmpty()) {
                        Text(
                            trailing,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: Note) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(note.body, style = MaterialTheme.typography.bodyMedium)
            if (note.tags.isNotEmpty()) {
                Text(
                    note.tags.joinToString(" ") { "#${it}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
