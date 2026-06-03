package com.unnamed.app.data.repository

import com.unnamed.app.data.analytics.Analytics
import com.unnamed.app.data.analytics.ProgressOverview
import com.unnamed.app.data.analytics.SessionDetail
import com.unnamed.app.data.analytics.SessionSummary
import com.unnamed.app.data.exercise.ExerciseLibrary
import com.unnamed.app.data.local.dao.WorkoutDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Read-side analytics access. Composes the raw Room flows through the pure
 * [Analytics] engine so the UI observes ready-to-render summaries. Reactive:
 * any logged set re-emits updated stats automatically.
 */
class StatsRepository(
    private val dao: WorkoutDao,
    private val library: ExerciseLibrary,
) {

    /** Every session, summarized, newest first (drives the History list). */
    fun observeHistory(): Flow<List<SessionSummary>> =
        combine(dao.observeSessions(), dao.observeAllSets()) { sessions, sets ->
            val bySession = sets.groupBy { it.sessionId }
            sessions.map { Analytics.summarize(it, bySession[it.id].orEmpty(), library) }
        }

    /** One session in full, with its notes (drives the Session Detail screen). */
    fun observeSession(sessionId: String): Flow<SessionDetail?> =
        combine(
            dao.observeSession(sessionId),
            dao.observeSets(sessionId),
            dao.observeNotes(sessionId),
        ) { session, sets, notes ->
            session?.let {
                SessionDetail(Analytics.summarize(it, sets, library), notes)
            }
        }

    /** Top-level rollup for the Progress tab. */
    fun observeOverview(): Flow<ProgressOverview> =
        combine(dao.observeSessions(), dao.observeAllSets()) { sessions, sets ->
            Analytics.overview(sessions, sets, library, System.currentTimeMillis())
        }
}
