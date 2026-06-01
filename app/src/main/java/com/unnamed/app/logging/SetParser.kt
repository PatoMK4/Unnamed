package com.unnamed.app.logging

import com.unnamed.app.data.exercise.Exercise
import com.unnamed.app.data.exercise.ExerciseLibrary

/**
 * A confirmed-before-commit draft parsed from one chat message.
 * Mirrors the eventual AI parser's output shape so the UI/commit flow won't
 * change when we swap this local heuristic for the Claude-backed parser.
 */
data class ParsedSet(
    val exercise: Exercise?,
    val exercisePhrase: String,
    val sets: Int = 1,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val rpe: Double? = null,
    val fatigue: Int? = null,
    val noteText: String? = null,
    val noteTags: List<String> = emptyList(),
)

/**
 * Offline heuristic parser — a placeholder for the LLM parser (Phase A in the
 * spec). Handles common phrasings like:
 *   "incline db press 3 sets of 8 at 80kg rpe 8, shoulder felt heavy"
 * It is intentionally forgiving; the UI always shows a confirmation card the
 * user edits before anything is saved, so imperfect parses are safe.
 */
class SetParser(private val library: ExerciseLibrary) {

    private val painWords = listOf("pain", "hurt", "tweak", "sore", "heavy",
        "bothered", "pinch", "ache", "uncomfortable")
    private val bodyParts = listOf("shoulder", "knee", "elbow", "wrist", "hip",
        "back", "lower back", "neck", "ankle", "bicep", "hamstring")

    fun parse(message: String): ParsedSet {
        val lower = message.lowercase()

        // sets x reps:  "3x8", "3 x 8", "3 sets of 8"
        var sets = 1
        var reps: Int? = null
        Regex("""(\d+)\s*(?:x|sets?\s*of)\s*(\d+)""").find(lower)?.let {
            sets = it.groupValues[1].toInt()
            reps = it.groupValues[2].toInt()
        } ?: Regex("""(\d+)\s*reps?""").find(lower)?.let {
            reps = it.groupValues[1].toInt()
        }

        // weight: "80kg", "at 80", "80 kilos"
        val weight = Regex("""(?:at\s*)?(\d+(?:\.\d+)?)\s*(?:kg|kilo|kilos|kgs)""")
            .find(lower)?.groupValues?.get(1)?.toDoubleOrNull()

        // rpe: "rpe 8", "@8"
        val rpe = Regex("""rpe\s*(\d+(?:\.\d+)?)""").find(lower)
            ?.groupValues?.get(1)?.toDoubleOrNull()

        // fatigue: "fatigue 3"
        val fatigue = Regex("""fatigue\s*(\d)""").find(lower)
            ?.groupValues?.get(1)?.toIntOrNull()

        // subjective note: anything after a comma, or a pain-word sentence
        val noteText = extractNote(message)
        val tags = buildList {
            if (noteText != null) {
                if (painWords.any { noteText.lowercase().contains(it) }) add("pain")
                bodyParts.firstOrNull { noteText.lowercase().contains(it) }?.let { add(it) }
            }
        }

        // exercise phrase: strip the numeric/keyword tail to isolate the name
        val phrase = isolateExercisePhrase(message)
        val exercise = library.resolve(phrase)

        return ParsedSet(
            exercise = exercise,
            exercisePhrase = phrase,
            sets = sets,
            reps = reps,
            weightKg = weight,
            rpe = rpe,
            fatigue = fatigue,
            noteText = noteText,
            noteTags = tags,
        )
    }

    private fun extractNote(message: String): String? {
        val afterComma = message.substringAfter(',', "").trim()
        if (afterComma.isNotEmpty()) return afterComma
        val lower = message.lowercase()
        return if (painWords.any { lower.contains(it) }) message.trim() else null
    }

    private fun isolateExercisePhrase(message: String): String {
        val head = message.substringBefore(',')
        // cut at the first number (where the set/rep/weight data starts)
        val cut = Regex("""\d""").find(head)?.range?.first ?: head.length
        return head.substring(0, cut).trim()
    }
}
