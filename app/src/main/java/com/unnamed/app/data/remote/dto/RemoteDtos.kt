package com.unnamed.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serialized shapes for the Supabase (PostgREST) tables. Field names use
 * @SerialName to match the snake_case Postgres columns in
 * supabase/migrations/0001_initial_schema.sql exactly.
 *
 * Timestamps are ISO-8601 strings (timestamptz); session_date is a "yyyy-MM-dd"
 * date string. `created_at` is omitted where the DB default manages it.
 * `user_id` is set on push to satisfy RLS (`with check user_id = auth.uid()`).
 */

@Serializable
data class WorkoutSessionDto(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val title: String? = null,
    @SerialName("session_date") val sessionDate: String,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("overall_fatigue") val overallFatigue: Int? = null,
    val notes: String? = null,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class SetEntryDto(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("session_id") val sessionId: String,
    @SerialName("exercise_id") val exerciseId: String? = null,
    @SerialName("set_index") val setIndex: Int = 1,
    @SerialName("set_type") val setType: String = "working",
    val reps: Int? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    val rpe: Double? = null,
    val fatigue: Int? = null,
    @SerialName("is_bodyweight") val isBodyweight: Boolean = false,
    @SerialName("added_load_kg") val addedLoadKg: Double? = null,
    val side: String? = null,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class NoteDto(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("set_entry_id") val setEntryId: String? = null,
    @SerialName("exercise_id") val exerciseId: String? = null,
    val body: String,
    val tags: List<String> = emptyList(),
    val severity: Int? = null,
    // notes has no updated_at column; created_at doubles as the sync cursor.
    @SerialName("created_at") val createdAt: String,
)
