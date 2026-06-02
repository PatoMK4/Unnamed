package com.unnamed.app.data.repository

import com.unnamed.app.data.local.dao.WorkoutDao
import com.unnamed.app.data.local.entity.Note
import com.unnamed.app.data.local.entity.SetEntry
import com.unnamed.app.data.local.entity.WorkoutSession
import com.unnamed.app.logging.ParsedSet
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first workout data access. Writes go to Room immediately; a future
 * sync worker will push unsynced rows to Supabase.
 */
class WorkoutRepository(private val dao: WorkoutDao) {

    /** Invoked after a successful commit so the app can trigger a sync. */
    var afterCommit: (() -> Unit)? = null

    fun observeSessions(): Flow<List<WorkoutSession>> = dao.observeSessions()

    fun observeSets(sessionId: String): Flow<List<SetEntry>> = dao.observeSets(sessionId)

    suspend fun startSession(title: String? = null): WorkoutSession {
        val session = WorkoutSession(title = title, startedAt = System.currentTimeMillis())
        dao.upsertSession(session)
        return session
    }

    /** Persist a user-confirmed parsed log into the current session. */
    suspend fun commit(sessionId: String, parsed: ParsedSet) {
        val exerciseId = parsed.exercise?.id
        val existing = if (exerciseId != null)
            dao.countSetsForExercise(sessionId, exerciseId) else 0

        val notes = buildList {
            parsed.noteText?.let { text ->
                add(
                    Note(
                        sessionId = sessionId,
                        exerciseId = exerciseId,
                        body = text,
                        tags = parsed.noteTags,
                    ),
                )
            }
        }

        // One set row per "set" the user logged (e.g. "3 sets of 8").
        repeat(parsed.sets) { i ->
            val set = SetEntry(
                sessionId = sessionId,
                exerciseId = exerciseId,
                setIndex = existing + i + 1,
                reps = parsed.reps,
                weightKg = parsed.weightKg,
                rpe = parsed.rpe,
                fatigue = parsed.fatigue,
                isBodyweight = parsed.exercise?.is_bodyweight ?: false,
            )
            // Attach notes only to the first set to avoid duplication.
            dao.commitLog(set, if (i == 0) notes else emptyList())
        }
        afterCommit?.invoke()
    }
}
