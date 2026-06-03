package com.unnamed.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
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
import com.unnamed.app.data.analytics.PersonalRecord
import com.unnamed.app.ui.components.BarItem
import com.unnamed.app.ui.components.HorizontalBarChart
import com.unnamed.app.ui.components.SectionCard
import com.unnamed.app.ui.components.StatTile
import com.unnamed.app.ui.format.Format

@Composable
fun ProgressScreen() {
    val app = LocalContext.current.applicationContext as UnnamedApp
    val flow = remember { app.stats.observeOverview() }
    val overview by flow.collectAsState(initial = null)

    val o = overview
    if (o == null || !o.hasData) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Progress", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Log a few sessions and your training stats — volume, weekly sets " +
                    "per muscle, and personal records — will build up here.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        item {
            Text(
                "All time",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Sessions", o.totalSessions.toString(), Modifier.weight(1f))
                StatTile("Volume", Format.volume(o.totalVolume), Modifier.weight(1f))
                StatTile("Work sets", o.totalWorkingSets.toString(), Modifier.weight(1f))
            }
        }

        item {
            SectionCard("This week") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatTile("Sessions", o.weekSessions.toString(), Modifier.weight(1f))
                        StatTile("Volume", Format.volume(o.weekVolume), Modifier.weight(1f))
                        StatTile("Sets", o.weekWorkingSets.toString(), Modifier.weight(1f))
                    }
                    if (o.weekMuscleSets.isNotEmpty()) {
                        Text(
                            "Sets per muscle",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalBarChart(
                            o.weekMuscleSets.map {
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
        }

        if (o.records.isNotEmpty()) {
            item {
                Text(
                    "Personal records",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(o.records, key = { it.exerciseId }) { pr -> RecordRow(pr) }
        }
    }
}

@Composable
private fun RecordRow(pr: PersonalRecord) {
    SectionCard(pr.name) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column {
                Text("Best e1RM", style = MaterialTheme.typography.labelSmall)
                Text(
                    Format.e1rm(pr.bestE1rm),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column {
                Text("Heaviest", style = MaterialTheme.typography.labelSmall)
                Text(
                    Format.weight(pr.heaviestKg),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
