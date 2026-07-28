package com.ideaforge.ai.data.mapper

import com.ideaforge.ai.core.database.BuildHistoryEntity
import com.ideaforge.ai.core.database.ProjectEntity
import com.ideaforge.ai.domain.model.BuildHistoryItem
import com.ideaforge.ai.domain.model.Project
import com.ideaforge.ai.domain.model.ProjectStatus

object ProjectMapper {

    fun toDomain(entity: ProjectEntity): Project {
        return Project(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            idea = entity.idea,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            status = try {
                ProjectStatus.valueOf(entity.status)
            } catch (_: Exception) {
                ProjectStatus.DRAFT
            },
            apkPath = entity.apkPath,
            apkSize = entity.apkSize,
            versionName = entity.versionName,
            versionCode = entity.versionCode,
            minSdk = entity.minSdk,
            targetSdk = entity.targetSdk,
            packageName = entity.packageName,
            buildLogs = if (entity.buildLogs.isBlank()) emptyList()
            else entity.buildLogs.split("\n")
        )
    }

    fun toEntity(domain: Project): ProjectEntity {
        return ProjectEntity(
            id = domain.id,
            name = domain.name,
            description = domain.description,
            idea = domain.idea,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            status = domain.status.name,
            apkPath = domain.apkPath,
            apkSize = domain.apkSize,
            versionName = domain.versionName,
            versionCode = domain.versionCode,
            minSdk = domain.minSdk,
            targetSdk = domain.targetSdk,
            packageName = domain.packageName,
            buildLogs = domain.buildLogs.joinToString("\n")
        )
    }
}

object BuildHistoryMapper {

    fun toDomain(entity: BuildHistoryEntity): BuildHistoryItem {
        return BuildHistoryItem(
            id = entity.id,
            projectName = entity.projectName,
            idea = entity.idea,
            status = try {
                ProjectStatus.valueOf(entity.status)
            } catch (_: Exception) {
                ProjectStatus.DRAFT
            },
            apkPath = entity.apkPath,
            apkSize = entity.apkSize,
            buildDuration = entity.buildDuration,
            completedAt = entity.completedAt,
            errorMessage = entity.errorMessage,
            buildLogs = if (entity.buildLogs.isBlank()) emptyList()
            else entity.buildLogs.split("\n")
        )
    }

    fun toEntity(domain: BuildHistoryItem): BuildHistoryEntity {
        return BuildHistoryEntity(
            id = domain.id,
            projectName = domain.projectName,
            idea = domain.idea,
            status = domain.status.name,
            apkPath = domain.apkPath,
            apkSize = domain.apkSize,
            buildDuration = domain.buildDuration,
            completedAt = domain.completedAt,
            errorMessage = domain.errorMessage,
            buildLogs = domain.buildLogs.joinToString("\n")
        )
    }
}
