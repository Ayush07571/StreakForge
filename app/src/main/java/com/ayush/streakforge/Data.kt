package com.ayush.streakforge

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class ContributionDay(val date: String, val count: Int)

data class StreakInfo(
    val current: Int,
    val longest: Int,
    val todayDone: Boolean,
    val total: Int,
    val shields: Int = 0,
    val shieldActive: Boolean = false,
    val last14Days: List<ContributionDay> = emptyList(),
    val localUtcResetTime: String = ""
)

enum class FlameState { LIT, FADING, BROKEN }

fun flameState(current: Int, todayDone: Boolean): FlameState = when {
    todayDone && current > 0 -> FlameState.LIT
    !todayDone && current > 0 -> FlameState.FADING
    else -> FlameState.BROKEN
}

fun flameState(s: StreakInfo): FlameState = flameState(s.current, s.todayDone || s.shieldActive)


data class Reward(
    val day: Int,
    val emoji: String,
    val label: String,
    val grantShield: Boolean = false
)

val REWARDS = listOf(
    Reward(7, "🥤", "A cold drink can"),
    Reward(14, "🛡️", "Streak Shield + Movie break", grantShield = true),
    Reward(21, "🍕", "Your favourite meal"),
    Reward(30, "🎧", "A new playlist or an hour of gaming"),
    Reward(40, "🛡️", "Streak Shield + Extra snack treat", grantShield = true),
    Reward(45, "🛍️", "Something small you have been wanting"),
    Reward(60, "🏞️", "A day trip with friends"),
    Reward(75, "🛡️", "Streak Shield + Special lunch", grantShield = true),
    Reward(90, "🎮", "A big treat: game, gadget or outing"),
    Reward(120, "🛡️", "Streak Shield + Proper celebration", grantShield = true),
    Reward(150, "🍔", "Full Gourmet Feast"),
    Reward(180, "👑", "Streak Shield + DSA Phase 1 Done! Choice reward", grantShield = true),
    Reward(210, "🍿", "Weekend Binge Pass"),
    Reward(240, "🛡️", "Streak Shield + Tech Accessory", grantShield = true),
    Reward(270, "🧘", "Wellness & Relax Day"),
    Reward(300, "🎧", "Premium Audio Upgrade"),
    Reward(330, "🛡️", "Streak Shield + Milestone Treat", grantShield = true),
    Reward(360, "🌟", "1 YEAR LEGEND! Major Life Reward"),
    Reward(450, "🛡️", "Streak Shield + Custom Dream Reward", grantShield = true),
    Reward(500, "🚀", "500-Day Half-Kilo Legend Status"),
    Reward(600, "💎", "Diamond Streak Club Treat"),
    Reward(730, "🏰", "2 YEARS UNSTOPPABLE LEGACY!")
)

const val DSA_TARGET = 360 // 2 problems a day for roughly 6 months
const val DAILY_GOAL = 2

val DSA_WEEKS = listOf(
    "Complexity and math basics", "Arrays", "Prefix sums and two pointers", "Sliding window",
    "Strings", "Hashing and maps", "Sorting and binary search", "Binary search on answer",
    "Recursion", "Backtracking", "Linked lists", "Stacks", "Queues and monotonic stack",
    "Heaps and priority queues", "Tree traversals", "Binary search trees", "Tree problems with DFS and BFS",
    "Tries", "Graphs: BFS and DFS", "Graphs: shortest paths", "Union-find and MST", "Topological sort",
    "DP basics", "DP on grids and subsequences", "Knapsack and string DP", "Revision and mock contests"
)

val QUOTES = listOf(
    "One commit a day beats ten commits once a month.",
    "You do not need motivation. You need the next small push.",
    "Every green square is a vote for the engineer you are becoming.",
    "Hard problems get easier the day you stop avoiding them.",
    "Consistency is boring. That is exactly why it works.",
    "Six months from now, you will wish you had started today.",
    "Do not break the chain. Break the problem instead.",
    "Slow progress is still progress. Open the editor.",
    "Your future interviewer is watching your streak right now.",
    "Two problems today. That is the whole job.",
    "Discipline is choosing what you want most over what you want now.",
    "A bug you fixed today is a pattern you will never forget.",
    "Small daily wins compound into a career."
)

fun computeStreak(
    days: List<ContributionDay>,
    totalContributions: Int = 0,
    shields: Int = 0,
    isShieldActive: Boolean = false,
    todayUtc: LocalDate = LocalDate.now(ZoneOffset.UTC)
): StreakInfo {
    val validDays = days.filter {
        try {
            LocalDate.parse(it.date) <= todayUtc
        } catch (e: Exception) {
            false
        }
    }.sortedBy { it.date }

    if (validDays.isEmpty()) {
        return StreakInfo(
            current = 0,
            longest = 0,
            todayDone = false,
            total = totalContributions,
            shields = shields,
            shieldActive = false,
            last14Days = emptyList(),
            localUtcResetTime = getLocalUtcResetTime()
        )
    }

    val dayMap = validDays.associate { it.date to it.count }
    val todayStr = todayUtc.toString()
    val todayDone = (dayMap[todayStr] ?: 0) > 0

    var checkDate = if (todayDone) todayUtc else todayUtc.minusDays(1)
    var current = 0
    var activeShieldUsed = false
    var remainingShields = shields

    // Only apply shield protection if explicitly activated by user!
    var shieldAvailableToUse = if (isShieldActive && shields > 0) 1 else 0

    while (true) {
        val dateStr = checkDate.toString()
        val count = dayMap[dateStr] ?: 0
        if (count > 0) {
            current++
            checkDate = checkDate.minusDays(1)
        } else if (shieldAvailableToUse > 0) {
            current++
            shieldAvailableToUse--
            activeShieldUsed = true
            remainingShields = (remainingShields - 1).coerceAtLeast(0)
            checkDate = checkDate.minusDays(1)
        } else {
            break
        }
    }

    var longest = 0
    var run = 0
    for (d in validDays) {
        if (d.count > 0) {
            run++
            if (run > longest) longest = run
        } else {
            run = 0
        }
    }
    if (current > longest) longest = current

    val last14 = (0..13).map { offset ->
        val d = todayUtc.minusDays((13 - offset).toLong()).toString()
        ContributionDay(d, dayMap[d] ?: 0)
    }

    return StreakInfo(
        current = current,
        longest = longest,
        todayDone = todayDone,
        total = if (totalContributions > 0) totalContributions else validDays.sumOf { it.count },
        shields = remainingShields,
        shieldActive = activeShieldUsed,
        last14Days = last14,
        localUtcResetTime = getLocalUtcResetTime()
    )
}

fun getLocalUtcResetTime(): String {
    return try {
        val utcZero = java.time.LocalTime.MIDNIGHT.atDate(LocalDate.now()).atZone(ZoneOffset.UTC)
        val localZone = ZoneId.systemDefault()
        val localTime = utcZero.withZoneSameInstant(localZone)
        val formatter = DateTimeFormatter.ofPattern("hh:mm a z")
        localTime.format(formatter)
    } catch (e: Exception) {
        "00:00 UTC"
    }
}

object LegacyMigration {
    fun wipeLegacyStore(context: Context) {
        val prefs = context.getSharedPreferences("streak", Context.MODE_PRIVATE)
        if (prefs.getBoolean("legacy_wiped_v2", false)) return
        prefs.edit().clear().putBoolean("legacy_wiped_v2", true).apply()
    }
}
