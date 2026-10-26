package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import java.net.URLEncoder

sealed class Screen(val route: String) {
    // Bottom Nav Screens
    object Home : Screen("home")
    object Tools : Screen("tools")
    object CategoryDetails : Screen("category/{categoryId}") {
        fun createRoute(categoryId: String) = "category/$categoryId"
    }
    object Scanner : Screen("scanner?mode={mode}") {
        fun createRoute(mode: String = "QR_BARCODE") = "scanner?mode=$mode"
    }
    object ScanResult : Screen("scan_result?content={content}&format={format}") {
        fun createRoute(content: String, format: String = "QR_CODE"): String {
            val encoded = try {
                URLEncoder.encode(content, "UTF-8")
            } catch (e: Exception) {
                content
            }
            return "scan_result?content=$encoded&format=$format"
        }
    }
    object DocumentViewer : Screen("document_viewer?filePath={filePath}&title={title}&docId={docId}") {
        fun createRoute(filePath: String, title: String = "Scanned Document", docId: Long = -1L): String {
            val encPath = try {
                URLEncoder.encode(filePath, "UTF-8")
            } catch (e: Exception) {
                filePath
            }
            val encTitle = try {
                URLEncoder.encode(title, "UTF-8")
            } catch (e: Exception) {
                title
            }
            return "document_viewer?filePath=$encPath&title=$encTitle&docId=$docId"
        }
    }
    object History : Screen("history")
    object PastScans : Screen("past_scans")
    object DocScan : Screen("doc_scan")
    object DocScanDetail : Screen("doc_scan_detail?docId={docId}") {
        fun createRoute(docId: String) = "doc_scan_detail?docId=$docId"
    }
    object Pro : Screen("pro")
    object Settings : Screen("settings")

    // Tool Screens
    object QrScanner : Screen("tool_qr_scanner")
    object BarcodeScanner : Screen("tool_barcode_scanner")
    object DocumentScanner : Screen("tool_doc_scanner")
    object PdfTools : Screen("tool_pdf_tools?tab={tab}") {
        fun createRoute(tab: Int = 0) = "tool_pdf_tools?tab=$tab"
    }
    object ImageTools : Screen("tool_image_tools?tab={tab}") {
        fun createRoute(tab: Int = 0) = "tool_image_tools?tab=$tab"
    }
    object QrGenerator : Screen("tool_qr_gen?type={type}") {
        fun createRoute(type: String = "TEXT") = "tool_qr_gen?type=$type"
    }
    object ExpenseTracker : Screen("tool_expense")
    object Calculator : Screen("tool_calculator")
    object UnitConverter : Screen("tool_unit_converter")
    object PercentageCalculator : Screen("tool_percentage")
    object AgeDateCalculator : Screen("tool_age_date?mode={mode}") {
        fun createRoute(mode: String = "AGE") = "tool_age_date?mode=$mode"
    }
    object Notes : Screen("tool_notes")
    object Stopwatch : Screen("tool_stopwatch")
    object Timer : Screen("tool_timer")
    object PasswordGenerator : Screen("tool_password_gen")
    object StorageAnalyzer : Screen("tool_storage_analyzer")
    object FileManager : Screen("tool_file_manager")
}

data class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)
