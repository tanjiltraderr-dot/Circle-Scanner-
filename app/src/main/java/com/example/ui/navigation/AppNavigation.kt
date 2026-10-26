package com.example.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.components.AppBottomBar
import com.example.ui.screens.calculator.*
import com.example.ui.screens.finance.ExpenseTrackerScreen
import com.example.ui.screens.history.HistoryScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.image.ImageToolsScreen
import com.example.ui.screens.pdf.PdfToolsScreen
import com.example.ui.screens.pro.ProScreen
import com.example.ui.screens.productivity.*
import com.example.ui.screens.qr.QrGeneratorScreen
import com.example.ui.screens.scanner.*
import com.example.docscan.ui.*
import com.example.ui.screens.security.PasswordGeneratorScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.storage.FileManagerScreen
import com.example.ui.screens.storage.StorageAnalyzerScreen
import com.example.ui.screens.tools.CategoryDetailsScreen
import com.example.ui.screens.tools.ToolsScreen

import android.content.Context
import androidx.compose.ui.platform.LocalContext

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    fun getPreferredScannerRoute(): String {
        val prefs = context.getSharedPreferences("utility_hub_prefs", Context.MODE_PRIVATE)
        val defaultMode = prefs.getString("default_scanner_mode", "QR & Barcode") ?: "QR & Barcode"
        val modeParam = when (defaultMode) {
            "Document / PDF" -> "DOCUMENT_PDF"
            "ID / Card" -> "ID_CARD"
            "Image Scan" -> "IMAGE_SCAN"
            else -> "QR_BARCODE"
        }
        return Screen.Scanner.createRoute(modeParam)
    }

    val isBottomBarVisible = currentRoute == null ||
            currentRoute == Screen.Home.route ||
            currentRoute == Screen.Tools.route ||
            currentRoute.startsWith("category/") ||
            currentRoute == Screen.History.route ||
            currentRoute == Screen.FileManager.route ||
            currentRoute == Screen.Settings.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (isBottomBarVisible) {
                AppBottomBar(
                    currentRoute = if (currentRoute?.startsWith("category/") == true) Screen.Tools.route else currentRoute,
                    onNavigate = { route ->
                        if (route == Screen.Home.route) {
                            val popped = navController.popBackStack(Screen.Home.route, inclusive = false)
                            if (!popped) {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        inclusive = false
                                    }
                                    launchSingleTop = true
                                }
                            }
                        } else {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onScannerClick = {
                        navController.navigate(getPreferredScannerRoute())
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            // 1. Home Dashboard
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToScanner = {
                        navController.navigate(getPreferredScannerRoute())
                    },
                    onNavigateToCategory = { categoryId ->
                        navController.navigate(Screen.CategoryDetails.createRoute(categoryId))
                    },
                    onNavigateToTool = { route ->
                        navController.navigate(route)
                    },
                    onNavigateToTools = {
                        navController.navigate(Screen.Tools.route)
                    },
                    onNavigateToPro = {
                        navController.navigate(Screen.Pro.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onNavigateToHistory = {
                        navController.navigate(Screen.History.route)
                    }
                )
            }

            // 2. Tools Main Page (Categories Only)
            composable(Screen.Tools.route) {
                ToolsScreen(
                    onNavigateToCategory = { categoryId ->
                        navController.navigate(Screen.CategoryDetails.createRoute(categoryId))
                    },
                    onNavigateToTool = { route ->
                        navController.navigate(route)
                    }
                )
            }

            // 3. Category Details Page (3-column grid)
            composable(
                route = Screen.CategoryDetails.route,
                arguments = listOf(navArgument("categoryId") { type = NavType.StringType })
            ) { backStackEntry ->
                val categoryId = backStackEntry.arguments?.getString("categoryId") ?: "scanner"
                CategoryDetailsScreen(
                    categoryId = categoryId,
                    onBack = { navController.popBackStack() },
                    onNavigateToRoute = { route -> navController.navigate(route) }
                )
            }

            // 4. Dedicated Full-Screen Scanner
            composable(
                route = Screen.Scanner.route,
                arguments = listOf(navArgument("mode") { defaultValue = "QR_BARCODE" })
            ) { backStackEntry ->
                val mode = backStackEntry.arguments?.getString("mode") ?: "QR_BARCODE"
                FullScannerScreen(
                    initialMode = mode,
                    onBack = { navController.popBackStack() },
                    onNavigateToResult = { content, format ->
                        navController.navigate(Screen.ScanResult.createRoute(content, format))
                    },
                    onNavigateToDocumentViewer = { filePath, title ->
                        navController.navigate(Screen.DocumentViewer.createRoute(filePath, title))
                    },
                    onNavigateToDocumentViewerWithId = { filePath, title, docId ->
                        navController.navigate(Screen.DocumentViewer.createRoute(filePath, title, docId))
                    },
                    onNavigateToPastScans = {
                        navController.navigate(Screen.PastScans.route)
                    },
                    onNavigateToCategory = { catId ->
                        navController.navigate(Screen.CategoryDetails.createRoute(catId))
                    }
                )
            }

            // Dedicated Past Scans (Room Schema List UI)
            composable(Screen.PastScans.route) {
                PastScansScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToScanner = {
                        navController.navigate(Screen.Scanner.createRoute("DOCUMENT_PDF"))
                    },
                    onOpenDocumentViewer = { filePath, title, docId ->
                        navController.navigate(Screen.DocumentViewer.createRoute(filePath, title, docId))
                    }
                )
            }

            // DocScan Main Screen (Ported from Nykz/cap-document-scanner)
            composable(Screen.DocScan.route) {
                DocScanMainScreen(
                    onBack = { navController.popBackStack() },
                    onStartScan = {
                        navController.navigate(Screen.Scanner.createRoute("DOCUMENT_PDF"))
                    },
                    onOpenDocumentDetail = { docId ->
                        navController.navigate(Screen.DocScanDetail.createRoute(docId))
                    }
                )
            }

            // DocScan Document Detail Screen
            composable(
                route = Screen.DocScanDetail.route,
                arguments = listOf(
                    navArgument("docId") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val docId = backStackEntry.arguments?.getString("docId") ?: ""
                DocScanDetailScreen(
                    documentId = docId,
                    onBack = { navController.popBackStack() },
                    onOpenPdfViewer = { filePath, title ->
                        navController.navigate(Screen.DocumentViewer.createRoute(filePath, title))
                    }
                )
            }

            // 5. Scan Result Screen
            composable(
                route = Screen.ScanResult.route,
                arguments = listOf(
                    navArgument("content") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("format") {
                        type = NavType.StringType
                        defaultValue = "QR_CODE"
                    }
                )
            ) { backStackEntry ->
                val content = backStackEntry.arguments?.getString("content") ?: ""
                val format = backStackEntry.arguments?.getString("format") ?: "QR_CODE"
                ScanResultScreen(
                    rawContent = content,
                    format = format,
                    onBack = { navController.popBackStack() },
                    onScanAgain = { navController.popBackStack() }
                )
            }

            // 5b. Scanned Document Viewer (View Page for Document / Image scans)
            composable(
                route = Screen.DocumentViewer.route,
                arguments = listOf(
                    navArgument("filePath") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("title") {
                        type = NavType.StringType
                        defaultValue = "Scanned Document"
                    },
                    navArgument("docId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val filePath = backStackEntry.arguments?.getString("filePath") ?: ""
                val title = backStackEntry.arguments?.getString("title") ?: "Scanned Document"
                val docId = backStackEntry.arguments?.getLong("docId") ?: -1L
                ScannedDocumentViewScreen(
                    filePath = filePath,
                    initialTitle = title,
                    docId = docId,
                    onBack = { navController.popBackStack() },
                    onNavigateToTool = { route -> navController.navigate(route) }
                )
            }

            // History
            composable(Screen.History.route) {
                HistoryScreen(
                    onNavigateToTool = { route -> navController.navigate(route) }
                )
            }

            // Pro
            composable(Screen.Pro.route) {
                ProScreen()
            }

            // Settings
            composable(Screen.Settings.route) {
                SettingsScreen()
            }

            // Scanner Tools (All Unified under CamScanner Interface)
            composable(Screen.QrScanner.route) {
                FullScannerScreen(
                    initialMode = "QR_BARCODE",
                    onBack = { navController.popBackStack() },
                    onNavigateToResult = { content, format ->
                        navController.navigate(Screen.ScanResult.createRoute(content, format))
                    }
                )
            }

            composable(Screen.BarcodeScanner.route) {
                FullScannerScreen(
                    initialMode = "QR_BARCODE",
                    onBack = { navController.popBackStack() },
                    onNavigateToResult = { content, format ->
                        navController.navigate(Screen.ScanResult.createRoute(content, format))
                    }
                )
            }

            composable(Screen.DocumentScanner.route) {
                FullScannerScreen(
                    initialMode = "SCANNER",
                    onBack = { navController.popBackStack() },
                    onNavigateToResult = { content, format ->
                        navController.navigate(Screen.ScanResult.createRoute(content, format))
                    }
                )
            }

            // PDF Tools
            composable(
                route = Screen.PdfTools.route,
                arguments = listOf(navArgument("tab") {
                    type = NavType.IntType
                    defaultValue = 0
                })
            ) { backStackEntry ->
                val tab = backStackEntry.arguments?.getInt("tab") ?: 0
                PdfToolsScreen(
                    initialTab = tab,
                    onBack = { navController.popBackStack() }
                )
            }

            // Image Tools
            composable(
                route = Screen.ImageTools.route,
                arguments = listOf(navArgument("tab") {
                    type = NavType.IntType
                    defaultValue = 0
                })
            ) { backStackEntry ->
                val tab = backStackEntry.arguments?.getInt("tab") ?: 0
                ImageToolsScreen(
                    initialTab = tab,
                    onBack = { navController.popBackStack() }
                )
            }

            // QR Generator
            composable(
                route = Screen.QrGenerator.route,
                arguments = listOf(navArgument("type") {
                    type = NavType.StringType
                    defaultValue = "TEXT"
                })
            ) { backStackEntry ->
                val type = backStackEntry.arguments?.getString("type") ?: "TEXT"
                QrGeneratorScreen(
                    initialType = type,
                    onBack = { navController.popBackStack() }
                )
            }

            // Finance
            composable(Screen.ExpenseTracker.route) {
                ExpenseTrackerScreen(onBack = { navController.popBackStack() })
            }

            // Calculators
            composable(Screen.Calculator.route) {
                CalculatorScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.UnitConverter.route) {
                UnitConverterScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.PercentageCalculator.route) {
                PercentageCalculatorScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Screen.AgeDateCalculator.route,
                arguments = listOf(navArgument("mode") {
                    type = NavType.StringType
                    defaultValue = "AGE"
                })
            ) { backStackEntry ->
                val mode = backStackEntry.arguments?.getString("mode") ?: "AGE"
                AgeDateCalculatorScreen(
                    initialMode = mode,
                    onBack = { navController.popBackStack() }
                )
            }

            // Productivity
            composable(Screen.Notes.route) {
                NotesScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.Stopwatch.route) {
                StopwatchScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.Timer.route) {
                TimerScreen(onBack = { navController.popBackStack() })
            }

            // Security
            composable(Screen.PasswordGenerator.route) {
                PasswordGeneratorScreen(onBack = { navController.popBackStack() })
            }

            // Storage
            composable(Screen.StorageAnalyzer.route) {
                StorageAnalyzerScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.FileManager.route) {
                FileManagerScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
