package com.ayush.streakforge

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

class StreakWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = Store.streak(context)
        provideContent { WidgetContent(s) }
    }
}

@Composable
private fun WidgetContent(s: StreakInfo) {
    val state = flameState(s)

    val bgColor = when (state) {
        FlameState.LIT -> Color(0xFF2A1D45)
        FlameState.FADING -> Color(0xFF221E2E)
        FlameState.BROKEN -> Color(0xFF13111A)
    }

    val imageRes = when (state) {
        FlameState.LIT -> R.drawable.flame_lit
        FlameState.FADING -> R.drawable.flame_fading
        FlameState.BROKEN -> R.drawable.wood_burnt
    }

    val numberColor = when (state) {
        FlameState.LIT -> Color(0xFFFFC24B) // Gold
        FlameState.FADING -> Color(0xFFA99BC4) // Muted grey
        FlameState.BROKEN -> Color(0xFFEDE6F5) // Ash
    }

    val labelColor = when {
        s.shieldActive -> Color(0xFFFFC24B) // Gold for active shield
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

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(bgColor))
            .cornerRadius(24.dp)
            .padding(10.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            provider = ImageProvider(imageRes),
            contentDescription = "Streak Flame",
            modifier = GlanceModifier.size(44.dp)
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            "Day ${s.current}",
            style = TextStyle(
                color = ColorProvider(numberColor),
                fontSize = 24.sp,
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

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}
