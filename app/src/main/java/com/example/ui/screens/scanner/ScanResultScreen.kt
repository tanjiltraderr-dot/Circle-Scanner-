package com.example.ui.screens.scanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.UtilityHubApp
import com.example.ui.components.AppBackButton
import com.example.ui.components.AppTopBar
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import kotlinx.coroutines.launch
import java.net.URLDecoder

enum class ScannedType {
    WEBSITE,
    WIFI,
    EMAIL,
    PHONE,
    SMS,
    CONTACT,
    LOCATION,
    BARCODE,
    PLAIN_TEXT
}

@Composable
fun ScanResultScreen(
    rawContent: String,
    format: String = "QR_CODE",
    onBack: () -> Unit,
    onScanAgain: () -> Unit = onBack
) {
    val context = LocalContext.current
    val repository = remember { UtilityHubApp.instance.repository }
    val scope = rememberCoroutineScope()

    val decodedContent = remember(rawContent) {
        try {
            URLDecoder.decode(rawContent, "UTF-8")
        } catch (e: Exception) {
            rawContent
        }
    }

    // Determine type
    val scannedType = remember(decodedContent, format) {
        classifyContent(decodedContent, format)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Scan Result",
                navigationIcon = { AppBackButton(onBack = onBack) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("scan_result_screen"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Classified Badge Icon
            val (icon, typeTitle, typeColor) = getTypeDetails(scannedType, format)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(typeColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = typeTitle,
                    tint = typeColor,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Type Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = typeColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text = typeTitle,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = typeColor,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Result Content Container
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Decoded Information",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = decodedContent,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Context-Sensitive Actions
            when (scannedType) {
                ScannedType.WEBSITE -> {
                    val url = if (!decodedContent.startsWith("http://", ignoreCase = true) &&
                        !decodedContent.startsWith("https://", ignoreCase = true)
                    ) "https://$decodedContent" else decodedContent

                    PrimaryButton(
                        text = "Visit Website",
                        icon = Icons.Default.OpenInBrowser,
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                            }
                        },
                        testTag = "visit_website_btn"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SecondaryButton(
                            text = "Copy Link",
                            icon = Icons.Default.ContentCopy,
                            onClick = { copyToClipboard(context, "URL", url) },
                            modifier = Modifier.weight(1f),
                            testTag = "copy_url_btn"
                        )
                        SecondaryButton(
                            text = "Share",
                            icon = Icons.Default.Share,
                            onClick = { shareText(context, url) },
                            modifier = Modifier.weight(1f),
                            testTag = "share_url_btn"
                        )
                    }
                }

                ScannedType.WIFI -> {
                    val (ssid, pass, type) = parseWifiDetails(decodedContent)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Network (SSID): $ssid", fontWeight = FontWeight.SemiBold)
                            if (pass.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Password: $pass", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (type.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Security: $type", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    if (pass.isNotEmpty()) {
                        PrimaryButton(
                            text = "Copy Wi-Fi Password",
                            icon = Icons.Default.Key,
                            onClick = { copyToClipboard(context, "Wi-Fi Password", pass) },
                            testTag = "copy_wifi_pass_btn"
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SecondaryButton(
                            text = "Copy SSID",
                            icon = Icons.Default.Wifi,
                            onClick = { copyToClipboard(context, "Wi-Fi Name", ssid) },
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryButton(
                            text = "Share Details",
                            icon = Icons.Default.Share,
                            onClick = { shareText(context, "Wi-Fi Network: $ssid\nPassword: $pass") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                ScannedType.EMAIL -> {
                    val email = decodedContent.removePrefix("mailto:")
                    PrimaryButton(
                        text = "Send Email",
                        icon = Icons.Default.Email,
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                            }
                        },
                        testTag = "send_email_btn"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SecondaryButton(
                            text = "Copy Email",
                            icon = Icons.Default.ContentCopy,
                            onClick = { copyToClipboard(context, "Email", email) },
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryButton(
                            text = "Share",
                            icon = Icons.Default.Share,
                            onClick = { shareText(context, email) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                ScannedType.PHONE -> {
                    val phone = decodedContent.removePrefix("tel:")
                    PrimaryButton(
                        text = "Call Number",
                        icon = Icons.Default.Phone,
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open dialer", Toast.LENGTH_SHORT).show()
                            }
                        },
                        testTag = "call_phone_btn"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SecondaryButton(
                            text = "Copy Number",
                            icon = Icons.Default.ContentCopy,
                            onClick = { copyToClipboard(context, "Phone", phone) },
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryButton(
                            text = "Share",
                            icon = Icons.Default.Share,
                            onClick = { shareText(context, phone) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                ScannedType.SMS -> {
                    val (smsNumber, smsBody) = parseSms(decodedContent)
                    PrimaryButton(
                        text = "Compose SMS",
                        icon = Icons.Default.Sms,
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$smsNumber")).apply {
                                    if (smsBody.isNotEmpty()) putExtra("sms_body", smsBody)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open SMS app", Toast.LENGTH_SHORT).show()
                            }
                        },
                        testTag = "compose_sms_btn"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SecondaryButton(
                            text = "Copy Message",
                            icon = Icons.Default.ContentCopy,
                            onClick = { copyToClipboard(context, "SMS", smsBody.ifEmpty { smsNumber }) },
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryButton(
                            text = "Share",
                            icon = Icons.Default.Share,
                            onClick = { shareText(context, decodedContent) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                ScannedType.CONTACT -> {
                    PrimaryButton(
                        text = "Save Contact",
                        icon = Icons.Default.PersonAdd,
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_INSERT).apply {
                                    type = ContactsContract.RawContacts.CONTENT_TYPE
                                    putExtra(ContactsContract.Intents.Insert.NAME, extractVCardField(decodedContent, "FN:"))
                                    putExtra(ContactsContract.Intents.Insert.PHONE, extractVCardField(decodedContent, "TEL:"))
                                    putExtra(ContactsContract.Intents.Insert.EMAIL, extractVCardField(decodedContent, "EMAIL:"))
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not save contact", Toast.LENGTH_SHORT).show()
                            }
                        },
                        testTag = "save_contact_btn"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SecondaryButton(
                            text = "Copy Contact",
                            icon = Icons.Default.ContentCopy,
                            onClick = { copyToClipboard(context, "Contact", decodedContent) },
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryButton(
                            text = "Share",
                            icon = Icons.Default.Share,
                            onClick = { shareText(context, decodedContent) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                ScannedType.LOCATION -> {
                    PrimaryButton(
                        text = "View on Map",
                        icon = Icons.Default.Map,
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(decodedContent))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open map app", Toast.LENGTH_SHORT).show()
                            }
                        },
                        testTag = "view_map_btn"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SecondaryButton(
                            text = "Copy Coordinates",
                            icon = Icons.Default.ContentCopy,
                            onClick = { copyToClipboard(context, "Coordinates", decodedContent.removePrefix("geo:")) },
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryButton(
                            text = "Share",
                            icon = Icons.Default.Share,
                            onClick = { shareText(context, decodedContent) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                ScannedType.BARCODE, ScannedType.PLAIN_TEXT -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PrimaryButton(
                            text = "Copy Content",
                            icon = Icons.Default.ContentCopy,
                            onClick = { copyToClipboard(context, "Scanned Text", decodedContent) },
                            modifier = Modifier.weight(1f),
                            testTag = "copy_text_btn"
                        )
                        SecondaryButton(
                            text = "Share",
                            icon = Icons.Default.Share,
                            onClick = { shareText(context, decodedContent) },
                            modifier = Modifier.weight(1f),
                            testTag = "share_text_btn"
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    SecondaryButton(
                        text = "Save to History",
                        icon = Icons.Default.BookmarkBorder,
                        onClick = {
                            scope.launch {
                                repository.addHistory("qr_scanner", "Saved Scanned Content", decodedContent)
                                Toast.makeText(context, "Saved to Activity History", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scan Again Action
            TextButton(
                onClick = onScanAgain,
                modifier = Modifier.testTag("scan_again_action_btn")
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan Another Code", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun classifyContent(content: String, format: String): ScannedType {
    if (format.isNotEmpty() && !format.equals("QR_CODE", ignoreCase = true) && !format.equals("DATA_MATRIX", ignoreCase = true)) {
        return ScannedType.BARCODE
    }
    val trimmed = content.trim()
    return when {
        trimmed.startsWith("WIFI:", ignoreCase = true) -> ScannedType.WIFI
        trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) ||
                (trimmed.contains(".") && !trimmed.contains(" ") && (trimmed.startsWith("www.", ignoreCase = true) || trimmed.endsWith(".com") || trimmed.endsWith(".org") || trimmed.endsWith(".net"))) -> ScannedType.WEBSITE
        trimmed.startsWith("mailto:", ignoreCase = true) || (trimmed.contains("@") && trimmed.contains(".") && !trimmed.contains(" ")) -> ScannedType.EMAIL
        trimmed.startsWith("tel:", ignoreCase = true) || trimmed.matches(Regex("^[+]?[0-9\\-\\s()]{7,}$")) -> ScannedType.PHONE
        trimmed.startsWith("smsto:", ignoreCase = true) || trimmed.startsWith("sms:", ignoreCase = true) -> ScannedType.SMS
        trimmed.startsWith("BEGIN:VCARD", ignoreCase = true) || trimmed.startsWith("MECARD:", ignoreCase = true) -> ScannedType.CONTACT
        trimmed.startsWith("geo:", ignoreCase = true) -> ScannedType.LOCATION
        else -> ScannedType.PLAIN_TEXT
    }
}

private fun getTypeDetails(type: ScannedType, format: String): Triple<ImageVector, String, Color> {
    return when (type) {
        ScannedType.WEBSITE -> Triple(Icons.Default.Language, "Website Link", Color(0xFF356AE6))
        ScannedType.WIFI -> Triple(Icons.Default.Wifi, "Wi-Fi Network", Color(0xFF10B981))
        ScannedType.EMAIL -> Triple(Icons.Default.Email, "Email Address", Color(0xFFEF4444))
        ScannedType.PHONE -> Triple(Icons.Default.Phone, "Phone Number", Color(0xFF8B5CF6))
        ScannedType.SMS -> Triple(Icons.Default.Sms, "SMS Message", Color(0xFF06B6D4))
        ScannedType.CONTACT -> Triple(Icons.Default.ContactPage, "Contact Card", Color(0xFFF59E0B))
        ScannedType.LOCATION -> Triple(Icons.Default.LocationOn, "Geo Location", Color(0xFFEC4899))
        ScannedType.BARCODE -> Triple(Icons.Default.ViewWeek, "Barcode ($format)", Color(0xFF6366F1))
        ScannedType.PLAIN_TEXT -> Triple(Icons.Default.TextFields, "Plain Text", Color(0xFF707887))
    }
}

private fun parseWifiDetails(raw: String): Triple<String, String, String> {
    val ssid = Regex("S:([^;]+);", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.getOrNull(1) ?: ""
    val pass = Regex("P:([^;]+);", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.getOrNull(1) ?: ""
    val type = Regex("T:([^;]+);", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.getOrNull(1) ?: ""
    return Triple(ssid, pass, type)
}

private fun parseSms(raw: String): Pair<String, String> {
    val clean = raw.removePrefix("smsto:").removePrefix("sms:")
    val parts = clean.split(":", "?body=")
    val number = parts.getOrNull(0) ?: ""
    val body = parts.getOrNull(1) ?: ""
    return Pair(number, body)
}

private fun extractVCardField(vCard: String, prefix: String): String {
    val line = vCard.lines().firstOrNull { it.startsWith(prefix, ignoreCase = true) }
    return line?.substringAfter(prefix)?.trim() ?: ""
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share via"))
}
