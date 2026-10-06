package com.ayush.streakforge

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ayush.streakforge.data.StreakRepository
import com.ayush.streakforge.db.StreakDatabase

class StreakWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp), // 2x2
            DpSize(250.dp, 110.dp)  // 4x2
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = StreakRepository(context)
        val activeUser = repo.getActiveUser()
        val streakInfo = if (activeUser.isBlank()) {
            null
        } else {
            val db = StreakDatabase.getInstance(context)
            val user = db.userDao().getUser(activeUser)
            val contribs = db.contributionDao().getContributions(activeUser)
            val contribDays = contribs.map { ContributionDay(it.date, it.count) }
            computeStreak(contribDays, user?.totalContributions ?: 0, user?.shields ?: 0)
        }
        provideContent {
            val size = LocalSize.current
            WidgetContent(streakInfo, activeUser, size)
        }
    }
}

@Composable
private fun WidgetContent(s: StreakInfo?, activeUser: String, size: DpSize) {
    val isWide = size.width >= 200.dp
    val bgColor = if (s == null || activeUser.isBlank()) {
        Color(0xFF1B1230)
    } else {
        when (flameState(s)) {
            FlameState.LIT -> Color(0xFF2A1D45)
            FlameState.FADING -> Color(0xFF221E2E)
            FlameState.BROKEN -> Color(0xFF13111A)
        }
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(bgColor))
            .cornerRadius(24.dp)
            .padding(12.dp)
            .clickable(actionStartActivity(MainActivity::class.java)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (s == null || activeUser.isBlank()) {
            Image(
                provider = ImageProvider(R.drawable.wood_burnt),
                contentDescription = "StreakForge Widget: Not Connected",
                modifier = GlanceModifier.size(36.dp)
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                "Not Connected",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFFFC24B)),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                "Tap to sign in",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFA99BC4)),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            )
        } else {
            val state = flameState(s)
            val imageRes = when (state) {
                FlameState.LIT -> R.drawable.flame_lit
                FlameState.FADING -> R.drawable.flame_fading
                FlameState.BROKEN -> R.drawable.wood_burnt
            }

            val numberColor = when (state) {
                FlameState.LIT -> Color(0xFFFFC24B) // Gold
                FlameState.FADING -> Color(0xFFA99BC4) // Muted
                FlameState.BROKEN -> Color(0xFFEDE6F5) // Ash
            }

            val labelColor = when {
                s.shieldActive -> Color(0xFFFFC24B)
                state == FlameState.LIT -> Color(0xFFFFC24B)
                state == FlameState.FADING -> Color(0xFFFF6B2C)
                else -> Color(0xFFFF6B2C)
            }

            val labelText = when {
                s.shieldActive -> "🛡️ Shield Active"
                state == FlameState.LIT -> "Done today"
                state == FlameState.FADING -> "Push today to keep it alive"
                else -> "Streak broke. Start again today."
            }

            val flameContentDesc = when (state) {
                FlameState.LIT -> "Flame State: Lit. Current streak ${s.current} days."
                FlameState.FADING -> "Flame State: Fading. Current streak ${s.current} days. Push today to keep it alive."
                FlameState.BROKEN -> "Flame State: Broken. Streak broke. Start again today."
            }

            if (isWide) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        provider = ImageProvider(imageRes),
                        contentDescription = flameContentDesc,
                        modifier = GlanceModifier.size(44.dp)
                    )
                    Spacer(GlanceModifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            "Day ${s.current}",
                            style = TextStyle(
                                color = ColorProvider(numberColor),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            labelText,
                            style = TextStyle(
                                color = ColorProvider(labelColor),
                                fontSize = 11.sp
                            )
                        )
                    }
                }
                Spacer(GlanceModifier.height(8.dp))
                // 7-day contribution strip for 4x2 layout
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val last7 = s.last14Days.takeLast(7)
                    last7.forEach { day ->
                        val dayColor = if (day.count > 0) Color(0xFFFF6B2C) else Color(0xFF2A1D45)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = GlanceModifier.padding(horizontal = 2.dp)
                        ) {
                            Text(
                                if (day.date.length >= 5) day.date.substring(5) else day.date,
                                style = TextStyle(color = ColorProvider(Color(0xFFA99BC4)), fontSize = 8.sp)
                            )
                            Spacer(GlanceModifier.height(2.dp))
                            Column(
                                modifier = GlanceModifier
                                    .size(14.dp)
                                    .background(ColorProvider(dayColor))
                                    .cornerRadius(4.dp)
                            ) {}
                        }
                    }
                }
            } else {
                // 2x2 layout
                Image(
                    provider = ImageProvider(imageRes),
                    contentDescription = flameContentDesc,
                    modifier = GlanceModifier.size(40.dp)
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    "Day ${s.current}",
                    style = TextStyle(
                        color = ColorProvider(numberColor),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    labelText,
                    style = TextStyle(
                        color = ColorProvider(labelColor),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }
}

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}
