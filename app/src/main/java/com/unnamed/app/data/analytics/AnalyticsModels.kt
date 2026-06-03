package com.unnamed.app.data.analytics

import com.unnamed.app.data.local.entity.Note
import com.unnamed.app.data.local.entity.SetEntry
import com.unnamed.app.data.local.entity.WorkoutSession

/**
 * Derived, read-side data structures. These are *computed* from the raw Room
 * entities (sessions, sets, notes) plus the bundled exercise library — they are
 * never persisted. Keeping them separate from the entities lets the storage
 * model stay lean while the UI gets rich, well-shaped stats to render.
 *
 * Canonical units: weight in kilograms; "volume load" = load(kg) × reps.
 */

/** A single set with its derived training metrics. */
data class SetMetrics(
    val set: SetEntry,
    /** Total external load moved: weight + any added load (vest/belt). */
    val loadKg: Double?,
    /** Volume load for this set (loadKg × reps), 0 when load is unknown. */
    val volumeLoad: Double,
    /** Estimated 1-rep max (Epley), null when weight or reps are missing. */
    val e1rm: Double?,
    /** Working sets count toward training stress; warm-ups don't. */
    val isWorking: Boolean,
)

/** All the sets for one exercise within a single session. */
data class ExerciseGroup(
    val exerciseId: String?,
    val name: String,
    val sets: List<SetMetrics>,
    val workingSets: Int,
    val totalVolume: Double,
    /** Best estimated 1RM across the group's sets. */
    val topE1rm: Double?,
    /** Heaviest load lifted in the group. */
    val topLoadKg: Double?,
)

/** A whole workout, summarized. */
data class SessionSummary(
    val session: WorkoutSession,
    val exercises: List<ExerciseGroup>,
    val totalVolume: Double,
    val totalSets: Int,
    val workingSets: Int,
    val exerciseCount: Int,
    /** Wall-clock minutes, when both start and end are known. */
    val durationMin: Long?,
    /** Direct working-set count per muscle (primary 1.0, secondary 0.5). */
    val muscleSets: List<MuscleVolume>,
    val topExerciseName: String?,
) {
    val isEmpty: Boolean get() = totalSets == 0
}

/** A session plus its notes — the payload for the detail screen. */
data class SessionDetail(
    val summary: SessionSummary,
    val notes: List<Note>,
)

/** Working sets attributed to a muscle over some window. */
data class MuscleVolume(
    val muscle: String,
    val sets: Double,
)

/** A best-ever mark for one exercise. */
data class PersonalRecord(
    val exerciseId: String,
    val name: String,
    val bestE1rm: Double?,
    val heaviestKg: Double?,
    val achievedAt: Long,
)

/** Top-level rollup shown on the Progress tab. */
data class ProgressOverview(
    val totalSessions: Int,
    val totalVolume: Double,
    val totalWorkingSets: Int,
    val weekSessions: Int,
    val weekVolume: Double,
    val weekWorkingSets: Int,
    val weekMuscleSets: List<MuscleVolume>,
    val records: List<PersonalRecord>,
) {
    val hasData: Boolean get() = totalSessions > 0
}
