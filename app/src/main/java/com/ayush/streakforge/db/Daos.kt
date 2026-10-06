package com.ayush.streakforge.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE login = :login")
    fun getUserFlow(login: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE login = :login")
    suspend fun getUser(login: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(user: UserEntity)

    @Query("DELETE FROM users WHERE login = :login")
    suspend fun deleteUser(login: String)
}

@Dao
interface ContributionDao {
    @Query("SELECT * FROM contribution_days WHERE userLogin = :userLogin ORDER BY date ASC")
    fun getContributionsFlow(userLogin: String): Flow<List<ContributionDayEntity>>

    @Query("SELECT * FROM contribution_days WHERE userLogin = :userLogin ORDER BY date ASC")
    suspend fun getContributions(userLogin: String): List<ContributionDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contributions: List<ContributionDayEntity>)

    @Query("DELETE FROM contribution_days WHERE userLogin = :userLogin")
    suspend fun deleteForUser(userLogin: String)
}

@Dao
interface RewardDao {
    @Query("SELECT rewardDay FROM claimed_rewards WHERE userLogin = :userLogin")
    fun getClaimedRewardsFlow(userLogin: String): Flow<List<Int>>

    @Query("SELECT rewardDay FROM claimed_rewards WHERE userLogin = :userLogin")
    suspend fun getClaimedRewards(userLogin: String): List<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun claimReward(reward: ClaimedRewardEntity)

    @Query("DELETE FROM claimed_rewards WHERE userLogin = :userLogin")
    suspend fun deleteForUser(userLogin: String)
}

@Dao
interface CustomRewardDao {
    @Query("SELECT * FROM custom_rewards WHERE userLogin = :userLogin")
    fun getCustomRewardsFlow(userLogin: String): Flow<List<CustomRewardEntity>>

    @Query("SELECT * FROM custom_rewards WHERE userLogin = :userLogin")
    suspend fun getCustomRewards(userLogin: String): List<CustomRewardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomReward(reward: CustomRewardEntity)

    @Query("DELETE FROM custom_rewards WHERE userLogin = :userLogin")
    suspend fun deleteForUser(userLogin: String)
}
