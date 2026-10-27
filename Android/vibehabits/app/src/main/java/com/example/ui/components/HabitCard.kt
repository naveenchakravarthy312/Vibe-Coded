package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Habit
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CanvasDark
import com.example.ui.theme.OnSuccessGreen
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HabitCard(
    habit: Habit,
    onToggleToday: () -> Unit,
    onToggleDay: (dateStr: String, currentStatus: Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val today = LocalDate.now()
    val isCompletedToday = habit.isCompletedToday(today)
    val streak = habit.currentStreak(today)
    val trailingDays = habit.trailing7Days(today)

    val checkBgColor by animateColorAsState(
        targetValue = if (isCompletedToday) SuccessGreen else CanvasDark,
        animationSpec = spring(),
        label = "checkBgColor"
    )

    val checkBorderColor by animateColorAsState(
        targetValue = if (isCompletedToday) SuccessGreen else BorderSubtle,
        animationSpec = spring(),
        label = "checkBorderColor"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariantDark)
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("habit_card_${habit.id}")
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Habit Title, Category Tag & Quick-Toggle Button for Today
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = habit.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // Category compact pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceDark)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = habit.category.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Created date and stats row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Created ${habit.createdAt}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )

                        // Streak counter in JetBrains Mono
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = "Streak",
                                tint = if (streak > 0) AccentCyan else TextMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "$streak ${if (streak == 1) "day" else "days"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (streak > 0) TextSecondary else TextMuted,
                                fontWeight = if (streak > 0) FontWeight.Medium else FontWeight.Normal
                            )
                        }

                        // All-time total completions
                        Text(
                            text = "•  ${habit.totalCompletions} total",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Quick-toggle checkbox/button for "Today" with immediate tactile feedback
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(checkBgColor)
                        .border(1.5.dp, checkBorderColor, RoundedCornerShape(10.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleToday()
                        }
                        .testTag("habit_toggle_today_${habit.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompletedToday) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completed Today",
                            tint = OnSuccessGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Text(
                            text = "TODAY",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Horizontal subtle divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderSubtle)
            )

            // 7-day trailing mini-calendar strip
            SevenDayStrip(
                days = trailingDays,
                onToggleDay = onToggleDay
            )
        }
    }
}
