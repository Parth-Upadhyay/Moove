package com.example.kinetiq.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GridCalendar(
    selectedDate: Date,
    onDateSelected: (Date) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentMonth by remember { mutableStateOf(Calendar.getInstance().apply { time = selectedDate }) }
    
    val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(currentMonth.time)
    val daysInMonth = getDaysInMonth(currentMonth)
    val firstDayOfWeek = getFirstDayOfMonth(currentMonth) // 1 = Sunday, 2 = Monday...

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val newMonth = (currentMonth.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                    currentMonth = newMonth
                }) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous Month")
                }
                
                Text(
                    text = monthName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = {
                    val newMonth = (currentMonth.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                    currentMonth = newMonth
                }) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next Month")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Day Labels
            val daysOfWeek = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            Row(modifier = Modifier.fillMaxWidth()) {
                daysOfWeek.forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Calendar Grid
            val totalCells = 42 // 6 weeks * 7 days
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.height(240.dp),
                userScrollEnabled = false
            ) {
                // Empty cells before the first day
                items((1 until firstDayOfWeek).toList()) {
                    Box(modifier = Modifier.aspectRatio(1f))
                }

                // Days of the month
                items(daysInMonth) { date ->
                    val isSelected = isSameDay(date, selectedDate)
                    val isToday = isSameDay(date, Date())
                    
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    isToday -> MaterialTheme.colorScheme.primaryContainer
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
                                isSelected -> Color.White
                                isToday -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
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
