package com.ideaforge.ai.core.di

import android.content.Context
import androidx.room.Room
import com.ideaforge.ai.core.constants.AppConstants
import com.ideaforge.ai.core.database.AppDatabase
import com.ideaforge.ai.core.database.BuildHistoryDao
import com.ideaforge.ai.core.database.BuildLogDao
import com.ideaforge.ai.core.database.ProjectDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppConstants.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideProjectDao(database: AppDatabase): ProjectDao {
        return database.projectDao()
    }

    @Provides
    fun provideBuildHistoryDao(database: AppDatabase): BuildHistoryDao {
        return database.buildHistoryDao()
    }

    @Provides
    fun provideBuildLogDao(database: AppDatabase): BuildLogDao {
        return database.buildLogDao()
    }
}
