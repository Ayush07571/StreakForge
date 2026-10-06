package com.ayush.streakforge.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val login: String,
    val name: String = "",
    val avatarUrl: String = "",
    val bio: String = "",
    val createdAt: String = "",
    val followersCount: Int = 0,
    val publicReposCount: Int = 0,
    val lastSynced: Long = 0L,
    val planStart: String = "",
    val shields: Int = 0,
    val restoresMonth: String = "",
    val restoresUsed: Int = 0,
    val solvedDsa: Int = 0,
    val totalContributions: Int = 0,
    val dailyDsaGoal: Int = 2,
    val remindersEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0
)

@Entity(tableName = "contribution_days")
data class ContributionDayEntity(
    @PrimaryKey val id: String, // "${userLogin}_${date}"
    val userLogin: String,
    val date: String, // "YYYY-MM-DD"
    val count: Int
)

@Entity(tableName = "claimed_rewards")
data class ClaimedRewardEntity(
    @PrimaryKey val id: String, // "${userLogin}_${rewardDay}"
    val userLogin: String,
    val rewardDay: Int,
    val claimedAt: Long
)

@Entity(tableName = "custom_rewards")
data class CustomRewardEntity(
    @PrimaryKey val id: String, // "${userLogin}_${day}"
    val userLogin: String,
    val day: Int,
    val emoji: String,
    val label: String,
    val grantShield: Boolean = false
)
