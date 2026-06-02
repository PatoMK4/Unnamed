package com.unnamed.app.data.sync

import android.content.Context
import android.util.Log
import com.unnamed.app.data.auth.AuthManager
import com.unnamed.app.data.local.dao.WorkoutDao
import com.unnamed.app.data.remote.Supabase
import com.unnamed.app.data.remote.dto.NoteDto
import com.unnamed.app.data.remote.dto.SetEntryDto
import com.unnamed.app.data.remote.dto.WorkoutSessionDto
import com.unnamed.app.data.remote.toDto
import com.unnamed.app.data.remote.toEntity
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * Offline-first two-way sync between Room and Supabase.
 *
 * Push: rows with synced=0 → upsert (stamped with user_id) → mark synced.
 * Pull: rows changed since lastSync (RLS scopes to the user) → upsert into Room.
 * Conflict policy: last-write-wins; local unsynced rows are never clobbered.
 *
 * Triggered opportunistically (sign-in, app start, after a commit, manual).
 */
class SyncManager(
    context: Context,
    private val dao: WorkoutDao,
    private val auth: AuthManager,
) {
    private val prefs = context.getSharedPreferences("unnamed_sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private var lastSync: Long
        get() = prefs.getLong("lastSync", 0L)
        set(v) = prefs.edit().putLong("lastSync", v).apply()

    private var localOwner: String?
        get() = prefs.getString("localOwner", null)
        set(v) = prefs.edit().putString("localOwner", v).apply()

    /** Full sync if signed in; no-op otherwise. Safe to call frequently. */
    suspend fun syncNow() {
        val userId = auth.currentUserId() ?: return
        mutex.withLock {
            runCatching {
                val started = System.currentTimeMillis()
                pushAll(userId)
                pullSince(lastSync)
                lastSync = started
            }.onFailure { Log.w(TAG, "sync failed", it) }
        }
    }

    /** Reconcile local data with the account that just signed in. */
    suspend fun onSignIn(userId: String) {
        when (localOwner) {
            null -> {                       // never claimed → claim local data
                dao.markAllUnsynced()
                localOwner = userId
                lastSync = 0L
            }
            userId -> { /* same user → just sync below */ }
            else -> {                       // different user on this device → reset
                dao.clearAll()
                localOwner = userId
                lastSync = 0L
            }
        }
        syncNow()
    }

    /** Keep local data + owner so re-sign-in resyncs; cross-user handled above. */
    fun onSignOut() { /* intentionally no wipe */ }

    // --- push -----------------------------------------------------------------
    private suspend fun pushAll(userId: String) {
        val client = Supabase.client
        val sessions = dao.getUnsyncedSessions()
        if (sessions.isNotEmpty()) {
            client.from("workout_sessions").upsert(sessions.map { it.toDto(userId) }) { onConflict = "id" }
            dao.markSessionsSynced(sessions.map { it.id })
        }
        val sets = dao.getUnsyncedSets()
        if (sets.isNotEmpty()) {
            client.from("set_entries").upsert(sets.map { it.toDto(userId) }) { onConflict = "id" }
            dao.markSetsSynced(sets.map { it.id })
        }
        val notes = dao.getUnsyncedNotes()
        if (notes.isNotEmpty()) {
            client.from("notes").upsert(notes.map { it.toDto(userId) }) { onConflict = "id" }
            dao.markNotesSynced(notes.map { it.id })
        }
    }

    // --- pull -----------------------------------------------------------------
    private suspend fun pullSince(since: Long) {
        val client = Supabase.client
        val iso = Instant.ofEpochMilli(since).toString()

        val pendingSessions = dao.getUnsyncedSessions().map { it.id }.toSet()
        val remoteSessions = client.from("workout_sessions")
            .select { filter { gt("updated_at", iso) } }
            .decodeList<WorkoutSessionDto>()
            .filter { it.id !in pendingSessions }
        if (remoteSessions.isNotEmpty()) dao.upsertSessions(remoteSessions.map { it.toEntity() })

        val pendingSets = dao.getUnsyncedSets().map { it.id }.toSet()
        val remoteSets = client.from("set_entries")
            .select { filter { gt("updated_at", iso) } }
            .decodeList<SetEntryDto>()
            .filter { it.id !in pendingSets }
        if (remoteSets.isNotEmpty()) dao.upsertSets(remoteSets.map { it.toEntity() })

        val pendingNotes = dao.getUnsyncedNotes().map { it.id }.toSet()
        val remoteNotes = client.from("notes")
            .select { filter { gt("created_at", iso) } }
            .decodeList<NoteDto>()
            .filter { it.id !in pendingNotes }
        if (remoteNotes.isNotEmpty()) dao.upsertNotes(remoteNotes.map { it.toEntity() })
    }

    private companion object { const val TAG = "SyncManager" }
}
