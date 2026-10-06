package com.ayush.streakforge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.ayush.streakforge.data.StreakRepository
import com.ayush.streakforge.db.StreakDatabase
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = StreakRepository(applicationContext)
        val activeUser = repo.getActiveUser()
        if (activeUser.isBlank()) return Result.success()

        val db = StreakDatabase.getInstance(applicationContext)
        val user = db.userDao().getUser(activeUser) ?: return Result.success()
        val contribs = db.contributionDao().getContributions(activeUser)
        val contribDays = contribs.map { ContributionDay(it.date, it.count) }
        val streak = computeStreak(contribDays, user.totalContributions, user.shields)

        if (!streak.todayDone && !streak.shieldActive) {
            showNotification(applicationContext)
        }
        return Result.success()
    }

    private fun showNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "streak_reminders"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Daily Streak Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminds you to push a commit before the day ends"
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.flame_lit)
            .setContentTitle("Keep the fire alive! 🔥")
            .setContentText("You haven't committed to GitHub today. Push a commit to preserve your streak!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(1001, notification)
    }
}

fun scheduleDailyReminder(context: Context, hour: Int = 20, minute: Int = 0) {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        if (before(now)) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }
    val initialDelay = target.timeInMillis - now.timeInMillis

    val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
        .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
        .build()

    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork("daily-reminder", ExistingPeriodicWorkPolicy.UPDATE, request)
}

fun cancelDailyReminder(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork("daily-reminder")
}
