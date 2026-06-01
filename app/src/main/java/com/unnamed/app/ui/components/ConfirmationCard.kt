package com.unnamed.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.unnamed.app.logging.ParsedSet

/**
 * Shows a parsed log as an editable card. Nothing is saved until the user taps
 * Confirm — this is what keeps the database clean despite a fuzzy parser.
 */
@Composable
fun ConfirmationCard(
    parsed: ParsedSet,
    onConfirm: (ParsedSet) -> Unit,
    onDiscard: (ParsedSet) -> Unit,
) {
    var reps by remember { mutableStateOf(parsed.reps?.toString() ?: "") }
    var sets by remember { mutableStateOf(parsed.sets.toString()) }
    var weight by remember { mutableStateOf(parsed.weightKg?.toString() ?: "") }
    var rpe by remember { mutableStateOf(parsed.rpe?.toString() ?: "") }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val title = parsed.exercise?.name ?: parsed.exercisePhrase.ifBlank { "Unknown exercise" }
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (parsed.exercise == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null)
                    Text("  Couldn't match this exercise — confirm or pick it.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Sets", sets, Modifier.weight(1f)) { sets = it }
                NumField("Reps", reps, Modifier.weight(1f)) { reps = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Weight (kg)", weight, Modifier.weight(1f)) { weight = it }
                NumField("RPE", rpe, Modifier.weight(1f)) { rpe = it }
            }

            parsed.noteText?.let { note ->
                Text("Note: $note", style = MaterialTheme.typography.bodyMedium)
                if (parsed.noteTags.isNotEmpty()) {
                    Text("Tags: ${parsed.noteTags.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall)
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = { onDiscard(parsed) }) { Text("Discard") }
                Button(onClick = {
                    onConfirm(
                        parsed.copy(
                            sets = sets.toIntOrNull() ?: 1,
                            reps = reps.toIntOrNull(),
                            weightKg = weight.toDoubleOrNull(),
                            rpe = rpe.toDoubleOrNull(),
                        ),
                    )
                }) { Text("Confirm") }
            }
        }
    }
}

@Composable
private fun NumField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
