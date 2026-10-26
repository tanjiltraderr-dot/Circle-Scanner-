package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.core.content.FileProvider
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeviceStorageInfo(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val usedPercent: Int,
    val appCacheBytes: Long,
    val appFilesBytes: Long
)

data class ManagedFileItem(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val lastModified: String,
    val extension: String,
    val isDirectory: Boolean
)

object StorageUtils {

    fun getDeviceStorageInfo(context: Context): DeviceStorageInfo {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val total = totalBlocks * blockSize
            val free = availableBlocks * blockSize
            val used = total - free
            val usedPct = if (total > 0) ((used.toDouble() / total.toDouble()) * 100).toInt() else 0

            val cacheBytes = getFolderSize(context.cacheDir) + (context.externalCacheDir?.let { getFolderSize(it) } ?: 0L)
            val filesBytes = getFolderSize(context.filesDir)

            DeviceStorageInfo(
                totalBytes = total,
                freeBytes = free,
                usedBytes = used,
                usedPercent = usedPct,
                appCacheBytes = cacheBytes,
                appFilesBytes = filesBytes
            )
        } catch (e: Exception) {
            DeviceStorageInfo(0, 0, 0, 0, 0, 0)
        }
    }

    fun getFolderSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size: Long = 0
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isDirectory) getFolderSize(f) else f.length()
        }
        return size
    }

    fun clearAppCache(context: Context): Boolean {
        return try {
            deleteDirRecursive(context.cacheDir)
            context.externalCacheDir?.let { deleteDirRecursive(it) }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun deleteDirRecursive(dir: File?): Boolean {
        if (dir != null && dir.isDirectory) {
            val children = dir.list() ?: return false
            for (child in children) {
                val success = deleteDirRecursive(File(dir, child))
                if (!success) return false
            }
            return dir.delete()
        } else if (dir != null && dir.isFile) {
            return dir.delete()
        }
        return false
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val df = DecimalFormat("#,##0.#")
        return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
    }

    fun shareFile(context: Context, file: File, mimeType: String = "*/*") {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share file"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getAppManagedFiles(context: Context): List<ManagedFileItem> {
        val list = mutableListOf<ManagedFileItem>()
        val baseDir = context.filesDir
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

        fun scan(dir: File) {
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.isDirectory) {
                    scan(f)
                } else {
                    list.add(
                        ManagedFileItem(
                            file = f,
                            name = f.name,
                            sizeBytes = f.length(),
                            formattedSize = formatFileSize(f.length()),
                            lastModified = sdf.format(Date(f.lastModified())),
                            extension = f.extension.uppercase(),
                            isDirectory = false
                        )
                    )
                }
            }
        }
        scan(baseDir)
        return list.sortedByDescending { it.file.lastModified() }
    }
}
