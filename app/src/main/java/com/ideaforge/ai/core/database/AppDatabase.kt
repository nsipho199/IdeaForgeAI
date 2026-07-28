package com.ideaforge.ai.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        BuildHistoryEntity::class,
        BuildLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun buildHistoryDao(): BuildHistoryDao
    abstract fun buildLogDao(): BuildLogDao
}
