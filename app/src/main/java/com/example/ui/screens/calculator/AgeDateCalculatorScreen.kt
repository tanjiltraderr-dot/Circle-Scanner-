package com.example.ui.screens.calculator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppBackButton
import com.example.ui.components.AppTopBar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun AgeDateCalculatorScreen(
    initialMode: String = "AGE",
    onBack: () -> Unit
) {
    var isAgeMode by remember { mutableStateOf(initialMode == "AGE") }

    // Age inputs
    var birthYear by remember { mutableStateOf("1998") }
    var birthMonth by remember { mutableStateOf("5") }
    var birthDay by remember { mutableStateOf("15") }

    // Date Diff inputs
    var dateDaysToAdd by remember { mutableStateOf("30") }

    val sdf = remember { SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (isAgeMode) "Age Calculator" else "Date Calculator",
                navigationIcon = { AppBackButton(onBack = onBack) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = if (isAgeMode) 0 else 1,
                modifier = Modifier.fillMaxWidth().testTag("age_date_tabs")
            ) {
                Tab(
                    selected = isAgeMode,
                    onClick = { isAgeMode = true },
                    text = { Text("Age Calculator", fontWeight = if (isAgeMode) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = !isAgeMode,
                    onClick = { isAgeMode = false },
                    text = { Text("Date Calculator", fontWeight = if (!isAgeMode) FontWeight.Bold else FontWeight.Normal) }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isAgeMode) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(text = "Enter Date of Birth", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = birthDay,
                                        onValueChange = { birthDay = it },
                                        label = { Text("Day") },
                                        modifier = Modifier.weight(1f).testTag("birth_day_input")
                                    )
                                    OutlinedTextField(
                                        value = birthMonth,
                                        onValueChange = { birthMonth = it },
                                        label = { Text("Month (1-12)") },
                                        modifier = Modifier.weight(1f).testTag("birth_month_input")
                                    )
                                    OutlinedTextField(
                                        value = birthYear,
                                        onValueChange = { birthYear = it },
                                        label = { Text("Year") },
                                        modifier = Modifier.weight(1.2f).testTag("birth_year_input")
                                    )
                                }
                            }
                        }
                    }

                    // Calculation Results
                    item {
                        val d = birthDay.toIntOrNull() ?: 1
                        val m = (birthMonth.toIntOrNull() ?: 1) - 1
                        val y = birthYear.toIntOrNull() ?: 2000

                        val birthCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, y)
                            set(Calendar.MONTH, m)
                            set(Calendar.DAY_OF_MONTH, d)
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }

                        val nowCal = Calendar.getInstance()
                        val diffMillis = nowCal.timeInMillis - birthCal.timeInMillis
                        val totalDays = TimeUnit.MILLISECONDS.toDays(diffMillis).coerceAtLeast(0)

                        var years = nowCal.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR)
                        var months = nowCal.get(Calendar.MONTH) - birthCal.get(Calendar.MONTH)
                        var days = nowCal.get(Calendar.DAY_OF_MONTH) - birthCal.get(Calendar.DAY_OF_MONTH)

                        if (days < 0) {
                            months--
                            val prevMonth = (nowCal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                            days += prevMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
                        }
                        if (months < 0) {
                            years--
                            months += 12
                        }

                        // Next Birthday Calculation
                        val nextBday = Calendar.getInstance().apply {
                            set(Calendar.MONTH, m)
                            set(Calendar.DAY_OF_MONTH, d)
                        }
                        if (nextBday.before(nowCal)) {
                            nextBday.add(Calendar.YEAR, 1)
                        }
                        val daysUntilBday = TimeUnit.MILLISECONDS.toDays(nextBday.timeInMillis - nowCal.timeInMillis)

                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("age_result_card"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "Current Age",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "$years Years, $months Months, $days Days",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Total Days Lived", style = MaterialTheme.typography.labelSmall)
                                        Text("$totalDays days", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    }
                                    Column {
                                        Text("Next Birthday in", style = MaterialTheme.typography.labelSmall)
                                        Text("$daysUntilBday days", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                val bornDayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(birthCal.time)
                                Text(
                                    text = "Born on a $bornDayName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                } else {
                    // Date Calculator: Add / Subtract Days
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "Add / Subtract Days From Today",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Today: ${sdf.format(Date())}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                OutlinedTextField(
                                    value = dateDaysToAdd,
                                    onValueChange = { dateDaysToAdd = it },
                                    label = { Text("Number of Days") },
                                    modifier = Modifier.fillMaxWidth().testTag("date_calc_days_input")
                                )

                                val daysVal = dateDaysToAdd.toIntOrNull() ?: 0

                                val futureCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, daysVal) }
                                val pastCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysVal) }

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text("After $daysVal days:", style = MaterialTheme.typography.labelMedium)
                                        Text(sdf.format(futureCal.time), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("$daysVal days ago was:", style = MaterialTheme.typography.labelMedium)
                                        Text(sdf.format(pastCal.time), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
