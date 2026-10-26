package com.example.ui.screens.calculator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
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
import java.text.DecimalFormat

enum class UnitCategory(val title: String) {
    LENGTH("Length"),
    WEIGHT("Weight"),
    TEMPERATURE("Temp"),
    AREA("Area"),
    VOLUME("Volume"),
    SPEED("Speed"),
    TIME("Time")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitConverterScreen(
    onBack: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(UnitCategory.LENGTH) }
    var inputValueStr by remember { mutableStateOf("1") }

    val unitsForCategory = remember(selectedCategory) {
        when (selectedCategory) {
            UnitCategory.LENGTH -> listOf("Meters (m)", "Kilometers (km)", "Centimeters (cm)", "Millimeters (mm)", "Feet (ft)", "Inches (in)", "Miles (mi)", "Yards (yd)")
            UnitCategory.WEIGHT -> listOf("Kilograms (kg)", "Grams (g)", "Milligrams (mg)", "Pounds (lb)", "Ounces (oz)")
            UnitCategory.TEMPERATURE -> listOf("Celsius (°C)", "Fahrenheit (°F)", "Kelvin (K)")
            UnitCategory.AREA -> listOf("Square Meters (m²)", "Square Kilometers (km²)", "Square Feet (ft²)", "Acres", "Hectares")
            UnitCategory.VOLUME -> listOf("Liters (L)", "Milliliters (mL)", "Gallons (gal)", "Cups")
            UnitCategory.SPEED -> listOf("km/h", "mph", "m/s", "Knots")
            UnitCategory.TIME -> listOf("Seconds (s)", "Minutes (min)", "Hours (hr)", "Days")
        }
    }

    var fromUnit by remember(unitsForCategory) { mutableStateOf(unitsForCategory[0]) }
    var toUnit by remember(unitsForCategory) { mutableStateOf(unitsForCategory.getOrElse(1) { unitsForCategory[0] }) }

    var expandedFrom by remember { mutableStateOf(false) }
    var expandedTo by remember { mutableStateOf(false) }

    fun convert(value: Double, from: String, to: String, cat: UnitCategory): Double {
        if (from == to) return value
        return when (cat) {
            UnitCategory.LENGTH -> {
                val inMeters = when (from) {
                    "Kilometers (km)" -> value * 1000.0
                    "Centimeters (cm)" -> value / 100.0
                    "Millimeters (mm)" -> value / 1000.0
                    "Feet (ft)" -> value * 0.3048
                    "Inches (in)" -> value * 0.0254
                    "Miles (mi)" -> value * 1609.34
                    "Yards (yd)" -> value * 0.9144
                    else -> value
                }
                when (to) {
                    "Kilometers (km)" -> inMeters / 1000.0
                    "Centimeters (cm)" -> inMeters * 100.0
                    "Millimeters (mm)" -> inMeters * 1000.0
                    "Feet (ft)" -> inMeters / 0.3048
                    "Inches (in)" -> inMeters / 0.0254
                    "Miles (mi)" -> inMeters / 1609.34
                    "Yards (yd)" -> inMeters / 0.9144
                    else -> inMeters
                }
            }
            UnitCategory.WEIGHT -> {
                val inKg = when (from) {
                    "Grams (g)" -> value / 1000.0
                    "Milligrams (mg)" -> value / 1000000.0
                    "Pounds (lb)" -> value * 0.453592
                    "Ounces (oz)" -> value * 0.0283495
                    else -> value
                }
                when (to) {
                    "Grams (g)" -> inKg * 1000.0
                    "Milligrams (mg)" -> inKg * 1000000.0
                    "Pounds (lb)" -> inKg / 0.453592
                    "Ounces (oz)" -> inKg / 0.0283495
                    else -> inKg
                }
            }
            UnitCategory.TEMPERATURE -> {
                val inCelsius = when (from) {
                    "Fahrenheit (°F)" -> (value - 32.0) * 5.0 / 9.0
                    "Kelvin (K)" -> value - 273.15
                    else -> value
                }
                when (to) {
                    "Fahrenheit (°F)" -> (inCelsius * 9.0 / 5.0) + 32.0
                    "Kelvin (K)" -> inCelsius + 273.15
                    else -> inCelsius
                }
            }
            UnitCategory.AREA -> {
                val inSqM = when (from) {
                    "Square Kilometers (km²)" -> value * 1000000.0
                    "Square Feet (ft²)" -> value * 0.092903
                    "Acres" -> value * 4046.86
                    "Hectares" -> value * 10000.0
                    else -> value
                }
                when (to) {
                    "Square Kilometers (km²)" -> inSqM / 1000000.0
                    "Square Feet (ft²)" -> inSqM / 0.092903
                    "Acres" -> inSqM / 4046.86
                    "Hectares" -> inSqM / 10000.0
                    else -> inSqM
                }
            }
            UnitCategory.VOLUME -> {
                val inLiters = when (from) {
                    "Milliliters (mL)" -> value / 1000.0
                    "Gallons (gal)" -> value * 3.78541
                    "Cups" -> value * 0.236588
                    else -> value
                }
                when (to) {
                    "Milliliters (mL)" -> inLiters * 1000.0
                    "Gallons (gal)" -> inLiters / 3.78541
                    "Cups" -> inLiters / 0.236588
                    else -> inLiters
                }
            }
            UnitCategory.SPEED -> {
                val inKmh = when (from) {
                    "mph" -> value * 1.60934
                    "m/s" -> value * 3.6
                    "Knots" -> value * 1.852
                    else -> value
                }
                when (to) {
                    "mph" -> inKmh / 1.60934
                    "m/s" -> inKmh / 3.6
                    "Knots" -> inKmh / 1.852
                    else -> inKmh
                }
            }
            UnitCategory.TIME -> {
                val inSec = when (from) {
                    "Minutes (min)" -> value * 60.0
                    "Hours (hr)" -> value * 3600.0
                    "Days" -> value * 86400.0
                    else -> value
                }
                when (to) {
                    "Minutes (min)" -> inSec / 60.0
                    "Hours (hr)" -> inSec / 3600.0
                    "Days" -> inSec / 86400.0
                    else -> inSec
                }
            }
        }
    }

    val convertedValue = remember(inputValueStr, fromUnit, toUnit, selectedCategory) {
        val input = inputValueStr.toDoubleOrNull() ?: 0.0
        val res = convert(input, fromUnit, toUnit, selectedCategory)
        val df = DecimalFormat("#,##0.######")
        df.format(res)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Unit Converter",
                navigationIcon = { AppBackButton(onBack = onBack) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("unit_converter_content"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category Chips
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(UnitCategory.values()) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = cat
                                fromUnit = unitsForCategory[0]
                                toUnit = unitsForCategory.getOrElse(1) { unitsForCategory[0] }
                            },
                            label = { Text(cat.title) },
                            modifier = Modifier.testTag("unit_cat_${cat.name}")
                        )
                    }
                }
            }

            // Input Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = "From", style = MaterialTheme.typography.labelMedium)

                        OutlinedTextField(
                            value = inputValueStr,
                            onValueChange = { inputValueStr = it },
                            label = { Text("Enter Value") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("unit_input_value")
                        )

                        ExposedDropdownMenuBox(
                            expanded = expandedFrom,
                            onExpandedChange = { expandedFrom = !expandedFrom }
                        ) {
                            OutlinedTextField(
                                value = fromUnit,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFrom) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedFrom,
                                onDismissRequest = { expandedFrom = false }
                            ) {
                                unitsForCategory.forEach { unit ->
                                    DropdownMenuItem(
                                        text = { Text(unit) },
                                        onClick = {
                                            fromUnit = unit
                                            expandedFrom = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Swap Button
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = {
                            val temp = fromUnit
                            fromUnit = toUnit
                            toUnit = temp
                        },
                        modifier = Modifier.testTag("swap_units_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Swap Units",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Output Result Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("unit_result_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "To",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Text(
                            text = convertedValue,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        ExposedDropdownMenuBox(
                            expanded = expandedTo,
                            onExpandedChange = { expandedTo = !expandedTo }
                        ) {
                            OutlinedTextField(
                                value = toUnit,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTo) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = expandedTo,
                                onDismissRequest = { expandedTo = false }
                            ) {
                                unitsForCategory.forEach { unit ->
                                    DropdownMenuItem(
                                        text = { Text(unit) },
                                        onClick = {
                                            toUnit = unit
                                            expandedTo = false
                                        }
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
