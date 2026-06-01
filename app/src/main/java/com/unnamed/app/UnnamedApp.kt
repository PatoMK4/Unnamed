package com.unnamed.app

import android.app.Application
import com.unnamed.app.data.exercise.ExerciseLibrary
import com.unnamed.app.data.local.AppDatabase
import com.unnamed.app.data.repository.WorkoutRepository
import com.unnamed.app.logging.SetParser

/**
 * Minimal manual DI container. Holds the app-wide singletons. (If the project
 * grows, swap this for Hilt — see docs.)
 */
class UnnamedApp : Application() {
    lateinit var library: ExerciseLibrary
        private set
    lateinit var repository: WorkoutRepository
        private set
    lateinit var parser: SetParser
        private set

    override fun onCreate() {
        super.onCreate()
        library = ExerciseLibrary.get(this)
        repository = WorkoutRepository(AppDatabase.get(this).workoutDao())
        parser = SetParser(library)
    }
}
