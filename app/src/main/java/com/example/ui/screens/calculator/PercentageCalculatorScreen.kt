package com.example.ui.screens.calculator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppBackButton
import com.example.ui.components.AppTopBar
import java.text.DecimalFormat

@Composable
fun PercentageCalculatorScreen(
    onBack: () -> Unit
) {
    var modeIndex by remember { mutableStateOf(0) }
    val modes = listOf("Percent of", "% What is X of Y", "% Change", "Tip & Split")

    // Mode 1: What is X% of Y?
    var p1Percent by remember { mutableStateOf("15") }
    var p1Total by remember { mutableStateOf("200") }

    // Mode 2: X is what % of Y?
    var p2Part by remember { mutableStateOf("25") }
    var p2Total by remember { mutableStateOf("100") }

    // Mode 3: % Increase/Decrease from X to Y
    var p3From by remember { mutableStateOf("50") }
    var p3To by remember { mutableStateOf("75") }

    // Mode 4: Tip Calculator
    var tipBill by remember { mutableStateOf("120") }
    var tipPercent by remember { mutableStateOf(18f) }
    var splitPeople by remember { mutableStateOf(2f) }

    val df = remember { DecimalFormat("#,##0.##") }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Percentage Calculator",
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
            ScrollableTabRow(
                selectedTabIndex = modeIndex,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth().testTag("percentage_tabs")
            ) {
                modes.forEachIndexed { idx, title ->
                    Tab(
                        selected = modeIndex == idx,
                        onClick = { modeIndex = idx },
                        text = { Text(title, fontWeight = if (modeIndex == idx) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (modeIndex) {
                    0 -> { // What is X% of Y?
                        item {
                            val p = p1Percent.toDoubleOrNull() ?: 0.0
                            val t = p1Total.toDoubleOrNull() ?: 0.0
                            val res = (p / 100.0) * t

                            CalculationCard(
                                title = "What is $p% of $t?",
                                resultText = df.format(res)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = p1Percent,
                                        onValueChange = { p1Percent = it },
                                        label = { Text("Percentage (%)") },
                                        modifier = Modifier.weight(1f).testTag("pct_p1_percent")
                                    )
                                    OutlinedTextField(
                                        value = p1Total,
                                        onValueChange = { p1Total = it },
                                        label = { Text("Total Value") },
                                        modifier = Modifier.weight(1f).testTag("pct_p1_total")
                                    )
                                }
                            }
                        }
                    }

                    1 -> { // X is what % of Y?
                        item {
                            val part = p2Part.toDoubleOrNull() ?: 0.0
                            val total = p2Total.toDoubleOrNull() ?: 1.0
                            val res = if (total != 0.0) (part / total) * 100.0 else 0.0

                            CalculationCard(
                                title = "$part is what % of $total?",
                                resultText = "${df.format(res)}%"
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = p2Part,
                                        onValueChange = { p2Part = it },
                                        label = { Text("Part (X)") },
                                        modifier = Modifier.weight(1f).testTag("pct_p2_part")
                                    )
                                    OutlinedTextField(
                                        value = p2Total,
                                        onValueChange = { p2Total = it },
                                        label = { Text("Whole (Y)") },
                                        modifier = Modifier.weight(1f).testTag("pct_p2_total")
                                    )
                                }
                            }
                        }
                    }

                    2 -> { // % Change
                        item {
                            val from = p3From.toDoubleOrNull() ?: 0.0
                            val to = p3To.toDoubleOrNull() ?: 0.0
                            val diff = to - from
                            val changePct = if (from != 0.0) (diff / from) * 100.0 else 0.0

                            CalculationCard(
                                title = "Change from $from to $to",
                                resultText = (if (changePct > 0) "+ " else "") + "${df.format(changePct)}%"
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = p3From,
                                        onValueChange = { p3From = it },
                                        label = { Text("Initial Value") },
                                        modifier = Modifier.weight(1f).testTag("pct_p3_from")
                                    )
                                    OutlinedTextField(
                                        value = p3To,
                                        onValueChange = { p3To = it },
                                        label = { Text("Final Value") },
                                        modifier = Modifier.weight(1f).testTag("pct_p3_to")
                                    )
                                }
                            }
                        }
                    }

                    3 -> { // Tip & Split
                        item {
                            val bill = tipBill.toDoubleOrNull() ?: 0.0
                            val tipAmount = bill * (tipPercent / 100.0)
                            val totalAmount = bill + tipAmount
                            val perPerson = if (splitPeople > 0) totalAmount / splitPeople else totalAmount

                            Card(
                                modifier = Modifier.fillMaxWidth().testTag("pct_tip_card"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = tipBill,
                                        onValueChange = { tipBill = it },
                                        label = { Text("Bill Amount ($)") },
                                        modifier = Modifier.fillMaxWidth().testTag("pct_tip_bill")
                                    )

                                    Text("Tip: ${tipPercent.toInt()}% ($${df.format(tipAmount)})", style = MaterialTheme.typography.bodyMedium)
                                    Slider(
                                        value = tipPercent,
                                        onValueChange = { tipPercent = it },
                                        valueRange = 0f..40f,
                                        steps = 7,
                                        modifier = Modifier.testTag("pct_tip_slider")
                                    )

                                    Text("Split between: ${splitPeople.toInt()} people", style = MaterialTheme.typography.bodyMedium)
                                    Slider(
                                        value = splitPeople,
                                        onValueChange = { splitPeople = it },
                                        valueRange = 1f..10f,
                                        steps = 8,
                                        modifier = Modifier.testTag("pct_split_slider")
                                    )

                                    HorizontalDivider()

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Total with Tip:", style = MaterialTheme.typography.titleMedium)
                                        Text("$${df.format(totalAmount)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Each Person Pays:", style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.primary))
                                        Text(
                                            "$${df.format(perPerson)}",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        )
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

@Composable
private fun CalculationCard(
    title: String,
    resultText: String,
    inputs: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("pct_calc_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            inputs()
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Result",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = resultText,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
