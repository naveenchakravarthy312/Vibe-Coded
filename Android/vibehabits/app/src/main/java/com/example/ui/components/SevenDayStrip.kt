package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DayStatus
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CanvasDark
import com.example.ui.theme.OnSuccessGreen
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun SevenDayStrip(
    days: List<DayStatus>,
    onToggleDay: (dateStr: String, currentStatus: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        days.forEach { day ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleDay(day.dateStr, day.isCompleted)
                    }
                    .testTag("day_strip_${day.dateStr}")
            ) {
                // Day letter header (e.g. M, T, W)
                Text(
                    text = day.dayOfWeekLetter,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day.isToday) AccentCyan else TextMuted,
                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 10.sp
                )

                // Completed day: filled pill in SuccessGreen
                // Uncompleted day: subtle hollow ring in BorderSubtle
                Box(
                    modifier = Modifier
                        .size(width = 28.dp, height = 28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (day.isCompleted) {
                                Modifier.background(SuccessGreen)
                            } else {
                                Modifier
                                    .background(CanvasDark)
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day.date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (day.isCompleted) OnSuccessGreen else TextMuted,
                        fontWeight = if (day.isCompleted) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 10.sp
                    )
                }

                // Small indicator dot if it's today
                if (day.isToday) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(AccentCyan)
                    )
                } else {
                    Box(modifier = Modifier.size(3.dp))
                }
            }
        }
    }
}
