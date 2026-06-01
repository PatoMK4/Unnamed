package com.unnamed.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Local (offline-first) tables. These mirror the Supabase schema in
 * supabase/migrations/0001_initial_schema.sql. Room is the source of truth;
 * sync to Supabase happens in the background (not yet wired).
 *
 * Canonical units: weight in kilograms; food mass (later) in grams.
 */

@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String? = null,
    val sessionDate: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val overallFatigue: Int? = null,     // 1..5
    val notes: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false,         // pushed to Supabase yet?
)

@Entity(
    tableName = "set_entries",
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SetEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val exerciseId: String?,             // slug into the bundled library
    val setIndex: Int = 1,
    val setType: String = "working",     // working|warmup|drop|amrap|backoff
    val reps: Int? = null,
    val weightKg: Double? = null,
    val rpe: Double? = null,             // 1..10 (.5 steps)
    val fatigue: Int? = null,            // 1..5
    val isBodyweight: Boolean = false,
    val addedLoadKg: Double? = null,
    val side: String? = null,            // left|right|both (unilateral)
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false,
)

@Entity(
    tableName = "notes",
    indices = [Index("sessionId"), Index("setEntryId")],
)
data class Note(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String? = null,
    val setEntryId: String? = null,
    val exerciseId: String? = null,
    val body: String,
    val tags: List<String> = emptyList(),  // e.g. [pain, shoulder]
    val severity: Int? = null,              // 1..5
    val createdAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false,
)
