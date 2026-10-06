package com.ayush.streakforge.data

import android.content.Context
import androidx.work.WorkManager
import com.ayush.streakforge.*
import com.ayush.streakforge.db.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

data class UserFullState(
    val userLogin: String,
    val name: String,
    val avatarUrl: String,
    val bio: String,
    val createdAt: String,
    val followersCount: Int,
    val publicReposCount: Int,
    val lastSynced: Long,
    val planStart: String,
    val streakInfo: StreakInfo,
    val claimedRewards: Set<Int>,
    val customRewards: List<Reward>,
    val solvedDsa: Int,
    val dailyDsaGoal: Int,
    val restoresThisMonth: Int,
    val restoresLeft: Int,
    val isStale: Boolean,
    val remindersEnabled: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int
)

data class DeviceCodeResponse(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresIn: Int,
    val interval: Int
)

sealed interface OAuthPollResult {
    data class Success(val accessToken: String) : OAuthPollResult
    data object Pending : OAuthPollResult
    data class SlowDown(val extraSeconds: Int = 5) : OAuthPollResult
    data class Expired(val message: String = "Code expired") : OAuthPollResult
    data class Denied(val message: String = "Access denied") : OAuthPollResult
    data class Error(val message: String) : OAuthPollResult
}

class StreakRepository(
    private val context: Context,
    private val db: StreakDatabase = StreakDatabase.getInstance(context),
    private val tokenStorage: TokenStorage = TokenStorage(context)
) {
    private val userDao = db.userDao()
    private val contributionDao = db.contributionDao()
    private val rewardDao = db.rewardDao()
    private val customRewardDao = db.customRewardDao()

    fun getActiveUser(): String = tokenStorage.getActiveUser()

    fun getToken(user: String): String = tokenStorage.getToken(user)

    fun getUserStateFlow(userLogin: String): Flow<UserFullState?> {
        val userFlow = userDao.getUserFlow(userLogin)
        val contribFlow = contributionDao.getContributionsFlow(userLogin)
        val rewardsFlow = rewardDao.getClaimedRewardsFlow(userLogin)
        val customRewardsFlow = customRewardDao.getCustomRewardsFlow(userLogin)

        return combine(userFlow, contribFlow, rewardsFlow, customRewardsFlow) { userEntity, contribList, rewards, customEntities ->
            if (userEntity == null && contribList.isEmpty()) return@combine null

            val user = userEntity ?: UserEntity(userLogin)
            val contribDays = contribList.map { ContributionDay(it.date, it.count) }
            val streak = computeStreak(
                days = contribDays,
                totalContributions = user.totalContributions,
                shields = user.shields
            )

            val currentMonthKey = LocalDate.now().toString().substring(0, 7)
            val restoresUsed = if (user.restoresMonth == currentMonthKey) user.restoresUsed else 0
            val restoresLeft = (2 - restoresUsed).coerceAtLeast(0)

            val now = System.currentTimeMillis()
            val isStale = user.lastSynced > 0 && (now - user.lastSynced) > (6 * 3600 * 1000)

            val customMap = customEntities.associateBy { it.day }
            val effectiveRewards = REWARDS.map { defaultReward ->
                val custom = customMap[defaultReward.day]
                if (custom != null) {
                    Reward(day = custom.day, emoji = custom.emoji, label = custom.label, grantShield = custom.grantShield)
                } else {
                    defaultReward
                }
            }

            UserFullState(
                userLogin = userLogin,
                name = user.name.ifBlank { userLogin },
                avatarUrl = user.avatarUrl,
                bio = user.bio,
                createdAt = user.createdAt,
                followersCount = user.followersCount,
                publicReposCount = user.publicReposCount,
                lastSynced = user.lastSynced,
                planStart = user.planStart.ifBlank { LocalDate.now().toString() },
                streakInfo = streak,
                claimedRewards = rewards.toSet(),
                customRewards = effectiveRewards,
                solvedDsa = user.solvedDsa,
                dailyDsaGoal = user.dailyDsaGoal,
                restoresThisMonth = restoresUsed,
                restoresLeft = restoresLeft,
                isStale = isStale,
                remindersEnabled = user.remindersEnabled,
                reminderHour = user.reminderHour,
                reminderMinute = user.reminderMinute
            )
        }
    }


    suspend fun requestDeviceCode(): Result<DeviceCodeResponse> = withContext(Dispatchers.IO) {
        try {
            val clientId = BuildConfig.GITHUB_CLIENT_ID
            val url = URL("https://github.com/login/device/code")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

            val params = "client_id=$clientId&scope=read:user"
            conn.outputStream.use { it.write(params.toByteArray()) }

            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                return@withContext Result.failure(Exception("GitHub OAuth returned HTTP $code"))
            }

            val json = JSONObject(text)
            val deviceCode = json.optString("device_code", "")
            val userCode = json.optString("user_code", "")
            val verificationUri = json.optString("verification_uri", "https://github.com/login/device")
            val expiresIn = json.optInt("expires_in", 900)
            val interval = json.optInt("interval", 5)

            if (deviceCode.isBlank() || userCode.isBlank()) {
                return@withContext Result.failure(Exception("Invalid device code response"))
            }

            Result.success(
                DeviceCodeResponse(
                    deviceCode = deviceCode,
                    userCode = userCode,
                    verificationUri = verificationUri,
                    expiresIn = expiresIn,
                    interval = interval
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pollDeviceToken(deviceCode: String): OAuthPollResult = withContext(Dispatchers.IO) {
        try {
            val clientId = BuildConfig.GITHUB_CLIENT_ID
            val url = URL("https://github.com/login/oauth/access_token")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

            val params = "client_id=$clientId&device_code=$deviceCode&grant_type=urn:ietf:params:oauth:grant-type:device_code"
            conn.outputStream.use { it.write(params.toByteArray()) }

            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                return@withContext OAuthPollResult.Error("HTTP $code")
            }

            val json = JSONObject(text)
            if (json.has("access_token")) {
                val token = json.getString("access_token")
                return@withContext OAuthPollResult.Success(token)
            }

            val error = json.optString("error", "")
            when (error) {
                "authorization_pending" -> OAuthPollResult.Pending
                "slow_down" -> OAuthPollResult.SlowDown(5)
                "expired_token" -> OAuthPollResult.Expired("Device code expired. Please get a new code.")
                "access_denied" -> OAuthPollResult.Denied("Authorization request was cancelled.")
                else -> OAuthPollResult.Error(json.optString("error_description", "OAuth error"))
            }
        } catch (e: Exception) {
            OAuthPollResult.Error(e.message ?: "Connection error")
        }
    }

    suspend fun completeOAuthAndFetch(token: String): Result<String> = withContext(Dispatchers.IO) {
        val result = fetchViewerProfileAndContributions(token)
        if (result.isSuccess) {
            val userLogin = result.getOrThrow()
            tokenStorage.saveCredentials(userLogin, token)
            Result.success(userLogin)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Failed to fetch profile"))
        }
    }

    suspend fun saveCredentialsAndFetch(username: String, token: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim()
        val cleanToken = token.trim()
        if (cleanUser.isBlank() || cleanToken.isBlank()) {
            return@withContext Result.failure(Exception("Username and token cannot be empty"))
        }

        tokenStorage.saveCredentials(cleanUser, cleanToken)
        fetchAndStore(cleanUser, cleanToken)
    }

    suspend fun refreshActiveUser(): Result<Unit> = withContext(Dispatchers.IO) {
        val activeUser = tokenStorage.getActiveUser()
        val token = tokenStorage.getToken(activeUser)
        if (activeUser.isBlank() || token.isBlank()) {
            return@withContext Result.failure(Exception("Not logged in"))
        }
        fetchAndStore(activeUser, token)
    }

    private suspend fun fetchViewerProfileAndContributions(token: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val query = """
                query {
                  viewer {
                    login
                    name
                    avatarUrl
                    bio
                    createdAt
                    followers { totalCount }
                    repositories(ownerAffiliations: OWNER, isFork: false) { totalCount }
                    contributionsCollection {
                      contributionCalendar {
                        totalContributions
                        weeks {
                          contributionDays {
                            date
                            contributionCount
                          }
                        }
                      }
                    }
                  }
                }
            """.trimIndent()

            val body = JSONObject().put("query", query).toString()
            val conn = URL("https://api.github.com/graphql").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "StreakForge")
            conn.outputStream.use { it.write(body.toByteArray()) }

            val code = conn.responseCode
            if (code == 401) {
                return@withContext Result.failure(Exception("HTTP 401 Unauthorized: Invalid or revoked token."))
            }

            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                return@withContext Result.failure(Exception("GitHub returned HTTP $code"))
            }

            val json = JSONObject(text)
            if (json.has("errors")) {
                val msg = json.getJSONArray("errors").optJSONObject(0)?.optString("message") ?: "GitHub API Error"
                return@withContext Result.failure(Exception(msg))
            }

            val viewer = json.optJSONObject("data")?.optJSONObject("viewer")
                ?: return@withContext Result.failure(Exception("Invalid viewer response structure"))

            val login = viewer.optString("login", "")
            if (login.isBlank()) {
                return@withContext Result.failure(Exception("Could not retrieve user login"))
            }

            val name = viewer.optString("name", login)
            val avatarUrl = viewer.optString("avatarUrl", "")
            val bio = viewer.optString("bio", "")
            val createdAt = viewer.optString("createdAt", "")
            val followers = viewer.optJSONObject("followers")?.optInt("totalCount", 0) ?: 0
            val repos = viewer.optJSONObject("repositories")?.optInt("totalCount", 0) ?: 0

            val cal = viewer.optJSONObject("contributionsCollection")?.optJSONObject("contributionCalendar")
            val totalContribs = cal?.optInt("totalContributions", 0) ?: 0
            val weeks = cal?.optJSONArray("weeks") ?: JSONArray()
            val entities = mutableListOf<ContributionDayEntity>()

            for (w in 0 until weeks.length()) {
                val days = weeks.optJSONObject(w)?.optJSONArray("contributionDays") ?: continue
                for (d in 0 until days.length()) {
                    val dayObj = days.optJSONObject(d) ?: continue
                    val dateStr = dayObj.optString("date", "")
                    val count = dayObj.optInt("contributionCount", 0)
                    if (dateStr.isNotBlank()) {
                        entities.add(ContributionDayEntity("${login}_$dateStr", login, dateStr, count))
                    }
                }
            }

            contributionDao.insertAll(entities)

            val existingUser = userDao.getUser(login)
            val now = System.currentTimeMillis()
            val updatedUser = UserEntity(
                login = login,
                name = name,
                avatarUrl = avatarUrl,
                bio = bio,
                createdAt = createdAt,
                followersCount = followers,
                publicReposCount = repos,
                lastSynced = now,
                planStart = existingUser?.planStart ?: LocalDate.now().toString(),
                shields = existingUser?.shields ?: 0,
                restoresMonth = existingUser?.restoresMonth ?: "",
                restoresUsed = existingUser?.restoresUsed ?: 0,
                solvedDsa = existingUser?.solvedDsa ?: 0,
                totalContributions = totalContribs
            )
            userDao.insertOrUpdate(updatedUser)

            Result.success(login)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchAndStore(user: String, token: String): Result<Unit> = withContext(Dispatchers.IO) {
        val result = fetchViewerProfileAndContributions(token)
        if (result.isSuccess) {
            Result.success(Unit)
        } else {
            val err = result.exceptionOrNull()
            if (err?.message?.contains("401") == true) {
                tokenStorage.clearToken(user)
                WorkManager.getInstance(context).cancelUniqueWork("streak-refresh")
            }
            Result.failure(err ?: Exception("Fetch failed"))
        }
    }

    suspend fun claimReward(userLogin: String, day: Int, grantShield: Boolean) = withContext(Dispatchers.IO) {
        rewardDao.claimReward(ClaimedRewardEntity("${userLogin}_$day", userLogin, day, System.currentTimeMillis()))
        if (grantShield) {
            val user = userDao.getUser(userLogin) ?: UserEntity(userLogin)
            userDao.insertOrUpdate(user.copy(shields = user.shields + 1))
        }
    }

    suspend fun setSolvedDsa(userLogin: String, solved: Int) = withContext(Dispatchers.IO) {
        val user = userDao.getUser(userLogin) ?: UserEntity(userLogin)
        userDao.insertOrUpdate(user.copy(solvedDsa = solved.coerceAtLeast(0)))
    }

    suspend fun restoreStreak(userLogin: String): Result<Int> = withContext(Dispatchers.IO) {
        val user = userDao.getUser(userLogin) ?: return@withContext Result.failure(Exception("User not found"))
        val currentMonthKey = LocalDate.now().toString().substring(0, 7)
        val restoresUsed = if (user.restoresMonth == currentMonthKey) user.restoresUsed else 0

        if (restoresUsed >= 2) {
            return@withContext Result.failure(Exception("No restores remaining for this calendar month."))
        }

        val updated = user.copy(
            restoresMonth = currentMonthKey,
            restoresUsed = restoresUsed + 1
        )
        userDao.insertOrUpdate(updated)
        Result.success((2 - (restoresUsed + 1)).coerceAtLeast(0))
    }

    suspend fun exportUserDataJson(userLogin: String): String = withContext(Dispatchers.IO) {
        val user = userDao.getUser(userLogin)
        val contribs = contributionDao.getContributions(userLogin)
        val rewards = rewardDao.getClaimedRewards(userLogin)

        val json = JSONObject()
        json.put("username", userLogin)
        json.put("name", user?.name ?: "")
        json.put("exportedAt", LocalDate.now().toString())
        json.put("lastSynced", user?.lastSynced ?: 0L)
        json.put("shields", user?.shields ?: 0)
        json.put("solvedDsa", user?.solvedDsa ?: 0)
        json.put("claimedRewards", JSONArray(rewards))

        val contribArray = JSONArray()
        contribs.forEach { c ->
            contribArray.put(JSONObject().put("date", c.date).put("count", c.count))
        }
        json.put("contributions", contribArray)

        json.toString(2)
    }

    suspend fun saveCustomReward(userLogin: String, day: Int, emoji: String, label: String, grantShield: Boolean) = withContext(Dispatchers.IO) {
        customRewardDao.insertCustomReward(
            CustomRewardEntity(
                id = "${userLogin}_$day",
                userLogin = userLogin,
                day = day,
                emoji = emoji,
                label = label,
                grantShield = grantShield
            )
        )
    }

    suspend fun updateDsaGoal(userLogin: String, goal: Int) = withContext(Dispatchers.IO) {
        val user = userDao.getUser(userLogin) ?: UserEntity(userLogin)
        userDao.insertOrUpdate(user.copy(dailyDsaGoal = goal.coerceAtLeast(1)))
    }

    suspend fun setReminders(userLogin: String, enabled: Boolean, hour: Int, minute: Int) = withContext(Dispatchers.IO) {
        val user = userDao.getUser(userLogin) ?: UserEntity(userLogin)
        userDao.insertOrUpdate(user.copy(remindersEnabled = enabled, reminderHour = hour, reminderMinute = minute))
    }

    suspend fun signOut(userLogin: String) = withContext(Dispatchers.IO) {
        tokenStorage.clearToken(userLogin)
        if (tokenStorage.getActiveUser() == userLogin) {
            tokenStorage.setActiveUser("")
        }
        WorkManager.getInstance(context).cancelUniqueWork("streak-refresh")
        cancelDailyReminder(context)
    }

    suspend fun deleteUserData(userLogin: String) = withContext(Dispatchers.IO) {
        userDao.deleteUser(userLogin)
        contributionDao.deleteForUser(userLogin)
        rewardDao.deleteForUser(userLogin)
        customRewardDao.deleteForUser(userLogin)
        signOut(userLogin)
    }

    suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/Ayush07571/StreakForge/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "StreakForgeApp")

            val code = conn.responseCode
            if (code == 404) {
                return@withContext UpdateCheckResult.NoReleasesFound
            }
            if (code == 403) {
                return@withContext UpdateCheckResult.Error("Rate limit exceeded. Please try again later.")
            }
            if (code !in 200..299) {
                return@withContext UpdateCheckResult.Error("GitHub returned HTTP $code")
            }

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)

            val tagName = json.optString("tag_name", "")
            val htmlUrl = json.optString("html_url", "https://github.com/Ayush07571/StreakForge/releases")
            val notes = json.optString("body", "")

            val latestVer = if (tagName.startsWith("v")) tagName.substring(1) else tagName
            val currentVer = BuildConfig.VERSION_NAME

            if (latestVer.isNotBlank() && isVersionNewer(latestVer, currentVer)) {
                UpdateCheckResult.UpdateAvailable(latestVer, htmlUrl, notes)
            } else {
                UpdateCheckResult.AlreadyLatest(currentVer)
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Could not check for updates")
        }
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        return try {
            val latestParts = latest.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            val maxLen = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            false
        } catch (e: Exception) {
            latest != current
        }
    }
}

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val latestVersion: String, val releaseUrl: String, val notes: String) : UpdateCheckResult
    data class AlreadyLatest(val currentVersion: String) : UpdateCheckResult
    data object NoReleasesFound : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}
