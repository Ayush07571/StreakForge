package com.ayush.streakforge

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.*
import java.util.concurrent.TimeUnit

class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val user = Store.username(applicationContext)
        val token = Store.token(applicationContext)
        if (user.isBlank() || token.isBlank()) return Result.success()
        return try {
            val shields = Store.shields(applicationContext)
            Store.saveStreak(applicationContext, GitHub.fetch(user, token, shields))
            StreakWidget().updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
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
