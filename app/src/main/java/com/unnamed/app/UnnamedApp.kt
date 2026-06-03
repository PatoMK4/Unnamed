package com.unnamed.app

import android.app.Application
import com.unnamed.app.data.auth.AuthManager
import com.unnamed.app.data.exercise.ExerciseLibrary
import com.unnamed.app.data.local.AppDatabase
import com.unnamed.app.data.remote.Supabase
import com.unnamed.app.data.repository.StatsRepository
import com.unnamed.app.data.repository.WorkoutRepository
import com.unnamed.app.data.sync.SyncManager
import com.unnamed.app.logging.SetParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Minimal manual DI container. Holds the app-wide singletons. (If the project
 * grows, swap this for Hilt — see docs.)
 */
class UnnamedApp : Application() {
    lateinit var library: ExerciseLibrary
        private set
    lateinit var repository: WorkoutRepository
        private set
    lateinit var stats: StatsRepository
        private set
    lateinit var parser: SetParser
        private set
    lateinit var auth: AuthManager
        private set
    lateinit var sync: SyncManager
        private set

    /** Background scope for fire-and-forget sync work. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Supabase.init(this)
        library = ExerciseLibrary.get(this)
        val dao = AppDatabase.get(this).workoutDao()
        repository = WorkoutRepository(dao)
        stats = StatsRepository(dao, library)
        parser = SetParser(library)
        auth = AuthManager()
        sync = SyncManager(this, dao, auth)

        // After each confirmed log, opportunistically push if signed in.
        repository.afterCommit = { appScope.launch { sync.syncNow() } }

        // On launch, pull anything new from other devices.
        appScope.launch { sync.syncNow() }
    }
}
