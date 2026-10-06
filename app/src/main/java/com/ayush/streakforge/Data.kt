package com.ayush.streakforge

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

data class StreakInfo(
    val current: Int,
    val longest: Int,
    val todayDone: Boolean,
    val total: Int,
    val shields: Int = 0,
    val shieldActive: Boolean = false
)

enum class FlameState { LIT, FADING, BROKEN }

fun flameState(s: StreakInfo): FlameState = when {
    (s.todayDone || s.shieldActive) && s.current > 0 -> FlameState.LIT
    !s.todayDone && s.current > 0 -> FlameState.FADING
    else -> FlameState.BROKEN
}

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

object Store {
    private fun p(c: Context) = c.getSharedPreferences("streak", Context.MODE_PRIVATE)

    fun username(c: Context) = p(c).getString("user", "") ?: ""
    fun token(c: Context) = p(c).getString("token", "") ?: ""
    fun saveLogin(c: Context, u: String, t: String) =
        p(c).edit().putString("user", u.trim()).putString("token", t.trim()).apply()

    fun saveStreak(c: Context, s: StreakInfo) = p(c).edit()
        .putInt("cur", s.current).putInt("long", s.longest)
        .putBoolean("done", s.todayDone).putInt("total", s.total)
        .putInt("shields", s.shields)
        .putBoolean("shieldActive", s.shieldActive)
        .apply()

    fun streak(c: Context) = StreakInfo(
        p(c).getInt("cur", 0), p(c).getInt("long", 0),
        p(c).getBoolean("done", false), p(c).getInt("total", 0),
        p(c).getInt("shields", 0), p(c).getBoolean("shieldActive", false)
    )

    fun shields(c: Context): Int = p(c).getInt("shields", 0)
    fun addShield(c: Context, amount: Int = 1) {
        val cur = shields(c)
        p(c).edit().putInt("shields", cur + amount).apply()
    }

    fun restoresThisMonth(c: Context): Int {
        val currentMonthKey = LocalDate.now().toString().substring(0, 7) // "YYYY-MM"
        val savedMonth = p(c).getString("restoreMonth", "") ?: ""
        if (savedMonth != currentMonthKey) {
            p(c).edit().putString("restoreMonth", currentMonthKey).putInt("restoresCount", 0).apply()
            return 0
        }
        return p(c).getInt("restoresCount", 0)
    }

    fun canRestore(c: Context): Boolean = restoresThisMonth(c) < 2

    fun useRestore(c: Context): Int {
        val currentCount = restoresThisMonth(c)
        if (currentCount >= 2) return 0
        val newCount = currentCount + 1
        val currentMonthKey = LocalDate.now().toString().substring(0, 7)
        p(c).edit().putString("restoreMonth", currentMonthKey).putInt("restoresCount", newCount).apply()
        return 2 - newCount
    }

    fun claimed(c: Context): Set<Int> =
        (p(c).getStringSet("claimed", emptySet()) ?: emptySet()).map { it.toInt() }.toSet()

    fun claim(c: Context, day: Int) {
        val set = claimed(c).map { it.toString() }.toMutableSet().apply { add(day.toString()) }
        p(c).edit().putStringSet("claimed", set).apply()
        val reward = REWARDS.firstOrNull { it.day == day }
        if (reward?.grantShield == true) {
            addShield(c, 1)
        }
    }

    fun planStart(c: Context): LocalDate {
        val saved = p(c).getString("planStart", null)
        if (saved != null) return LocalDate.parse(saved)
        val today = LocalDate.now()
        p(c).edit().putString("planStart", today.toString()).apply()
        return today
    }

    fun solved(c: Context) = p(c).getInt("solved", 0)
    fun setSolved(c: Context, n: Int) = p(c).edit().putInt("solved", n.coerceAtLeast(0)).apply()
}

object GitHub {
    private const val QUERY =
        "query(\$login:String!,\$from:DateTime!){user(login:\$login){contributionsCollection(from:\$from)" +
        "{contributionCalendar{totalContributions weeks{contributionDays{date contributionCount}}}}}}"

    fun fetch(user: String, token: String, shieldsAvailable: Int = 0): StreakInfo {
        val from = LocalDate.now().minusDays(364).toString() + "T00:00:00Z"
        val body = JSONObject().put("query", QUERY)
            .put("variables", JSONObject().put("login", user).put("from", from)).toString()

        val c = URL("https://api.github.com/graphql").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000
        c.readTimeout = 15000
        c.doOutput = true
        c.setRequestProperty("Authorization", "bearer $token")
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("User-Agent", "StreakForge")
        c.outputStream.use { it.write(body.toByteArray()) }

        val code = c.responseCode
        if (code == 401) throw Exception("Token rejected. Check it and try again.")
        val text = (if (code in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
        if (code !in 200..299) throw Exception("GitHub returned $code")

        val json = JSONObject(text)
        if (json.has("errors")) throw Exception(json.getJSONArray("errors").getJSONObject(0).getString("message"))
        val cal = json.getJSONObject("data").getJSONObject("user")
            .getJSONObject("contributionsCollection").getJSONObject("contributionCalendar")

        val counts = mutableListOf<Int>()
        val weeks = cal.getJSONArray("weeks")
        for (w in 0 until weeks.length()) {
            val days = weeks.getJSONObject(w).getJSONArray("contributionDays")
            for (d in 0 until days.length()) counts.add(days.getJSONObject(d).getInt("contributionCount"))
        }
        return computeStreak(counts, cal.getInt("totalContributions"), shieldsAvailable)
    }

    // counts: oldest first, last entry is "today" on GitHub's calendar.
    fun computeStreak(counts: List<Int>, total: Int, shieldsAvailable: Int = 0): StreakInfo {
        if (counts.isEmpty()) return StreakInfo(0, 0, false, total, shieldsAvailable)
        val todayDone = counts.last() > 0
        var i = counts.lastIndex
        if (!todayDone) i-- // yesterday
        
        var current = 0
        var available = shieldsAvailable
        var shieldActive = false
        
        while (i >= 0) {
            if (counts[i] > 0) {
                current++
                i--
            } else if (available > 0 && current > 0) {
                // Shield protects 1 missed day!
                current++
                available--
                shieldActive = true
                i--
            } else {
                break
            }
        }

        var longest = 0
        var run = 0
        for (n in counts) {
            if (n > 0) {
                run++
                if (run > longest) longest = run
            } else run = 0
        }
        if (current > longest) longest = current

        return StreakInfo(current, longest, todayDone, total, available, shieldActive)
    }
}
