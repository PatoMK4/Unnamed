package com.unnamed.app.data.analytics

import com.unnamed.app.data.exercise.ExerciseLibrary
import com.unnamed.app.data.local.entity.SetEntry
import com.unnamed.app.data.local.entity.WorkoutSession

/**
 * The analytics engine: pure functions that turn raw entities + the exercise
 * library into the derived structures in [AnalyticsModels]. No Android, no I/O,
 * no state — trivially unit-testable.
 */
object Analytics {

    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    // --- per-set ------------------------------------------------------------

    /** Estimated 1RM via the Epley formula: w × (1 + reps/30). */
    fun epley1rm(loadKg: Double, reps: Int): Double =
        if (reps <= 1) loadKg else loadKg * (1.0 + reps / 30.0)

    fun metricsFor(set: SetEntry): SetMetrics {
        val load = when {
            set.weightKg != null -> set.weightKg + (set.addedLoadKg ?: 0.0)
            set.addedLoadKg != null -> set.addedLoadKg
            else -> null
        }
        val reps = set.reps
        val volume = if (load != null && reps != null) load * reps else 0.0
        val e1rm = if (load != null && load > 0 && reps != null && reps > 0)
            epley1rm(load, reps) else null
        return SetMetrics(
            set = set,
            loadKg = load,
            volumeLoad = volume,
            e1rm = e1rm,
            isWorking = set.setType != "warmup",
        )
    }

    // --- per-session --------------------------------------------------------

    fun summarize(
        session: WorkoutSession,
        sets: List<SetEntry>,
        library: ExerciseLibrary,
    ): SessionSummary {
        val groups = sets
            .groupBy { it.exerciseId }
            .map { (exerciseId, rows) ->
                val metrics = rows.sortedBy { it.setIndex }.map(::metricsFor)
                ExerciseGroup(
                    exerciseId = exerciseId,
                    name = exerciseId?.let { library.byId(it)?.name } ?: "Unknown exercise",
                    sets = metrics,
                    workingSets = metrics.count { it.isWorking },
                    totalVolume = metrics.sumOf { it.volumeLoad },
                    topE1rm = metrics.mapNotNull { it.e1rm }.maxOrNull(),
                    topLoadKg = metrics.mapNotNull { it.loadKg }.maxOrNull(),
                )
            }
            .sortedByDescending { it.totalVolume }

        val muscle = muscleSets(sets, library)

        return SessionSummary(
            session = session,
            exercises = groups,
            totalVolume = groups.sumOf { it.totalVolume },
            totalSets = sets.size,
            workingSets = groups.sumOf { it.workingSets },
            exerciseCount = groups.size,
            durationMin = durationMinutes(session),
            muscleSets = muscle,
            topExerciseName = groups.firstOrNull { it.totalVolume > 0 }?.name
                ?: groups.firstOrNull()?.name,
        )
    }

    private fun durationMinutes(session: WorkoutSession): Long? {
        val start = session.startedAt ?: return null
        val end = session.endedAt ?: return null
        if (end <= start) return null
        return (end - start) / 60000
    }

    /**
     * Attribute working sets to muscles: each primary muscle gets a full set,
     * each secondary muscle a half set (a common hypertrophy bookkeeping rule).
     */
    private fun muscleSets(sets: List<SetEntry>, library: ExerciseLibrary): List<MuscleVolume> {
        val tally = HashMap<String, Double>()
        for (set in sets) {
            if (set.setType == "warmup") continue
            val exercise = set.exerciseId?.let { library.byId(it) } ?: continue
            for (m in exercise.primary_muscles) tally[m] = (tally[m] ?: 0.0) + 1.0
            for (m in exercise.secondary_muscles) tally[m] = (tally[m] ?: 0.0) + 0.5
        }
        return tally.entries
            .map { MuscleVolume(it.key, it.value) }
            .sortedByDescending { it.sets }
    }

    // --- top-level overview -------------------------------------------------

    fun overview(
        sessions: List<WorkoutSession>,
        sets: List<SetEntry>,
        library: ExerciseLibrary,
        now: Long,
    ): ProgressOverview {
        val setsBySession = sets.groupBy { it.sessionId }
        val summaries = sessions.map { summarize(it, setsBySession[it.id].orEmpty(), library) }

        val weekCutoff = now - WEEK_MS
        val weekSummaries = summaries.filter { it.session.sessionDate >= weekCutoff }

        val weekMuscle = HashMap<String, Double>()
        for (s in weekSummaries) {
            for (mv in s.muscleSets) weekMuscle[mv.muscle] = (weekMuscle[mv.muscle] ?: 0.0) + mv.sets
        }

        return ProgressOverview(
            totalSessions = summaries.count { !it.isEmpty },
            totalVolume = summaries.sumOf { it.totalVolume },
            totalWorkingSets = summaries.sumOf { it.workingSets },
            weekSessions = weekSummaries.count { !it.isEmpty },
            weekVolume = weekSummaries.sumOf { it.totalVolume },
            weekWorkingSets = weekSummaries.sumOf { it.workingSets },
            weekMuscleSets = weekMuscle.entries
                .map { MuscleVolume(it.key, it.value) }
                .sortedByDescending { it.sets },
            records = personalRecords(sessions, sets, library),
        )
    }

    /** Best estimated 1RM and heaviest load per exercise, most impressive first. */
    fun personalRecords(
        sessions: List<WorkoutSession>,
        sets: List<SetEntry>,
        library: ExerciseLibrary,
    ): List<PersonalRecord> {
        val dateBySession = sessions.associate { it.id to it.sessionDate }
        return sets
            .filter { it.exerciseId != null }
            .groupBy { it.exerciseId!! }
            .mapNotNull { (exerciseId, rows) ->
                val metrics = rows.map { metricsFor(it) to (dateBySession[it.sessionId] ?: 0L) }
                val bestE1rm = metrics.mapNotNull { it.first.e1rm }.maxOrNull()
                val heaviest = metrics.mapNotNull { it.first.loadKg }.maxOrNull()
                if (bestE1rm == null && heaviest == null) return@mapNotNull null
                val achievedAt = metrics
                    .filter { it.first.e1rm == bestE1rm || it.first.loadKg == heaviest }
                    .maxOfOrNull { it.second } ?: 0L
                PersonalRecord(
                    exerciseId = exerciseId,
                    name = library.byId(exerciseId)?.name ?: "Unknown exercise",
                    bestE1rm = bestE1rm,
                    heaviestKg = heaviest,
                    achievedAt = achievedAt,
                )
            }
            .sortedByDescending { it.bestE1rm ?: it.heaviestKg ?: 0.0 }
    }
}
