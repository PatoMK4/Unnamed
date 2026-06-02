package com.unnamed.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AccountScreen(vm: AccountViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Account", style = MaterialTheme.typography.headlineSmall)

        if (state.signedInEmail != null) {
            Text("Signed in as ${state.signedInEmail}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Your workouts sync to the cloud and across your devices.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = vm::signOut) { Text("Sign out") }
            return@Column
        }

        Text(
            "Optional — you can keep logging without an account. Sign in to back " +
                "up your data and sync across devices.",
            style = MaterialTheme.typography.bodyMedium,
        )

        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }

        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall) }

        Button(
            onClick = { vm.submit(email, password) },
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.loading) CircularProgressIndicator(
                modifier = Modifier.size(18.dp).padding(end = 4.dp), strokeWidth = 2.dp,
            )
            Text(if (state.isSignUp) "Create account" else "Sign in")
        }

        TextButton(onClick = vm::toggleMode, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(
                if (state.isSignUp) "Have an account? Sign in"
                else "New here? Create an account",
            )
        }
    }
}
