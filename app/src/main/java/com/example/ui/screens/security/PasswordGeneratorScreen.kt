package com.example.ui.screens.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppBackButton
import com.example.ui.components.AppTopBar
import com.example.ui.components.PrimaryButton
import java.security.SecureRandom

@Composable
fun PasswordGeneratorScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var length by remember { mutableStateOf(16f) }
    var includeUppercase by remember { mutableStateOf(true) }
    var includeLowercase by remember { mutableStateOf(true) }
    var includeNumbers by remember { mutableStateOf(true) }
    var includeSymbols by remember { mutableStateOf(true) }

    fun generatePassword(): String {
        val uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lowercase = "abcdefghijklmnopqrstuvwxyz"
        val numbers = "0123456789"
        val symbols = "!@#$%^&*()_+-=[]{}|;:,.<>?"

        val charPool = buildString {
            if (includeUppercase) append(uppercase)
            if (includeLowercase) append(lowercase)
            if (includeNumbers) append(numbers)
            if (includeSymbols) append(symbols)
        }.ifEmpty { lowercase }

        val random = SecureRandom()
        return (1..length.toInt())
            .map { charPool[random.nextInt(charPool.length)] }
            .joinToString("")
    }

    var currentPassword by remember { mutableStateOf(generatePassword()) }

    val strengthInfo = remember(currentPassword, includeUppercase, includeLowercase, includeNumbers, includeSymbols) {
        val len = currentPassword.length
        var score = 0
        if (len >= 8) score++
        if (len >= 12) score++
        if (len >= 16) score++
        if (includeUppercase && includeLowercase) score++
        if (includeNumbers) score++
        if (includeSymbols) score++

        when {
            score <= 2 -> "Weak" to Color(0xFFEF4444)
            score <= 4 -> "Moderate" to Color(0xFFF59E0B)
            score <= 5 -> "Strong" to Color(0xFF10B981)
            else -> "Very Strong" to Color(0xFF059669)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Password Generator",
                navigationIcon = { AppBackButton(onBack = onBack) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("password_gen_content"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Password Display Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("generated_password_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = strengthInfo.second,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Strength: ${strengthInfo.first}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = strengthInfo.second
                                )
                            }
                            IconButton(onClick = { currentPassword = generatePassword() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Regenerate",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = currentPassword,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        PrimaryButton(
                            text = "Copy Password",
                            icon = Icons.Default.ContentCopy,
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Password", currentPassword))
                                Toast.makeText(context, "Password copied securely!", Toast.LENGTH_SHORT).show()
                            },
                            testTag = "copy_password_btn"
                        )
                    }
                }
            }

            // Options Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Password Length: ${length.toInt()}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Slider(
                            value = length,
                            onValueChange = {
                                length = it
                                currentPassword = generatePassword()
                            },
                            valueRange = 6f..32f,
                            steps = 25,
                            modifier = Modifier.testTag("password_length_slider")
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        OptionToggle(
                            title = "Uppercase Letters (A-Z)",
                            checked = includeUppercase,
                            onCheckedChange = {
                                includeUppercase = it
                                currentPassword = generatePassword()
                            }
                        )
                        OptionToggle(
                            title = "Lowercase Letters (a-z)",
                            checked = includeLowercase,
                            onCheckedChange = {
                                includeLowercase = it
                                currentPassword = generatePassword()
                            }
                        )
                        OptionToggle(
                            title = "Numbers (0-9)",
                            checked = includeNumbers,
                            onCheckedChange = {
                                includeNumbers = it
                                currentPassword = generatePassword()
                            }
                        )
                        OptionToggle(
                            title = "Symbols (!@#\$%^&*)",
                            checked = includeSymbols,
                            onCheckedChange = {
                                includeSymbols = it
                                currentPassword = generatePassword()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
