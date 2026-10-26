package com.example.ui.screens.storage

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppBackButton
import com.example.ui.components.AppTopBar
import com.example.ui.components.PrimaryButton
import com.example.utils.DeviceStorageInfo
import com.example.utils.StorageUtils

@Composable
fun StorageAnalyzerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var storageInfo by remember { mutableStateOf(StorageUtils.getDeviceStorageInfo(context)) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Storage Analyzer",
                navigationIcon = { AppBackButton(onBack = onBack) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("storage_analyzer_content"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Internal Device Storage Overview Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("device_storage_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Device Internal Storage",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${storageInfo.usedPercent}% Used",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        LinearProgressIndicator(
                            progress = { storageInfo.usedPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            color = if (storageInfo.usedPercent > 90) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Used", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    StorageUtils.formatFileSize(storageInfo.usedBytes),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Column {
                                Text("Free Available", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    StorageUtils.formatFileSize(storageInfo.freeBytes),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                )
                            }
                            Column {
                                Text("Total Capacity", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    StorageUtils.formatFileSize(storageInfo.totalBytes),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }

            // App Storage Breakdown Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("app_storage_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "Circle Scanner Storage Footprint",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Temporary App Cache:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                StorageUtils.formatFileSize(storageInfo.appCacheBytes),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Saved Documents & Images:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                StorageUtils.formatFileSize(storageInfo.appFilesBytes),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        HorizontalDivider()

                        PrimaryButton(
                            text = "Clean Temporary Cache",
                            icon = Icons.Default.CleaningServices,
                            onClick = {
                                StorageUtils.clearAppCache(context)
                                storageInfo = StorageUtils.getDeviceStorageInfo(context)
                                Toast.makeText(context, "Cache cleaned successfully!", Toast.LENGTH_SHORT).show()
                            },
                            testTag = "clean_cache_btn"
                        )
                    }
                }
            }
        }
    }
}
