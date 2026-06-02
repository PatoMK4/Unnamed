package com.unnamed.app.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unnamed.app.UnnamedApp
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(
    val signedInEmail: String? = null,
    val isSignUp: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * Drives the optional account screen. Signing in claims local data and turns on
 * cross-device sync (see SyncManager.onSignIn).
 */
class AccountViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx = app as UnnamedApp
    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            appCtx.auth.sessionStatus.collect { status ->
                val email = if (status is SessionStatus.Authenticated) appCtx.auth.currentEmail() else null
                _state.update { it.copy(signedInEmail = email) }
            }
        }
    }

    fun toggleMode() = _state.update { it.copy(isSignUp = !it.isSignUp, error = null) }

    fun submit(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "Enter an email and password.") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                if (_state.value.isSignUp) appCtx.auth.signUp(email.trim(), password)
                else appCtx.auth.signIn(email.trim(), password)
                val userId = appCtx.auth.currentUserId()
                if (userId != null) appCtx.sync.onSignIn(userId)
            }
            _state.update {
                it.copy(
                    loading = false,
                    error = result.exceptionOrNull()?.let { e -> e.message ?: "Sign-in failed." },
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                appCtx.auth.signOut()
                appCtx.sync.onSignOut()
            }
        }
    }
}
