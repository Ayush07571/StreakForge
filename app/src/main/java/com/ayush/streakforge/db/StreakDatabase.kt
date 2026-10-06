package com.ayush.streakforge.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        ContributionDayEntity::class,
        ClaimedRewardEntity::class,
        CustomRewardEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class StreakDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun contributionDao(): ContributionDao
    abstract fun rewardDao(): RewardDao
    abstract fun customRewardDao(): CustomRewardDao

    companion object {
        @Volatile
        private var INSTANCE: StreakDatabase? = null

        fun getInstance(context: Context): StreakDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StreakDatabase::class.java,
                    "streak_forge_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
