package com.ayush.streakforge

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.*
import com.ayush.streakforge.data.StreakRepository
import java.util.concurrent.TimeUnit

class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repository = StreakRepository(applicationContext)
        val activeUser = repository.getActiveUser()
        val token = repository.getToken(activeUser)
        if (activeUser.isBlank() || token.isBlank()) return Result.success()

        val result = repository.refreshActiveUser()
        return if (result.isSuccess) {
            StreakWidget().updateAll(applicationContext)
            Result.success()
        } else {
            val err = result.exceptionOrNull()?.message ?: ""
            if (err.contains("401")) {
                StreakWidget().updateAll(applicationContext)
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }
}

fun scheduleRefresh(ctx: Context) {
    val request = PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    WorkManager.getInstance(ctx)
        .enqueueUniquePeriodicWork("streak-refresh", ExistingPeriodicWorkPolicy.UPDATE, request)
}
