package com.unnamed.app.data.remote

import android.content.Context
import com.unnamed.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.json.Json

/**
 * Supabase client (auth + postgrest). The publishable key is public by design;
 * Row-Level Security protects all data. See supabase/README.md.
 *
 * Call [init] once from the Application before use. Sessions persist across
 * launches via [SharedPrefsSessionManager] so users stay signed in.
 */
object Supabase {
    lateinit var client: SupabaseClient
        private set

    fun init(context: Context) {
        if (::client.isInitialized) return
        client = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY,
        ) {
            install(Auth) {
                sessionManager = SharedPrefsSessionManager(context.applicationContext)
            }
            install(Postgrest)
        }
    }
}

/**
 * Persists the auth session in SharedPreferences (no extra dependency).
 * Avoids relying on the platform default session manager.
 */
private class SharedPrefsSessionManager(context: Context) : SessionManager {
    private val prefs = context.getSharedPreferences("unnamed_auth", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val key = "session"

    override suspend fun saveSession(session: UserSession) {
        prefs.edit().putString(key, json.encodeToString(UserSession.serializer(), session)).apply()
    }

    override suspend fun loadSession(): UserSession? {
        val raw = prefs.getString(key, null) ?: return null
        return runCatching { json.decodeFromString(UserSession.serializer(), raw) }.getOrNull()
    }

    override suspend fun deleteSession() {
        prefs.edit().remove(key).apply()
    }
}
