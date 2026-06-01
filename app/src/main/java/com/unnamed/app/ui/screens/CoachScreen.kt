package com.unnamed.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Placeholder for the AI Coach (Phase B+). Wires to the Supabase `ai-coach`
 * edge function once the Anthropic key is activated — kept inert until then so
 * there's no paid API usage.
 */
@Composable
fun CoachScreen() {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("AI Coach", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Coming soon. Once your data builds up and the AI is switched on, " +
                "you'll ask things like \"how's my bench trending?\" or \"build " +
                "today's session around my sore shoulder\" here.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
