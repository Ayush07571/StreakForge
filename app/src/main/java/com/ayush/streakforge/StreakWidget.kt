package com.ayush.streakforge

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
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
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF2A1D45)))
            .cornerRadius(24.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🔥", style = TextStyle(fontSize = 30.sp))
        Text(
            "Day ${s.current}",
            style = TextStyle(
                color = ColorProvider(Color(0xFFFFC24B)),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            if (s.todayDone) "Done today" else "Commit today",
            style = TextStyle(
                color = ColorProvider(if (s.todayDone) Color(0xFFEDE6F5) else Color(0xFFFF6B2C)),
                fontSize = 12.sp
            )
        )
    }
}

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}
