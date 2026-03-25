package com.openclaw.agent.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openclaw.agent.ui.theme.Neutral500
import com.openclaw.agent.ui.theme.Neutral600
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Date separator line shown between message groups from different days.
 * Displays: "今天" / "昨天" / "3月22日" / "2025年12月1日"
 */
@Composable
fun DateDivider(
    date: LocalDate,
    modifier: Modifier = Modifier
) {
    val label = formatDateLabel(date)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Neutral600.copy(alpha = 0.5f)
        )
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Neutral500
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Neutral600.copy(alpha = 0.5f)
        )
    }
}

private fun formatDateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    val daysBetween = ChronoUnit.DAYS.between(date, today)

    return when {
        daysBetween == 0L -> "今天"
        daysBetween == 1L -> "昨天"
        daysBetween == 2L -> "前天"
        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("M月d日"))
        else -> date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
    }
}
