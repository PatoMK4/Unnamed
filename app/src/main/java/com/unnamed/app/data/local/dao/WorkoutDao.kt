package com.unnamed.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.unnamed.app.data.local.entity.Note
import com.unnamed.app.data.local.entity.SetEntry
import com.unnamed.app.data.local.entity.WorkoutSession
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    @Query("SELECT * FROM workout_sessions ORDER BY sessionDate DESC")
    fun observeSessions(): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSession(id: String): WorkoutSession?

    @Query("SELECT * FROM set_entries WHERE sessionId = :sessionId ORDER BY setIndex")
    fun observeSets(sessionId: String): Flow<List<SetEntry>>

    @Query("SELECT COUNT(*) FROM set_entries WHERE sessionId = :sessionId AND exerciseId = :exerciseId")
    suspend fun countSetsForExercise(sessionId: String, exerciseId: String): Int

    @Upsert
    suspend fun upsertSession(session: WorkoutSession)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(set: SetEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note)

    /** Commit a confirmed log: the set plus any attached subjective notes. */
    @Transaction
    suspend fun commitLog(set: SetEntry, notes: List<Note>) {
        insertSet(set)
        notes.forEach { insertNote(it) }
    }

    // --- sync: push side -----------------------------------------------------
    @Query("SELECT * FROM workout_sessions WHERE synced = 0")
    suspend fun getUnsyncedSessions(): List<WorkoutSession>

    @Query("SELECT * FROM set_entries WHERE synced = 0")
    suspend fun getUnsyncedSets(): List<SetEntry>

    @Query("SELECT * FROM notes WHERE synced = 0")
    suspend fun getUnsyncedNotes(): List<Note>

    @Query("UPDATE workout_sessions SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSessionsSynced(ids: List<String>)

    @Query("UPDATE set_entries SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSetsSynced(ids: List<String>)

    @Query("UPDATE notes SET synced = 1 WHERE id IN (:ids)")
    suspend fun markNotesSynced(ids: List<String>)

    // --- sync: pull side -----------------------------------------------------
    @Upsert suspend fun upsertSessions(rows: List<WorkoutSession>)
    @Upsert suspend fun upsertSets(rows: List<SetEntry>)
    @Upsert suspend fun upsertNotes(rows: List<Note>)

    // --- sync: claim / reset -------------------------------------------------
    @Query("UPDATE workout_sessions SET synced = 0") suspend fun markAllSessionsUnsynced()
    @Query("UPDATE set_entries SET synced = 0") suspend fun markAllSetsUnsynced()
    @Query("UPDATE notes SET synced = 0") suspend fun markAllNotesUnsynced()

    @Transaction
    suspend fun markAllUnsynced() {
        markAllSessionsUnsynced(); markAllSetsUnsynced(); markAllNotesUnsynced()
    }

    @Query("DELETE FROM workout_sessions") suspend fun deleteAllSessions()
    @Query("DELETE FROM set_entries") suspend fun deleteAllSets()
    @Query("DELETE FROM notes") suspend fun deleteAllNotes()

    @Transaction
    suspend fun clearAll() {
        deleteAllNotes(); deleteAllSets(); deleteAllSessions()
    }
}
