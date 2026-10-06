package com.ayush.streakforge

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val Night = Color(0xFF1B1230)
private val Plum = Color(0xFF2A1D45)
private val Ember = Color(0xFFFF6B2C)
private val Gold = Color(0xFFFFC24B)
private val Ash = Color(0xFFEDE6F5)
private val Muted = Color(0xFFA99BC4)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleRefresh(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Ember, background = Night, surface = Plum,
                    onSurface = Ash, onBackground = Ash, onPrimary = Color.White
                )
            ) { App() }
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var streak by remember { mutableStateOf(Store.streak(ctx)) }
    var user by remember { mutableStateOf(Store.username(ctx)) }
    var token by remember { mutableStateOf(Store.token(ctx)) }
    var claimed by remember { mutableStateOf(Store.claimed(ctx)) }
    var solved by remember { mutableStateOf(Store.solved(ctx)) }
    var status by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val planStart = remember { Store.planStart(ctx) }

    fun refresh() {
        if (user.isBlank() || token.isBlank()) {
            status = "Add your GitHub username and token at the bottom to start tracking."
            return
        }
        scope.launch {
            loading = true
            status = ""
            try {
                val availableShields = Store.shields(ctx)
                val s = withContext(Dispatchers.IO) { GitHub.fetch(user, token, availableShields) }
                Store.saveStreak(ctx, s)
                streak = s
                StreakWidget().updateAll(ctx)
            } catch (e: Exception) {
                status = "Could not refresh: ${e.message}"
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { refresh() }

    val quote = QUOTES[LocalDate.now().dayOfYear % QUOTES.size]
    val nextReward = REWARDS.firstOrNull { it.day > streak.current }
    val daysIn = ChronoUnit.DAYS.between(planStart, LocalDate.now()).toInt().coerceAtLeast(0)
    val week = (daysIn / 7 + 1).coerceAtMost(DSA_WEEKS.size)
    val expected = ((daysIn + 1) * DAILY_GOAL).coerceAtMost(DSA_TARGET)

    val stState = flameState(streak)
    val animScale = try {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } catch (e: Exception) {
        1f
    }
    val isAnimationEnabled = animScale > 0f

    val flameScale = if (stState == FlameState.LIT && isAnimationEnabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "flameFlicker")
        val s by infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "flameScale"
        )
        s
    } else {
        1f
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Night).statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val flameRes = when (stState) {
                FlameState.LIT -> R.drawable.flame_lit
                FlameState.FADING -> R.drawable.flame_fading
                FlameState.BROKEN -> R.drawable.wood_burnt
            }
            val numberColor = when (stState) {
                FlameState.LIT -> Gold
                FlameState.FADING -> Muted
                FlameState.BROKEN -> Muted
            }
            val statusMsg = when {
                streak.shieldActive -> "🛡️ Shield Active! Protected 1 missed day."
                stState == FlameState.LIT -> "Today is in the bank. Nice work."
                stState == FlameState.FADING -> "Push today to keep it alive"
                else -> "Streak broke. Start again today."
            }
            val statusColor = when (stState) {
                FlameState.LIT -> Gold
                FlameState.FADING -> Ember
                FlameState.BROKEN -> Ember
            }

            val isDebug = (ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

            Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(flameRes),
                    contentDescription = "Streak Flame Visual",
                    modifier = Modifier
                        .size(96.dp)
                        .scale(flameScale)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${streak.current}",
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Black,
                    color = numberColor,
                    modifier = if (isDebug) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    val next = when (stState) {
                                        FlameState.LIT -> StreakInfo(current = streak.current.coerceAtLeast(1), longest = streak.longest, todayDone = false, total = streak.total, shields = streak.shields, shieldActive = false)
                                        FlameState.FADING -> StreakInfo(current = 0, longest = streak.longest, todayDone = false, total = streak.total, shields = streak.shields, shieldActive = false)
                                        FlameState.BROKEN -> StreakInfo(current = 7, longest = streak.longest.coerceAtLeast(7), todayDone = true, total = streak.total + 1, shields = streak.shields, shieldActive = false)
                                    }
                                    Store.saveStreak(ctx, next)
                                    streak = next
                                    scope.launch { StreakWidget().updateAll(ctx) }
                                }
                            )
                        }
                    } else Modifier
                )
                Text("day streak", fontSize = 18.sp, color = Ash)
                Spacer(Modifier.height(8.dp))
                Text(
                    statusMsg,
                    color = statusColor,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🛡️ ${streak.shields} Shield${if (streak.shields != 1) "s" else ""} available", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(" • Best: ${streak.longest} days", color = Muted, fontSize = 13.sp)
                }
                if (status.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(status, color = Ember, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                TextButton(onClick = { refresh() }, enabled = !loading) {
                    Text(if (loading) "Refreshing..." else "Refresh", color = Gold)
                }
            }
        }

        // Restore Streak Option (Twice per month)
        item {
            val restoresUsed = Store.restoresThisMonth(ctx)
            val restoresLeft = (2 - restoresUsed).coerceAtLeast(0)
            Card(
                colors = CardDefaults.cardColors(containerColor = Plum),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("🛡️ Restore Streak", fontWeight = FontWeight.Bold, color = Ash, fontSize = 16.sp)
                        Text("$restoresLeft/2 left this month", color = Gold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "You can restore your streak up to 2 times every month if you accidentally missed a push.",
                        color = Muted, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (Store.canRestore(ctx)) {
                                val remaining = Store.useRestore(ctx)
                                val restoredCurrent = streak.longest.coerceAtLeast(1)
                                val restoredStreak = StreakInfo(
                                    current = restoredCurrent,
                                    longest = streak.longest.coerceAtLeast(restoredCurrent),
                                    todayDone = true,
                                    total = streak.total + 1,
                                    shields = streak.shields,
                                    shieldActive = false
                                )
                                Store.saveStreak(ctx, restoredStreak)
                                streak = restoredStreak
                                status = "Streak restored to $restoredCurrent days! You have $remaining restore(s) left this month."
                                scope.launch { StreakWidget().updateAll(ctx) }
                            } else {
                                status = "No restores remaining for this calendar month (2/2 used)."
                            }
                        },
                        enabled = Store.canRestore(ctx),
                        colors = ButtonDefaults.buttonColors(containerColor = Ember)
                    ) {
                        Text("Restore Streak ($restoresLeft available)")
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Plum), shape = RoundedCornerShape(20.dp)) {
                Text(
                    "“$quote”",
                    modifier = Modifier.padding(20.dp),
                    fontStyle = FontStyle.Italic, fontSize = 17.sp, color = Ash
                )
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Plum), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Next reward", fontWeight = FontWeight.Bold, color = Ash)
                    Spacer(Modifier.height(8.dp))
                    if (nextReward == null) {
                        Text("Every reward unlocked. You did it.", color = Gold)
                    } else {
                        Text(
                            "${nextReward.day - streak.current} days to go: ${nextReward.emoji} ${nextReward.label}",
                            color = Ash
                        )
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { (streak.current.toFloat() / nextReward.day).coerceAtMost(1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = Ember, trackColor = Night
                        )
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Plum), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Your rewards", fontWeight = FontWeight.Bold, color = Ash)
                        Text("${claimed.size}/${REWARDS.size} claimed", color = Muted, fontSize = 12.sp)
                    }
                    REWARDS.forEach { r ->
                        val unlocked = streak.longest >= r.day || streak.current >= r.day
                        val isClaimed = r.day in claimed
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(r.emoji, fontSize = 24.sp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(
                                    r.label,
                                    color = if (unlocked) Ash else Muted,
                                    fontWeight = if (r.grantShield) FontWeight.Bold else FontWeight.Normal
                                )
                                Text("Day ${r.day}${if (r.grantShield) " • +1 Shield 🛡️" else ""}", color = if (r.grantShield) Gold else Muted, fontSize = 12.sp)
                            }
                            when {
                                isClaimed -> Text("Claimed", color = Muted)
                                unlocked -> Button(
                                    onClick = {
                                        Store.claim(ctx, r.day)
                                        claimed = Store.claimed(ctx)
                                        val newStreak = Store.streak(ctx)
                                        streak = newStreak
                                        status = if (r.grantShield) "Reward claimed! +1 Streak Shield added to inventory 🛡️" else "Reward claimed!"
                                        scope.launch { StreakWidget().updateAll(ctx) }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Ember)
                                ) { Text("Claim") }
                                else -> Text("Locked", color = Muted)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Plum), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("DSA in 6 months", fontWeight = FontWeight.Bold, color = Ash)
                    Spacer(Modifier.height(8.dp))
                    Text("Week $week of ${DSA_WEEKS.size}: ${DSA_WEEKS[week - 1]}", color = Gold)
                    Text("Goal: $DAILY_GOAL problems a day", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (solved.toFloat() / DSA_TARGET).coerceAtMost(1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = Gold, trackColor = Night
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "$solved of $DSA_TARGET solved. By today you should be at $expected.",
                        color = if (solved >= expected) Gold else Ember, fontSize = 13.sp
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { Store.setSolved(ctx, solved - 1); solved = Store.solved(ctx) },
                            enabled = solved > 0
                        ) { Text("-1", color = Ash) }
                        Button(
                            onClick = { Store.setSolved(ctx, solved + 1); solved = Store.solved(ctx) },
                            colors = ButtonDefaults.buttonColors(containerColor = Ember)
                        ) { Text("Solved one") }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Plum), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("GitHub setup", fontWeight = FontWeight.Bold, color = Ash)
                    OutlinedTextField(
                        value = user, onValueChange = { user = it },
                        label = { Text("GitHub username") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = token, onValueChange = { token = it },
                        label = { Text("Personal access token") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Create a classic token at github.com/settings/tokens with only the read:user scope. It stays on this phone.",
                        color = Muted, fontSize = 12.sp
                    )
                    Button(
                        onClick = { Store.saveLogin(ctx, user, token); refresh() },
                        colors = ButtonDefaults.buttonColors(containerColor = Ember)
                    ) { Text("Save and refresh") }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
