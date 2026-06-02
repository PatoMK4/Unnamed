package com.unnamed.app.data.auth

import com.unnamed.app.data.remote.Supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin wrapper over Supabase Auth (email + password). Auth is optional in this
 * app — it's only needed to enable cross-device sync.
 */
class AuthManager {
    private val auth get() = Supabase.client.auth

    val sessionStatus: StateFlow<SessionStatus> get() = auth.sessionStatus

    fun currentUserId(): String? = auth.currentUserOrNull()?.id
    fun currentEmail(): String? = auth.currentUserOrNull()?.email

    suspend fun signUp(email: String, password: String) {
        auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() = auth.signOut()
}
