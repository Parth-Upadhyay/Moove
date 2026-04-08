package com.example.kinetiq.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kinetiq.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GridCalendar(
    selectedDate: Date,
    onDateSelected: (Date) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentMonthCalendar by remember { mutableStateOf(Calendar.getInstance().apply { time = selectedDate }) }
    
    val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(currentMonthCalendar.time)
    val daysInMonth = getDaysInMonth(currentMonthCalendar)
    val firstDayOfWeek = getFirstDayOfMonth(currentMonthCalendar) // 1 = Sunday

    KinetiqCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val newMonth = (currentMonthCalendar.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                        currentMonthCalendar = newMonth
                    },
                    modifier = Modifier.background(SectionBackground, RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous", tint = TealPrimary)
                }
                
                Text(
                    text = monthName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(
                    onClick = {
                        val newMonth = (currentMonthCalendar.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                        currentMonthCalendar = newMonth
                    },
                    modifier = Modifier.background(SectionBackground, RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next", tint = TealPrimary)
                }
            }

            Spacer(Modifier.height(24.dp))

            // Day Labels
            val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")
            Row(modifier = Modifier.fillMaxWidth()) {
                daysOfWeek.forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextDisabled,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Calendar Grid
            // We use a Box with fixed height to contain the grid
            Box(modifier = Modifier.height(240.dp)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = false,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Empty cells before the first day
                    items(firstDayOfWeek - 1) {
                        Box(modifier = Modifier.aspectRatio(1f))
                    }

                    // Days of the month
                    items(daysInMonth) { date ->
                        val isSelected = isSameDay(date, selectedDate)
                        val isToday = isSameDay(date, Date())
                        
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> TealPrimary
                                        isToday -> TealPrimary.copy(alpha = 0.1f)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { onDateSelected(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = SimpleDateFormat("d", Locale.getDefault()).format(date),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isSelected -> SurfaceWhite
                                    isToday -> TealPrimary
                                    else -> TextPrimary
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getDaysInMonth(calendar: Calendar): List<Date> {
    val tempCal = calendar.clone() as Calendar
    tempCal.set(Calendar.DAY_OF_MONTH, 1)
    val maxDay = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val list = mutableListOf<Date>()
    for (i in 1..maxDay) {
        list.add(tempCal.time)
        tempCal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return list
}

private fun getFirstDayOfMonth(calendar: Calendar): Int {
    val tempCal = calendar.clone() as Calendar
    tempCal.set(Calendar.DAY_OF_MONTH, 1)
    return tempCal.get(Calendar.DAY_OF_WEEK)
}

private fun isSameDay(date1: Date, date2: Date): Boolean {
    val cal1 = Calendar.getInstance().apply { time = date1 }
    val cal2 = Calendar.getInstance().apply { time = date2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
