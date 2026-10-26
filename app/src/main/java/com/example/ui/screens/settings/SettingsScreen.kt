package com.example.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import com.example.UtilityHubApp
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.DarkBlue
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val repository = remember { UtilityHubApp.instance.repository }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val prefs = remember {
        context.getSharedPreferences("utility_hub_settings", Context.MODE_PRIVATE)
    }

    val currentThemeMode by repository.themeMode.collectAsState()
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (currentThemeMode.uppercase()) {
        "DARK" -> true
        "SYSTEM", "SYSTEM DEFAULT" -> isSystemDark
        else -> false
    }
    val isReading = currentThemeMode.uppercase() == "READING"

    // Persisted settings states
    val themeModePref = when (currentThemeMode.uppercase()) {
        "DARK" -> "Dark"
        "SYSTEM", "SYSTEM DEFAULT" -> "System Default"
        "READING" -> "Reading Mode"
        else -> "Light"
    }
    var readingModeEnabled by remember(currentThemeMode) {
        mutableStateOf(currentThemeMode.uppercase() == "READING")
    }
    var fontSizePref by remember {
        mutableStateOf(prefs.getString("font_size_choice", "Medium") ?: "Medium")
    }
    var languagePref by remember {
        mutableStateOf(prefs.getString("language_choice", "English") ?: "English")
    }
    var notificationsEnabled by remember {
        mutableStateOf(prefs.getBoolean("notifications_enabled", true))
    }
    var autoSaveEnabled by remember {
        mutableStateOf(prefs.getBoolean("auto_save_enabled", true))
    }
    var defaultScannerMode by remember {
        mutableStateOf(prefs.getString("default_scanner_mode", "QR & Barcode") ?: "QR & Barcode")
    }
    val docScanPrefs = remember {
        context.getSharedPreferences("doc_scan_settings_prefs", Context.MODE_PRIVATE)
    }
    var autoEdgeDetectionEnabled by remember {
        mutableStateOf(docScanPrefs.getBoolean("pref_auto_edge_detection", true))
    }

    // Dialog visibility states
    var showThemeDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showScannerModeDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    // Color palette based on theme mode
    val backgroundColor = when {
        isDark -> DarkBackground
        isReading -> Color(0xFFFDFBF7)
        else -> Color.White
    }
    val cardBackground = when {
        isDark -> DarkSurface
        isReading -> Color(0xFFFAF7F2)
        else -> Color.White
    }
    val primaryTextColor = when {
        isDark -> DarkTextPrimary
        isReading -> Color(0xFF23201D)
        else -> Color(0xFF0F172A)
    }
    val secondaryTextColor = when {
        isDark -> DarkTextSecondary
        isReading -> Color(0xFF757068)
        else -> Color(0xFF64748B)
    }
    val borderColor = when {
        isDark -> DarkBorder
        isReading -> Color(0xFFEAE5DC)
        else -> Color(0xFFE2E8F0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // 1. Header (Title and Subtitle matching reference)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)
        ) {
            Text(
                text = "Settings",
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Customize your app experience",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor
                )
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings_screen_content"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 2. Circle Scanner App Card (Profile/App Info Card)
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBackground,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            Toast.makeText(context, "Circle Scanner v1.0.0 — All tools ready", Toast.LENGTH_SHORT).show()
                        }
                        .testTag("settings_app_profile_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDark) DarkBlue else BrandBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Circle Scanner",
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryTextColor
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "v1.0.0",
                                style = TextStyle(
                                    fontSize = 12.5.sp,
                                    color = secondaryTextColor
                                )
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFF64748B) else Color(0xFFCBD5E1),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 3. Appearance Section
            item {
                SettingsSectionHeader(
                    title = "Appearance",
                    icon = Icons.Outlined.LightMode,
                    textColor = primaryTextColor
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBackground,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // Theme Mode
                        SettingsClickableRow(
                            iconBoxColor = Color(0xFFF3E8FF),
                            iconVector = Icons.Default.DarkMode,
                            iconTint = Color(0xFF9333EA),
                            title = "Theme Mode",
                            description = "Choose your preferred theme",
                            valueText = themeModePref,
                            onClick = { showThemeDialog = true },
                            primaryColor = primaryTextColor,
                            secondaryColor = secondaryTextColor,
                            isDark = isDark,
                            testTag = "settings_row_theme_mode"
                        )

                        SettingsDivider(borderColor)

                        // Reading Mode (Working Toggle)
                        SettingsToggleRow(
                            iconBoxColor = Color(0xFFE0F2FE),
                            iconVector = Icons.Default.MenuBook,
                            iconTint = Color(0xFF0284C7),
                            title = "Reading Mode",
                            description = "Reduce eye strain with softer colors",
                            isChecked = readingModeEnabled,
                            onCheckedChange = { checked ->
                                readingModeEnabled = checked
                                prefs.edit().putBoolean("reading_mode_enabled", checked).apply()
                                if (checked) {
                                    repository.setThemeMode("READING")
                                } else {
                                    repository.setThemeMode("LIGHT")
                                }
                            },
                            primaryColor = primaryTextColor,
                            secondaryColor = secondaryTextColor,
                            isDark = isDark,
                            testTag = "settings_toggle_reading_mode"
                        )

                        SettingsDivider(borderColor)

                        // Font Size
                        SettingsClickableRow(
                            iconBoxColor = Color(0xFFDCFCE7),
                            iconVector = Icons.Default.FormatSize,
                            iconTint = Color(0xFF16A34A),
                            title = "Font Size",
                            description = "Adjust text size for better readability",
                            valueText = fontSizePref,
                            onClick = { showFontSizeDialog = true },
                            primaryColor = primaryTextColor,
                            secondaryColor = secondaryTextColor,
                            isDark = isDark,
                            testTag = "settings_row_font_size"
                        )
                    }
                }
            }

            // 4. General Section
            item {
                SettingsSectionHeader(
                    title = "General",
                    icon = Icons.Outlined.Settings,
                    textColor = primaryTextColor
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBackground,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // Language
                        SettingsClickableRow(
                            iconBoxColor = Color(0xFFE0E7FF),
                            iconVector = Icons.Default.Language,
                            iconTint = Color(0xFF3B82F6),
                            title = "Language",
                            description = "Choose your app language",
                            valueText = languagePref,
                            onClick = { showLanguageDialog = true },
                            primaryColor = primaryTextColor,
                            secondaryColor = secondaryTextColor,
                            isDark = isDark,
                            testTag = "settings_row_language"
                        )

                        SettingsDivider(borderColor)

                        // Notifications
                        SettingsToggleRow(
                            iconBoxColor = Color(0xFFF3E8FF),
                            iconVector = Icons.Default.Notifications,
                            iconTint = Color(0xFF8B5CF6),
                            title = "Notifications",
                            description = "Manage your notifications",
                            isChecked = notificationsEnabled,
                            onCheckedChange = { checked ->
                                notificationsEnabled = checked
                                prefs.edit().putBoolean("notifications_enabled", checked).apply()
                            },
                            primaryColor = primaryTextColor,
                            secondaryColor = secondaryTextColor,
                            isDark = isDark,
                            testTag = "settings_toggle_notifications"
                        )

                        SettingsDivider(borderColor)

                        // Auto Save
                        SettingsToggleRow(
                            iconBoxColor = Color(0xFFDCFCE7),
                            iconVector = Icons.Default.CloudDone,
                            iconTint = Color(0xFF10B981),
                            title = "Auto Save",
                            description = "Automatically save your work",
                            isChecked = autoSaveEnabled,
                            onCheckedChange = { checked ->
                                autoSaveEnabled = checked
                                prefs.edit().putBoolean("auto_save_enabled", checked).apply()
                            },
                            primaryColor = primaryTextColor,
                            secondaryColor = secondaryTextColor,
                            isDark = isDark,
                            testTag = "settings_toggle_auto_save"
                        )

                        SettingsDivider(borderColor)

                        // Default Scanner Mode
                        SettingsClickableRow(
                            iconBoxColor = Color(0xFFFFEDD5),
                            iconVector = Icons.Default.QrCodeScanner,
                            iconTint = Color(0xFFF97316),
                            title = "Default Scanner Mode",
                            description = "Set your preferred scan mode",
                            valueText = defaultScannerMode,
                            onClick = { showScannerModeDialog = true },
                            primaryColor = primaryTextColor,
                            secondaryColor = secondaryTextColor,
                            isDark = isDark,
                            testTag = "settings_row_scanner_mode"
                        )

                        SettingsDivider(borderColor)

                        // Automatic Edge Detection & Crop Setting
                        SettingsToggleRow(
                            iconBoxColor = Color(0xFFD1FAE5),
                            iconVector = Icons.Default.CropFree,
                            iconTint = Color(0xFF10B981),
                            title = "Auto Edge Detection & Crop",
                            description = if (autoEdgeDetectionEnabled)
                                "Auto-detect paper edges & dewarp on capture"
                            else
                                "Manual mode: opens crop editor to adjust corners manually",
                            isChecked = autoEdgeDetectionEnabled,
                            onCheckedChange = { isChecked ->
                                autoEdgeDetectionEnabled = isChecked
                                docScanPrefs.edit().putBoolean("pref_auto_edge_detection", isChecked).apply()
                                Toast.makeText(
                                    context,
                                    if (isChecked) "Automatic edge detection enabled" else "Manual crop area adjustment enabled",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            primaryColor = primaryTextColor,
                            secondaryColor = secondaryTextColor,
                            isDark = isDark,
                            testTag = "settings_toggle_auto_edge_detection"
                        )
                    }
                }
            }

            // 5. Data & Privacy Section
            item {
                SettingsSectionHeader(
                    title = "Data & Privacy",
                    icon = Icons.Outlined.Shield,
                    textColor = primaryTextColor
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBackground,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // Clear History
                        SettingsClickableRow(
                            iconBoxColor = Color(0xFFFEE2E2),
                            iconVector = Icons.Default.DeleteOutline,
                            iconTint = Color(0xFFEF4444),
                            title = "Clear History",
                            description = "Remove all scan and tool history",
                            valueText = null,
                            onClick = { showClearHistoryDialog = true },
                            primaryColor = primaryTextColor,
                            secondaryColor = secondaryTextColor,
                            isDark = isDark,
                            testTag = "settings_row_clear_history"
                        )

                        SettingsDivider(borderColor)

                        // Privacy Policy
                        SettingsClickableRow(
                            iconBoxColor = Color(0xFFE0F2FE),
                            iconVector = Icons.Default.Description,
                            iconTint = Color(0xFF0284C7),
                            title = "Privacy Policy",
                            description = "Read our privacy policy",
                            valueText = null,
                            onClick = { showPrivacyDialog = true },
                            primaryColor = primaryTextColor,
                            secondaryColor = secondaryTextColor,
                            isDark = isDark,
                            testTag = "settings_row_privacy_policy"
                        )

                        SettingsDivider(borderColor)

                        // Terms of Service
                        SettingsClickableRow(
                            iconBoxColor = Color(0xFFF3E8FF),
                            iconVector = Icons.Default.Article,
                            iconTint = Color(0xFF9333EA),
                            title = "Terms of Service",
                            description = "Read our terms and conditions",
                            valueText = null,
                            onClick = { showTermsDialog = true },
                            primaryColor = primaryTextColor,
                            secondaryColor = secondaryTextColor,
                            isDark = isDark,
                            testTag = "settings_row_terms"
                        )
                    }
                }
            }

            // 6. About Section
            item {
                SettingsSectionHeader(
                    title = "About",
                    icon = Icons.Outlined.Info,
                    textColor = primaryTextColor
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBackground,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // Version
                        SettingsClickableRow(
                            iconBoxColor = Color(0xFFF1F5F9),
                            iconVector = Icons.Default.Info,
                            iconTint = Color(0xFF64748B),
                            title = "Version",
                            description = "Check for updates",
                            valueText = "v${com.example.BuildConfig.VERSION_NAME}",
                            onClick = {
                                Toast.makeText(context, "Circle Scanner v${com.example.BuildConfig.VERSION_NAME} (Build ${com.example.BuildConfig.VERSION_CODE}) is up to date", Toast.LENGTH_SHORT).show()
                            },
                            primaryColor = primaryTextColor,
                            secondaryColor = secondaryTextColor,
                            isDark = isDark,
                            testTag = "settings_row_version"
                        )

                        SettingsDivider(borderColor)

                        // Rate Us
                        SettingsClickableRow(
                            iconBoxColor = Color(0xFFFEF3C7),
                            iconVector = Icons.Default.Star,
                            iconTint = Color(0xFFF59E0B),
                            title = "Rate Us",
                            description = "Tell us what you think",
                            valueText = null,
                            onClick = {
                                Toast.makeText(context, "Thank you for using Circle Scanner! Your feedback helps us improve.", Toast.LENGTH_LONG).show()
                            },
                            primaryColor = primaryTextColor,
                            secondaryColor = secondaryTextColor,
                            isDark = isDark,
                            testTag = "settings_row_rate_us"
                        )

                        SettingsDivider(borderColor)

                        // Share App
                        SettingsClickableRow(
                            iconBoxColor = Color(0xFFDCFCE7),
                            iconVector = Icons.Default.Share,
                            iconTint = Color(0xFF10B981),
                            title = "Share App",
                            description = "Share with your friends",
                            valueText = null,
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Circle Scanner App")
                                    putExtra(Intent.EXTRA_TEXT, "Download Circle Scanner — All-in-One tools for scanning, PDFs, and daily productivity: https://play.google.com/store")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Circle Scanner"))
                            },
                            primaryColor = primaryTextColor,
                            secondaryColor = secondaryTextColor,
                            isDark = isDark,
                            testTag = "settings_row_share_app"
                        )
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Interactive Dialogs
    // -----------------------------------------------------------------------

    // Theme Mode Dialog
    if (showThemeDialog) {
        val themeChoices = listOf("Light", "Dark", "Reading Mode", "System Default")
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            containerColor = cardBackground,
            titleContentColor = primaryTextColor,
            textContentColor = secondaryTextColor,
            title = { Text("Choose Theme Mode", fontWeight = FontWeight.Bold, color = primaryTextColor) },
            text = {
                Column {
                    themeChoices.forEach { choice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newMode = when (choice) {
                                        "Dark" -> "DARK"
                                        "Reading Mode" -> "READING"
                                        "Light" -> "LIGHT"
                                        else -> "SYSTEM"
                                    }
                                    repository.setThemeMode(newMode)
                                    prefs.edit().putString("theme_mode_choice", choice).apply()
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (themeModePref == choice || (choice == "Reading Mode" && isReading)),
                                onClick = {
                                    val newMode = when (choice) {
                                        "Dark" -> "DARK"
                                        "Reading Mode" -> "READING"
                                        "Light" -> "LIGHT"
                                        else -> "SYSTEM"
                                    }
                                    repository.setThemeMode(newMode)
                                    prefs.edit().putString("theme_mode_choice", choice).apply()
                                    showThemeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = BrandBlue)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = choice, fontSize = 15.sp, color = primaryTextColor)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close", color = BrandBlue)
                }
            }
        )
    }

    // Font Size Dialog
    if (showFontSizeDialog) {
        val sizeChoices = listOf("Small", "Medium", "Large")
        AlertDialog(
            onDismissRequest = { showFontSizeDialog = false },
            containerColor = cardBackground,
            titleContentColor = primaryTextColor,
            textContentColor = secondaryTextColor,
            title = { Text("Adjust Font Size", fontWeight = FontWeight.Bold, color = primaryTextColor) },
            text = {
                Column {
                    sizeChoices.forEach { choice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    fontSizePref = choice
                                    prefs.edit().putString("font_size_choice", choice).apply()
                                    showFontSizeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (fontSizePref == choice),
                                onClick = {
                                    fontSizePref = choice
                                    prefs.edit().putString("font_size_choice", choice).apply()
                                    showFontSizeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = BrandBlue)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = choice, fontSize = 15.sp, color = primaryTextColor)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFontSizeDialog = false }) {
                    Text("Close", color = BrandBlue)
                }
            }
        )
    }

    // Language Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = cardBackground,
            titleContentColor = primaryTextColor,
            textContentColor = secondaryTextColor,
            title = { Text("App Language", fontWeight = FontWeight.Bold, color = primaryTextColor) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                languagePref = "English"
                                prefs.edit().putString("language_choice", "English").apply()
                                showLanguageDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (languagePref == "English"),
                            onClick = {
                                languagePref = "English"
                                prefs.edit().putString("language_choice", "English").apply()
                                showLanguageDialog = false
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = BrandBlue)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "English", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
                            Text(text = "Default language", fontSize = 12.sp, color = secondaryTextColor)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Done", color = BrandBlue)
                }
            }
        )
    }

    // Default Scanner Mode Dialog
    if (showScannerModeDialog) {
        val scannerModes = listOf("QR & Barcode", "Document / PDF", "ID / Card", "Image Scan")
        AlertDialog(
            onDismissRequest = { showScannerModeDialog = false },
            containerColor = cardBackground,
            titleContentColor = primaryTextColor,
            textContentColor = secondaryTextColor,
            title = { Text("Default Scanner Mode", fontWeight = FontWeight.Bold, color = primaryTextColor) },
            text = {
                Column {
                    scannerModes.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    defaultScannerMode = mode
                                    prefs.edit().putString("default_scanner_mode", mode).apply()
                                    showScannerModeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (defaultScannerMode == mode),
                                onClick = {
                                    defaultScannerMode = mode
                                    prefs.edit().putString("default_scanner_mode", mode).apply()
                                    showScannerModeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = BrandBlue)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = mode, fontSize = 15.sp, color = primaryTextColor)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showScannerModeDialog = false }) {
                    Text("Close", color = BrandBlue)
                }
            }
        )
    }

    // Clear History Confirmation Dialog
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            containerColor = cardBackground,
            titleContentColor = primaryTextColor,
            textContentColor = secondaryTextColor,
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("Clear History?", fontWeight = FontWeight.Bold, color = primaryTextColor)
            },
            text = {
                Text(
                    "Are you sure you want to remove all scan records and tool activity? This action cannot be undone.",
                    color = secondaryTextColor,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.clearHistory()
                            Toast.makeText(context, "History cleared successfully", Toast.LENGTH_SHORT).show()
                        }
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Clear History", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel", color = secondaryTextColor)
                }
            }
        )
    }

    // Privacy Policy Dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            containerColor = cardBackground,
            titleContentColor = primaryTextColor,
            textContentColor = secondaryTextColor,
            title = { Text("Privacy Policy", fontWeight = FontWeight.Bold, color = primaryTextColor) },
            text = {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "Zero Cloud Tracking Guarantee",
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) DarkBlue else BrandBlue,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Circle Scanner operates with an on-device privacy policy. All document scans, barcodes, files, photos, calculations, and notes are processed and stored exclusively in private local storage on your device.\n\nWe do not collect, transmit, or sell your personal data or scanned documents to external cloud servers.",
                        color = secondaryTextColor,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Understood", color = BrandBlue)
                }
            }
        )
    }

    // Terms of Service Dialog
    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            containerColor = cardBackground,
            titleContentColor = primaryTextColor,
            textContentColor = secondaryTextColor,
            title = { Text("Terms of Service", fontWeight = FontWeight.Bold, color = primaryTextColor) },
            text = {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "Circle Scanner License & Terms",
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) DarkBlue else BrandBlue,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Circle Scanner is provided for your personal productivity and daily utility needs. All tools including PDF converters, calculators, and scanners are offered as-is with local processing capabilities.\n\nPlease ensure you maintain backup copies of important documents and files.",
                        color = secondaryTextColor,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTermsDialog = false }) {
                    Text("Accept", color = BrandBlue)
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Section Header Component
// ---------------------------------------------------------------------------
@Composable
private fun SettingsSectionHeader(
    title: String,
    icon: ImageVector,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, bottom = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Clickable Row Component (With value text and chevron)
// ---------------------------------------------------------------------------
@Composable
private fun SettingsClickableRow(
    iconBoxColor: Color,
    iconVector: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    valueText: String?,
    onClick: () -> Unit,
    primaryColor: Color,
    secondaryColor: Color,
    isDark: Boolean = false,
    testTag: String
) {
    val boxBg = if (isDark) iconTint.copy(alpha = 0.16f) else iconBoxColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(boxBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = TextStyle(
                    fontSize = 11.5.sp,
                    color = secondaryColor
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (valueText != null) {
            Text(
                text = valueText,
                style = TextStyle(
                    fontSize = 13.sp,
                    color = secondaryColor
                )
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = if (isDark) Color(0xFF64748B) else Color(0xFFCBD5E1),
            modifier = Modifier.size(16.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Toggle Row Component (With Switch)
// ---------------------------------------------------------------------------
@Composable
private fun SettingsToggleRow(
    iconBoxColor: Color,
    iconVector: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    primaryColor: Color,
    secondaryColor: Color,
    isDark: Boolean = false,
    testTag: String
) {
    val boxBg = if (isDark) iconTint.copy(alpha = 0.16f) else iconBoxColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(boxBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = TextStyle(
                    fontSize = 11.5.sp,
                    color = secondaryColor
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BrandBlue,
                uncheckedThumbColor = if (isDark) Color(0xFF94A3B8) else Color.White,
                uncheckedTrackColor = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Subtle Divider between Settings rows
// ---------------------------------------------------------------------------
@Composable
private fun SettingsDivider(color: Color) {
    HorizontalDivider(
        color = color.copy(alpha = 0.6f),
        thickness = 0.8.dp,
        modifier = Modifier.padding(horizontal = 14.dp)
    )
}
