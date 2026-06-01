package com.unnamed.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.unnamed.app.data.local.dao.WorkoutDao
import com.unnamed.app.data.local.entity.Note
import com.unnamed.app.data.local.entity.SetEntry
import com.unnamed.app.data.local.entity.WorkoutSession

@Database(
    entities = [WorkoutSession::class, SetEntry::class, Note::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "unnamed.db",
                ).build().also { instance = it }
            }
    }
}
