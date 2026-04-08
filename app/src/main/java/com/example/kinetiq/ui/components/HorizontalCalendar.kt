package com.example.kinetiq.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kinetiq.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HorizontalCalendar(
    selectedDate: Date,
    onDateSelected: (Date) -> Unit,
    modifier: Modifier = Modifier
) {
    // Generate dates for the current week or a range
    val dates = remember {
        val list = mutableListOf<Date>()
        val tempCal = Calendar.getInstance()
        tempCal.add(Calendar.DAY_OF_YEAR, -14) // Start 2 weeks ago
        repeat(30) {
            list.add(tempCal.time)
            tempCal.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }

    val listState = rememberLazyListState()
    
    // Initial scroll to today or selected date
    LaunchedEffect(Unit) {
        val todayIndex = dates.indexOfFirst { 
            isSameDay(it, selectedDate)
        }
        if (todayIndex != -1) {
            listState.scrollToItem(maxOf(0, todayIndex - 3))
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(selectedDate),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
        
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(dates) { date ->
                val isSelected = isSameDay(date, selectedDate)
                DateItem(
                    date = date,
                    isSelected = isSelected,
                    onClick = { onDateSelected(date) }
                )
            }
        }
    }
}

@Composable
private fun DateItem(
    date: Date,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dayOfWeek = SimpleDateFormat("EEE", Locale.getDefault()).format(date)
    val dayOfMonth = SimpleDateFormat("d", Locale.getDefault()).format(date)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) TealPrimary 
                else SectionBackground
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = dayOfWeek,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) SurfaceWhite else TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = dayOfMonth,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) SurfaceWhite else TextPrimary
        )
    }
}

private fun isSameDay(date1: Date, date2: Date): Boolean {
    val cal1 = Calendar.getInstance().apply { time = date1 }
    val cal2 = Calendar.getInstance().apply { time = date2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
