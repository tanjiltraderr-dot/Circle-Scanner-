package com.example.ui.screens.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.UtilityHubApp
import com.example.ui.components.AppBackButton
import com.example.ui.components.AppTopBar
import kotlinx.coroutines.launch
import java.text.DecimalFormat

@Composable
fun CalculatorScreen(
    onBack: () -> Unit
) {
    val repository = remember { UtilityHubApp.instance.repository }
    val scope = rememberCoroutineScope()

    var expression by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("0") }
    val historyTape = remember { mutableStateListOf<String>() }
    var showHistoryDialog by remember { mutableStateOf(false) }

    fun evaluateExpression(expr: String): String {
        return try {
            val sanitized = expr.replace("×", "*").replace("÷", "/")
            val res = simpleEval(sanitized)
            val fmt = DecimalFormat("#,##0.######")
            fmt.format(res)
        } catch (e: Exception) {
            "Error"
        }
    }

    fun onButtonClick(label: String) {
        when (label) {
            "C" -> {
                expression = ""
                resultText = "0"
            }
            "DEL" -> {
                if (expression.isNotEmpty()) {
                    expression = expression.dropLast(1)
                    resultText = if (expression.isEmpty()) "0" else evaluateExpression(expression)
                }
            }
            "=" -> {
                if (expression.isNotEmpty()) {
                    val finalRes = evaluateExpression(expression)
                    if (finalRes != "Error") {
                        val record = "$expression = $finalRes"
                        historyTape.add(0, record)
                        scope.launch {
                            repository.addHistory("calc_basic", "Calculator Result", record)
                        }
                        expression = finalRes.replace(",", "")
                        resultText = finalRes
                    } else {
                        resultText = "Error"
                    }
                }
            }
            "+", "-", "×", "÷" -> {
                if (expression.isNotEmpty() && !expression.last().isOperator()) {
                    expression += label
                } else if (expression.isEmpty() && label == "-") {
                    expression += label
                }
            }
            "%" -> {
                if (expression.isNotEmpty() && expression.last().isDigit()) {
                    try {
                        val num = expression.toDoubleOrNull()
                        if (num != null) {
                            val pct = num / 100.0
                            expression = pct.toString()
                            resultText = pct.toString()
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
            else -> {
                expression += label
                val eval = evaluateExpression(expression)
                if (eval != "Error") {
                    resultText = eval
                }
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Calculator",
                navigationIcon = { AppBackButton(onBack = onBack) },
                actions = {
                    IconButton(onClick = { showHistoryDialog = true }) {
                        Icon(imageVector = Icons.Default.History, contentDescription = "History")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Display Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = expression.ifEmpty { "0" },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 32.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 2,
                    textAlign = TextAlign.End,
                    modifier = Modifier.testTag("calc_expression_display")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = resultText,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.testTag("calc_result_display")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Keypad Grid
            val buttons = listOf(
                listOf("C", "%", "DEL", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "-"),
                listOf("1", "2", "3", "+"),
                listOf("00", "0", ".", "=")
            )

            buttons.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { label ->
                        CalcButton(
                            label = label,
                            onClick = { onButtonClick(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("Calculation History") },
            text = {
                if (historyTape.isEmpty()) {
                    Text("No calculations yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(historyTape) { item ->
                            Text(
                                text = item,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun CalcButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOp = label in listOf("÷", "×", "-", "+", "=")
    val isPrimary = label == "="
    val isSpecial = label in listOf("C", "%", "DEL")

    val bg = when {
        isPrimary -> MaterialTheme.colorScheme.primary
        isOp -> MaterialTheme.colorScheme.primaryContainer
        isSpecial -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when {
        isPrimary -> MaterialTheme.colorScheme.onPrimary
        isOp -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable { onClick() }
            .testTag("calc_btn_$label"),
        contentAlignment = Alignment.Center
    ) {
        if (label == "DEL") {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Delete",
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = if (isOp || isPrimary) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 22.sp
                ),
                color = contentColor
            )
        }
    }
}

private fun Char.isOperator(): Boolean = this in "+-×÷/*"

private fun simpleEval(str: String): Double {
    return object : Any() {
        var pos = -1
        var ch = ' '

        fun nextChar() {
            ch = if (++pos < str.length) str[pos] else '\u0000'
        }

        fun eat(charToEat: Char): Boolean {
            while (ch == ' ') nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < str.length) throw RuntimeException("Unexpected: $ch")
            return x
        }

        fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+') -> x += parseTerm()
                    eat('-') -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*') -> x *= parseFactor()
                    eat('/') -> x /= parseFactor()
                    else -> return x
                }
            }
        }

        fun parseFactor(): Double {
            if (eat('+')) return parseFactor()
            if (eat('-')) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('(')) {
                x = parseExpression()
                eat(')')
            } else if ((ch in '0'..'9') || ch == '.') {
                while ((ch in '0'..'9') || ch == '.') nextChar()
                x = str.substring(startPos, pos).toDouble()
            } else {
                throw RuntimeException("Unexpected: $ch")
            }
            return x
        }
    }.parse()
}
