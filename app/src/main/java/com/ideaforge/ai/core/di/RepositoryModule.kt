package com.ideaforge.ai.core.di

import com.ideaforge.ai.data.repository.BuildRepositoryImpl
import com.ideaforge.ai.data.repository.ProjectRepositoryImpl
import com.ideaforge.ai.domain.repository.BuildRepository
import com.ideaforge.ai.domain.repository.ProjectRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository

    @Binds
    @Singleton
    abstract fun bindBuildRepository(impl: BuildRepositoryImpl): BuildRepository
}
