package com.example.ui.screens.qr

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.UtilityHubApp
import com.example.ui.components.AppBackButton
import com.example.ui.components.AppTopBar
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.utils.ImageUtils
import com.example.utils.QrCodeHelper
import com.example.utils.StorageUtils
import kotlinx.coroutines.launch
import java.io.File

enum class QrType(val title: String) {
    TEXT("Text"),
    URL("URL / Web"),
    WIFI("Wi-Fi"),
    CONTACT("Contact"),
    PHONE("Phone"),
    EMAIL("Email")
}

@Composable
fun QrGeneratorScreen(
    initialType: String = "TEXT",
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { UtilityHubApp.instance.repository }
    val scope = rememberCoroutineScope()

    var selectedType by remember {
        mutableStateOf(try { QrType.valueOf(initialType) } catch (e: Exception) { QrType.TEXT })
    }

    // Input States
    var textInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("https://") }
    var phoneInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var emailSubject by remember { mutableStateOf("") }

    // Wi-Fi inputs
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var wifiSecurity by remember { mutableStateOf("WPA/WPA2") }

    // Contact inputs
    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var contactEmail by remember { mutableStateOf("") }
    var contactOrg by remember { mutableStateOf("") }

    var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var savedQrFile by remember { mutableStateOf<File?>(null) }

    fun buildContentString(): String? {
        return when (selectedType) {
            QrType.TEXT -> textInput.takeIf { it.isNotBlank() }
            QrType.URL -> urlInput.takeIf { it.length > 8 }
            QrType.WIFI -> {
                if (wifiSsid.isBlank()) null
                else QrCodeHelper.formatWifiString(wifiSsid, wifiPassword, wifiSecurity)
            }
            QrType.CONTACT -> {
                if (contactName.isBlank()) null
                else QrCodeHelper.formatVCard(contactName, contactPhone, contactEmail, contactOrg)
            }
            QrType.PHONE -> {
                if (phoneInput.isBlank()) null
                else "tel:$phoneInput"
            }
            QrType.EMAIL -> {
                if (emailInput.isBlank()) null
                else "mailto:$emailInput?subject=$emailSubject"
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "QR Generator",
                navigationIcon = { AppBackButton(onBack = onBack) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("qr_generator_content"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Type Selector Chips
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(QrType.values()) { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = {
                                selectedType = type
                                generatedBitmap = null
                                savedQrFile = null
                            },
                            label = { Text(type.title) },
                            modifier = Modifier.testTag("qr_type_${type.name}")
                        )
                    }
                }
            }

            // Input Fields based on Type
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        when (selectedType) {
                            QrType.TEXT -> {
                                OutlinedTextField(
                                    value = textInput,
                                    onValueChange = { textInput = it },
                                    label = { Text("Text Content") },
                                    placeholder = { Text("Type any message or note...") },
                                    modifier = Modifier.fillMaxWidth().testTag("qr_text_input")
                                )
                            }
                            QrType.URL -> {
                                OutlinedTextField(
                                    value = urlInput,
                                    onValueChange = { urlInput = it },
                                    label = { Text("Website Address") },
                                    modifier = Modifier.fillMaxWidth().testTag("qr_url_input")
                                )
                            }
                            QrType.WIFI -> {
                                OutlinedTextField(
                                    value = wifiSsid,
                                    onValueChange = { wifiSsid = it },
                                    label = { Text("Network Name (SSID)") },
                                    modifier = Modifier.fillMaxWidth().testTag("qr_wifi_ssid_input")
                                )
                                OutlinedTextField(
                                    value = wifiPassword,
                                    onValueChange = { wifiPassword = it },
                                    label = { Text("Password") },
                                    modifier = Modifier.fillMaxWidth().testTag("qr_wifi_pass_input")
                                )
                            }
                            QrType.CONTACT -> {
                                OutlinedTextField(
                                    value = contactName,
                                    onValueChange = { contactName = it },
                                    label = { Text("Full Name") },
                                    modifier = Modifier.fillMaxWidth().testTag("qr_contact_name_input")
                                )
                                OutlinedTextField(
                                    value = contactPhone,
                                    onValueChange = { contactPhone = it },
                                    label = { Text("Phone Number") },
                                    modifier = Modifier.fillMaxWidth().testTag("qr_contact_phone_input")
                                )
                                OutlinedTextField(
                                    value = contactEmail,
                                    onValueChange = { contactEmail = it },
                                    label = { Text("Email Address") },
                                    modifier = Modifier.fillMaxWidth().testTag("qr_contact_email_input")
                                )
                            }
                            QrType.PHONE -> {
                                OutlinedTextField(
                                    value = phoneInput,
                                    onValueChange = { phoneInput = it },
                                    label = { Text("Phone Number") },
                                    modifier = Modifier.fillMaxWidth().testTag("qr_phone_input")
                                )
                            }
                            QrType.EMAIL -> {
                                OutlinedTextField(
                                    value = emailInput,
                                    onValueChange = { emailInput = it },
                                    label = { Text("Email Address") },
                                    modifier = Modifier.fillMaxWidth().testTag("qr_email_input")
                                )
                                OutlinedTextField(
                                    value = emailSubject,
                                    onValueChange = { emailSubject = it },
                                    label = { Text("Subject (Optional)") },
                                    modifier = Modifier.fillMaxWidth().testTag("qr_email_sub_input")
                                )
                            }
                        }

                        PrimaryButton(
                            text = "Generate QR Code",
                            icon = Icons.Default.QrCode,
                            onClick = {
                                val content = buildContentString()
                                if (content != null) {
                                    val bmp = QrCodeHelper.generateQrBitmap(content)
                                    generatedBitmap = bmp
                                    if (bmp != null) {
                                        val file = ImageUtils.saveBitmapToGalleryOrApp(context, bmp, "qr_${System.currentTimeMillis()}")
                                        savedQrFile = file
                                        scope.launch {
                                            repository.addHistory("qr_gen", "Generated ${selectedType.title} QR", content.take(60))
                                        }
                                        Toast.makeText(context, "QR Code generated!", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Please fill in required fields", Toast.LENGTH_SHORT).show()
                                }
                            },
                            testTag = "generate_qr_code_btn"
                        )
                    }
                }
            }

            // QR Preview & Export
            if (generatedBitmap != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("generated_qr_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(240.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = generatedBitmap!!.asImageBitmap(),
                                    contentDescription = "Generated QR Code",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (savedQrFile != null) {
                                SecondaryButton(
                                    text = "Share QR Code",
                                    icon = Icons.Default.Share,
                                    onClick = {
                                        StorageUtils.shareFile(context, savedQrFile!!, "image/png")
                                    },
                                    testTag = "share_generated_qr_btn"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
