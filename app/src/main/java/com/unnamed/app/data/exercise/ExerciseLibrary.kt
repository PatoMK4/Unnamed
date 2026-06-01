package com.unnamed.app.data.exercise

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * Loads the bundled exercise library once and indexes it for fast lookup,
 * including alias-based matching for the chat logger.
 */
class ExerciseLibrary private constructor(
    val all: List<Exercise>,
) {
    private val byId: Map<String, Exercise> = all.associateBy { it.id }

    /** Lowercased alias/name -> exercise, for exact-phrase matching. */
    private val byPhrase: Map<String, Exercise> = buildMap {
        for (e in all) {
            put(e.name.lowercase(), e)
            for (a in e.aliases) putIfAbsent(a.lowercase(), e)
        }
    }

    fun byId(id: String): Exercise? = byId[id]

    /**
     * Best-effort resolution of a free-text exercise phrase.
     * 1) exact alias/name hit, 2) substring containment, else null (the UI
     * then asks the user to confirm/pick — see the confirmation-card flow).
     */
    fun resolve(phrase: String): Exercise? {
        val p = phrase.trim().lowercase()
        if (p.isEmpty()) return null
        byPhrase[p]?.let { return it }
        return byPhrase.entries.firstOrNull { (k, _) -> p.contains(k) || k.contains(p) }?.value
    }

    companion object {
        @Volatile private var instance: ExerciseLibrary? = null

        fun get(context: Context): ExerciseLibrary =
            instance ?: synchronized(this) {
                instance ?: load(context).also { instance = it }
            }

        private fun load(context: Context): ExerciseLibrary {
            val json = context.assets.open("exercise_library.json")
                .bufferedReader().use { it.readText() }
            val parser = Json { ignoreUnknownKeys = true }
            val file = parser.decodeFromString(ExerciseLibraryFile.serializer(), json)
            return ExerciseLibrary(file.exercises)
        }
    }
}
