package com.example.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.*

enum class ToolCategory(
    val categoryId: String,
    val title: String,
    val shortDescription: String,
    val icon: ImageVector,
    val accentColor: Color
) {
    SCANNER(
        categoryId = "scanner",
        title = "Scanner",
        shortDescription = "Scan QR codes, barcodes and documents",
        icon = Icons.Default.QrCodeScanner,
        accentColor = CategoryScannerColor
    ),
    PDF(
        categoryId = "pdf",
        title = "PDF Tools",
        shortDescription = "Convert, merge, split and compress PDFs",
        icon = Icons.Default.PictureAsPdf,
        accentColor = CategoryPdfColor
    ),
    QR_BARCODE(
        categoryId = "qr_barcode",
        title = "QR & Barcode",
        shortDescription = "Generate custom QR codes and barcodes",
        icon = Icons.Default.QrCode,
        accentColor = CategoryQrColor
    ),
    IMAGE(
        categoryId = "image",
        title = "Image Tools",
        shortDescription = "Compress, resize, crop and convert images",
        icon = Icons.Default.Image,
        accentColor = CategoryImageColor
    ),
    FILES(
        categoryId = "files",
        title = "File Tools",
        shortDescription = "Storage analyzer, file manager and downloads",
        icon = Icons.Default.Folder,
        accentColor = CategoryStorageColor
    ),
    FINANCE(
        categoryId = "finance",
        title = "Expense Tracker",
        shortDescription = "Track daily expenses, income and budgets",
        icon = Icons.Default.AccountBalanceWallet,
        accentColor = CategoryFinanceColor
    ),
    CALCULATOR(
        categoryId = "calculator",
        title = "Calculator & Converter",
        shortDescription = "Calculators, percentage, units and dates",
        icon = Icons.Default.Calculate,
        accentColor = CategoryCalculatorColor
    ),
    PRODUCTIVITY(
        categoryId = "productivity",
        title = "Productivity",
        shortDescription = "Quick notes, stopwatch and countdown timer",
        icon = Icons.Default.EditNote,
        accentColor = CategoryProductivityColor
    ),
    SECURITY(
        categoryId = "security",
        title = "Security",
        shortDescription = "Strong password generator and privacy tools",
        icon = Icons.Default.Key,
        accentColor = CategorySecurityColor
    ),
    OTHER(
        categoryId = "other",
        title = "Other Utilities",
        shortDescription = "Device storage tools and app utilities",
        icon = Icons.Default.Build,
        accentColor = BrandBlue
    );

    companion object {
        fun fromId(id: String): ToolCategory {
            return entries.firstOrNull {
                it.categoryId.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true)
            } ?: SCANNER
        }
    }
}

data class ToolItem(
    val id: String,
    val name: String,
    val description: String,
    val category: ToolCategory,
    val icon: ImageVector,
    val route: String,
    val keywords: List<String> = emptyList(),
    val isQuickTool: Boolean = false
)

object ToolRegistry {
    val allTools: List<ToolItem> = listOf(
        // 1. Scanner Tools
        ToolItem(
            id = "qr_scanner",
            name = "QR & Barcode Scanner",
            description = "Scan QR codes and barcodes with live camera",
            category = ToolCategory.SCANNER,
            icon = Icons.Default.QrCodeScanner,
            route = "scanner?mode=QR_BARCODE",
            keywords = listOf("camera", "scan", "barcode", "reader"),
            isQuickTool = true
        ),
        ToolItem(
            id = "doc_scanner",
            name = "Document / PDF Scanner",
            description = "Scan multi-page documents, crop & export to PDF",
            category = ToolCategory.SCANNER,
            icon = Icons.Default.DocumentScanner,
            route = "tool_doc_scanner",
            keywords = listOf("pdf", "camscanner", "paper", "receipt"),
            isQuickTool = true
        ),
        ToolItem(
            id = "id_card_scanner",
            name = "ID / Card Scanner",
            description = "Capture IDs, badges and business cards",
            category = ToolCategory.SCANNER,
            icon = Icons.Default.Badge,
            route = "scanner?mode=ID_CARD",
            keywords = listOf("license", "passport", "card")
        ),
        ToolItem(
            id = "image_scanner",
            name = "Image Scanner",
            description = "Process and enhance captured photos",
            category = ToolCategory.SCANNER,
            icon = Icons.Default.CropFree,
            route = "scanner?mode=IMAGE_SCAN",
            keywords = listOf("photo", "capture", "enhance")
        ),
        ToolItem(
            id = "scan_from_photo",
            name = "Scan from Photo",
            description = "Import an image from gallery to decode QR & barcodes",
            category = ToolCategory.SCANNER,
            icon = Icons.Default.PhotoLibrary,
            route = "scanner?mode=QR_BARCODE",
            keywords = listOf("gallery", "picture", "import")
        ),
        ToolItem(
            id = "scan_history_tool",
            name = "Scan History",
            description = "View previously scanned codes and documents",
            category = ToolCategory.SCANNER,
            icon = Icons.Default.History,
            route = "history",
            keywords = listOf("log", "scans", "saved")
        ),

        // 2. PDF Tools
        ToolItem(
            id = "img_to_pdf",
            name = "Image to PDF",
            description = "Convert photos into high quality PDF documents",
            category = ToolCategory.PDF,
            icon = Icons.Default.PictureAsPdf,
            route = "tool_pdf_tools?tab=0",
            keywords = listOf("convert", "jpg to pdf", "png to pdf"),
            isQuickTool = true
        ),
        ToolItem(
            id = "pdf_to_img",
            name = "PDF to Image",
            description = "Extract pages from PDF files as crisp images",
            category = ToolCategory.PDF,
            icon = Icons.Default.Image,
            route = "tool_pdf_tools?tab=1",
            keywords = listOf("extract", "pdf to jpg", "render")
        ),
        ToolItem(
            id = "pdf_merge",
            name = "PDF Merge",
            description = "Combine multiple PDF files into a single document",
            category = ToolCategory.PDF,
            icon = Icons.Default.CallMerge,
            route = "tool_pdf_tools?tab=2",
            keywords = listOf("combine", "join", "binder")
        ),
        ToolItem(
            id = "pdf_split",
            name = "PDF Split",
            description = "Extract pages or split PDFs into smaller parts",
            category = ToolCategory.PDF,
            icon = Icons.Default.CallSplit,
            route = "tool_pdf_tools?tab=3",
            keywords = listOf("cut", "separate", "pages")
        ),
        ToolItem(
            id = "pdf_compress",
            name = "PDF Compression",
            description = "Reduce PDF file size for easy sharing",
            category = ToolCategory.PDF,
            icon = Icons.Default.Compress,
            route = "tool_pdf_tools?tab=4",
            keywords = listOf("shrink", "size", "reduce")
        ),
        ToolItem(
            id = "pdf_viewer_info",
            name = "PDF Viewer & Info",
            description = "View PDF files and inspect page dimensions",
            category = ToolCategory.PDF,
            icon = Icons.Default.Visibility,
            route = "tool_pdf_tools?tab=0",
            keywords = listOf("read", "open", "metadata")
        ),

        // 3. QR & Barcode Tools
        ToolItem(
            id = "qr_scanner_cat",
            name = "QR Scanner",
            description = "Fast real-time QR code reader",
            category = ToolCategory.QR_BARCODE,
            icon = Icons.Default.QrCodeScanner,
            route = "scanner?mode=QR_BARCODE",
            keywords = listOf("quick response", "scan")
        ),
        ToolItem(
            id = "barcode_scanner_cat",
            name = "Barcode Scanner",
            description = "Scan UPC, EAN, Code 128 barcodes",
            category = ToolCategory.QR_BARCODE,
            icon = Icons.Default.ViewWeek,
            route = "scanner?mode=QR_BARCODE",
            keywords = listOf("product", "upc", "ean")
        ),
        ToolItem(
            id = "qr_gen_text",
            name = "Text QR Generator",
            description = "Create customizable QR codes for plain text",
            category = ToolCategory.QR_BARCODE,
            icon = Icons.Default.QrCode,
            route = "tool_qr_gen?type=TEXT",
            keywords = listOf("create qr", "maker")
        ),
        ToolItem(
            id = "qr_gen_url",
            name = "URL QR Generator",
            description = "Generate clickable web link QR codes",
            category = ToolCategory.QR_BARCODE,
            icon = Icons.Default.Link,
            route = "tool_qr_gen?type=URL",
            keywords = listOf("link", "website", "http")
        ),
        ToolItem(
            id = "qr_gen_wifi",
            name = "Wi-Fi QR Generator",
            description = "Share wireless network credentials instantly",
            category = ToolCategory.QR_BARCODE,
            icon = Icons.Default.Wifi,
            route = "tool_qr_gen?type=WIFI",
            keywords = listOf("network", "password", "wlan")
        ),
        ToolItem(
            id = "qr_gen_contact",
            name = "Contact QR Generator",
            description = "Generate vCard QR codes with name, phone & email",
            category = ToolCategory.QR_BARCODE,
            icon = Icons.Default.ContactPage,
            route = "tool_qr_gen?type=CONTACT",
            keywords = listOf("vcard", "business card", "person")
        ),
        ToolItem(
            id = "qr_gen_phone",
            name = "Phone QR Generator",
            description = "Generate QR codes to dial a phone number directly",
            category = ToolCategory.QR_BARCODE,
            icon = Icons.Default.Phone,
            route = "tool_qr_gen?type=PHONE",
            keywords = listOf("call", "dial", "number")
        ),
        ToolItem(
            id = "qr_gen_email",
            name = "Email QR Generator",
            description = "Create QR codes pre-filled with email address",
            category = ToolCategory.QR_BARCODE,
            icon = Icons.Default.Email,
            route = "tool_qr_gen?type=EMAIL",
            keywords = listOf("mail", "send", "mailto")
        ),
        ToolItem(
            id = "qr_scan_image",
            name = "Scan from Image",
            description = "Pick an image from gallery to decode QR codes",
            category = ToolCategory.QR_BARCODE,
            icon = Icons.Default.Photo,
            route = "scanner?mode=QR_BARCODE",
            keywords = listOf("gallery", "decode")
        ),

        // 4. Image Tools
        ToolItem(
            id = "img_compress",
            name = "Image Compressor",
            description = "Reduce photo size with target quality controls",
            category = ToolCategory.IMAGE,
            icon = Icons.Default.PhotoSizeSelectLarge,
            route = "tool_image_tools?tab=0",
            keywords = listOf("shrink", "resize bytes", "optimize"),
            isQuickTool = true
        ),
        ToolItem(
            id = "img_resize",
            name = "Image Resize",
            description = "Change image pixel dimensions & maintain aspect ratio",
            category = ToolCategory.IMAGE,
            icon = Icons.Default.AspectRatio,
            route = "tool_image_tools?tab=1",
            keywords = listOf("width", "height", "scale")
        ),
        ToolItem(
            id = "img_crop",
            name = "Image Crop",
            description = "Crop to 1:1, 4:3, 16:9 ratios, rotate & flip",
            category = ToolCategory.IMAGE,
            icon = Icons.Default.Crop,
            route = "tool_image_tools?tab=2",
            keywords = listOf("cut", "trim", "rotate")
        ),
        ToolItem(
            id = "img_convert",
            name = "Image Converter",
            description = "Convert images between JPG, PNG and WEBP formats",
            category = ToolCategory.IMAGE,
            icon = Icons.Default.Transform,
            route = "tool_image_tools?tab=3",
            keywords = listOf("png", "jpg", "webp")
        ),
        ToolItem(
            id = "img_info_tool",
            name = "Image Information",
            description = "Inspect image resolution, mime type and byte size",
            category = ToolCategory.IMAGE,
            icon = Icons.Default.Info,
            route = "tool_image_tools?tab=0",
            keywords = listOf("metadata", "dimensions")
        ),

        // 5. File Tools
        ToolItem(
            id = "file_manager",
            name = "File Browser",
            description = "Organize, view and share created and exported files",
            category = ToolCategory.FILES,
            icon = Icons.Default.Folder,
            route = "tool_file_manager",
            keywords = listOf("files", "storage", "downloads", "browse"),
            isQuickTool = true
        ),
        ToolItem(
            id = "storage_analyzer",
            name = "Storage Analyzer",
            description = "Inspect device storage, breakdown & clear app cache",
            category = ToolCategory.FILES,
            icon = Icons.Default.Storage,
            route = "tool_storage_analyzer",
            keywords = listOf("disk", "space", "memory")
        ),
        ToolItem(
            id = "recent_files_tool",
            name = "Recent Files",
            description = "Quickly access recently created PDF and image files",
            category = ToolCategory.FILES,
            icon = Icons.Default.AccessTime,
            route = "tool_file_manager",
            keywords = listOf("history", "exports")
        ),
        ToolItem(
            id = "cache_cleaner_tool",
            name = "Cache Cleaner",
            description = "Free up temporary storage and image buffers",
            category = ToolCategory.FILES,
            icon = Icons.Default.CleaningServices,
            route = "settings",
            keywords = listOf("clean", "cache", "free up")
        ),

        // 6. Finance Tools
        ToolItem(
            id = "expense_tracker",
            name = "Expense Tracker",
            description = "Log daily income & spending with category analytics",
            category = ToolCategory.FINANCE,
            icon = Icons.Default.AccountBalanceWallet,
            route = "tool_expense",
            keywords = listOf("money", "budget", "finance", "spending"),
            isQuickTool = true
        ),
        ToolItem(
            id = "expense_add_tool",
            name = "Add Transaction",
            description = "Quickly record a new income or expense entry",
            category = ToolCategory.FINANCE,
            icon = Icons.Default.AddCircle,
            route = "tool_expense",
            keywords = listOf("new expense", "log")
        ),
        ToolItem(
            id = "expense_analytics_tool",
            name = "Expense Statistics",
            description = "Review visual category breakdown and balance",
            category = ToolCategory.FINANCE,
            icon = Icons.Default.PieChart,
            route = "tool_expense",
            keywords = listOf("chart", "summary", "reports")
        ),

        // 7. Calculator & Converter Tools
        ToolItem(
            id = "calc_basic",
            name = "Basic Calculator",
            description = "Smart arithmetic calculator with history tape",
            category = ToolCategory.CALCULATOR,
            icon = Icons.Default.Calculate,
            route = "tool_calculator",
            keywords = listOf("math", "add", "multiply"),
            isQuickTool = true
        ),
        ToolItem(
            id = "calc_percent",
            name = "Percentage Calculator",
            description = "Calculate percentages, discounts, tips & changes",
            category = ToolCategory.CALCULATOR,
            icon = Icons.Default.Percent,
            route = "tool_percentage",
            keywords = listOf("discount", "tip", "tax")
        ),
        ToolItem(
            id = "calc_unit",
            name = "Unit Converter",
            description = "Convert Length, Weight, Temperature, Area & Volume",
            category = ToolCategory.CALCULATOR,
            icon = Icons.Default.SwapHoriz,
            route = "tool_unit_converter",
            keywords = listOf("convert", "units", "km", "miles", "kg", "lbs")
        ),
        ToolItem(
            id = "calc_age",
            name = "Age Calculator",
            description = "Calculate exact age, days lived & next birthday",
            category = ToolCategory.CALCULATOR,
            icon = Icons.Default.Cake,
            route = "tool_age_date?mode=AGE",
            keywords = listOf("birthday", "years", "days")
        ),
        ToolItem(
            id = "calc_date",
            name = "Date Calculator",
            description = "Calculate difference between dates or add/subtract days",
            category = ToolCategory.CALCULATOR,
            icon = Icons.Default.DateRange,
            route = "tool_age_date?mode=DATE",
            keywords = listOf("calendar", "days between")
        ),

        // 8. Productivity Tools
        ToolItem(
            id = "prod_notes",
            name = "Notes",
            description = "Quick local notes with pinning, colors & search",
            category = ToolCategory.PRODUCTIVITY,
            icon = Icons.Default.EditNote,
            route = "tool_notes",
            keywords = listOf("memo", "notepad", "write"),
            isQuickTool = true
        ),
        ToolItem(
            id = "prod_stopwatch",
            name = "Stopwatch",
            description = "High precision stopwatch with lap time tracking",
            category = ToolCategory.PRODUCTIVITY,
            icon = Icons.Default.Timer,
            route = "tool_stopwatch",
            keywords = listOf("chronometer", "laps", "time")
        ),
        ToolItem(
            id = "prod_timer",
            name = "Timer",
            description = "Countdown timer with progress ring & alert",
            category = ToolCategory.PRODUCTIVITY,
            icon = Icons.Default.HourglassEmpty,
            route = "tool_timer",
            keywords = listOf("countdown", "alarm")
        ),

        // 9. Security Tools
        ToolItem(
            id = "sec_pwd_gen",
            name = "Password Generator",
            description = "Create strong, secure passwords with custom rules",
            category = ToolCategory.SECURITY,
            icon = Icons.Default.Key,
            route = "tool_password_gen",
            keywords = listOf("password", "strong", "secure", "pin"),
            isQuickTool = true
        ),
        ToolItem(
            id = "sec_privacy",
            name = "Privacy Guarantee",
            description = "Verify zero-tracking and 100% on-device processing",
            category = ToolCategory.SECURITY,
            icon = Icons.Default.Security,
            route = "settings",
            keywords = listOf("privacy", "offline", "guarantee")
        ),

        // 10. Other Utilities
        ToolItem(
            id = "other_storage_details",
            name = "Storage Breakdown",
            description = "Inspect storage breakdown across categories",
            category = ToolCategory.OTHER,
            icon = Icons.Default.Storage,
            route = "tool_storage_analyzer",
            keywords = listOf("device", "memory")
        ),
        ToolItem(
            id = "other_activity_log",
            name = "Activity Log",
            description = "Inspect past utility activity, scans and conversions",
            category = ToolCategory.OTHER,
            icon = Icons.Default.History,
            route = "history",
            keywords = listOf("history", "recent")
        ),
        ToolItem(
            id = "other_settings_pref",
            name = "Preferences",
            description = "Change app theme, image compression quality and options",
            category = ToolCategory.OTHER,
            icon = Icons.Default.Tune,
            route = "settings",
            keywords = listOf("theme", "settings", "options")
        )
    )

    fun getByCategory(category: ToolCategory): List<ToolItem> {
        return allTools.filter { it.category == category }
    }

    fun getCategoryCount(category: ToolCategory): Int {
        return allTools.count { it.category == category }
    }

    fun getById(id: String): ToolItem? = allTools.firstOrNull { it.id == id }
}
