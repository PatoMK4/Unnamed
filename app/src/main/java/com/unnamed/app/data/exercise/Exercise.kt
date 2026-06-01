package com.unnamed.app.data.exercise

import kotlinx.serialization.Serializable

/**
 * A canonical exercise from the bundled library (data/exercise_library.json).
 * This is the offline-first source of truth for exercise reference data.
 * `aliases` are what the chat parser matches spoken/typed phrases against.
 */
@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val movement_pattern: String? = null,
    val equipment: String? = null,
    val primary_muscles: List<String> = emptyList(),
    val secondary_muscles: List<String> = emptyList(),
    val is_bodyweight: Boolean = false,
    val allows_added_load: Boolean = true,
    val is_unilateral: Boolean = false,
    val category: String? = null,
    val mechanic: String? = null,
    val force: String? = null,
    val level: String? = null,
    val instructions: List<String> = emptyList(),
    val images: List<String> = emptyList(),
)

@Serializable
data class ExerciseLibraryFile(
    val schema_version: Int = 0,
    val exercises: List<Exercise> = emptyList(),
)
