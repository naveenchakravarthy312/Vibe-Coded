package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [StageTimeRecord::class], version = 2, exportSchema = false)
abstract class WrcDatabase : RoomDatabase() {
    abstract fun stageTimeDao(): StageTimeDao

    companion object {
        @Volatile
        private var INSTANCE: WrcDatabase? = null

        fun getDatabase(context: Context): WrcDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WrcDatabase::class.java,
                    "wrc7_times.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
