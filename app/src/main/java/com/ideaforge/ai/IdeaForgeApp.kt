package com.ideaforge.ai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.ideaforge.ai.core.constants.AppConstants
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class IdeaForgeApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        WorkManager.initialize(this, workManagerConfiguration)

        val channel = NotificationChannel(
            AppConstants.NOTIFICATION_CHANNEL_ID,
            "Build Progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows build progress notifications"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }
}
