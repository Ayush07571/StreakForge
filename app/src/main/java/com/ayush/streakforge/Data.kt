package com.ayush.streakforge

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

data class StreakInfo(val current: Int, val longest: Int, val todayDone: Boolean, val total: Int)

data class Reward(val day: Int, val emoji: String, val label: String)

// Edit this list to change your rewards.
val REWARDS = listOf(
    Reward(7, "🥤", "A cold drink can"),
    Reward(14, "🎬", "A movie break"),
    Reward(21, "🍕", "Your favourite meal"),
    Reward(30, "🎧", "A new playlist or an hour of gaming"),
    Reward(45, "🛍️", "Something small you have been wanting"),
    Reward(60, "🏞️", "A day trip with friends"),
    Reward(90, "🎮", "A big treat: game, gadget or outing"),
    Reward(120, "🏆", "A proper celebration"),
    Reward(180, "👑", "DSA done. Reward of your choice")
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
        .putBoolean("done", s.todayDone).putInt("total", s.total).apply()

    fun streak(c: Context) = StreakInfo(
        p(c).getInt("cur", 0), p(c).getInt("long", 0),
        p(c).getBoolean("done", false), p(c).getInt("total", 0)
    )

    fun claimed(c: Context): Set<Int> =
        (p(c).getStringSet("claimed", emptySet()) ?: emptySet()).map { it.toInt() }.toSet()

    fun claim(c: Context, day: Int) {
        val set = claimed(c).map { it.toString() }.toMutableSet().apply { add(day.toString()) }
        p(c).edit().putStringSet("claimed", set).apply()
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

    fun fetch(user: String, token: String): StreakInfo {
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
        return computeStreak(counts, cal.getInt("totalContributions"))
    }

    // counts: oldest first, last entry is "today" on GitHub's calendar.
    fun computeStreak(counts: List<Int>, total: Int): StreakInfo {
        if (counts.isEmpty()) return StreakInfo(0, 0, false, total)
        val todayDone = counts.last() > 0
        var i = counts.lastIndex
        if (!todayDone) i-- // today is still open, so yesterday's run stays alive
        var current = 0
        while (i >= 0 && counts[i] > 0) { current++; i-- }
        var longest = 0
        var run = 0
        for (n in counts) { if (n > 0) { run++; if (run > longest) longest = run } else run = 0 }
        return StreakInfo(current, longest, todayDone, total)
    }
}
