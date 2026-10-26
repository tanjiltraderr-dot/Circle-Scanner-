package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ExpenseEntity
import com.example.data.local.entity.FavoriteEntity
import com.example.data.local.entity.HistoryEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.RecentToolEntity
import com.example.data.local.entity.ScannedDocumentEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "utility_hub_preferences")

class AppRepository(private val context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val expenseDao = database.expenseDao()
    private val noteDao = database.noteDao()
    private val historyDao = database.historyDao()
    private val utilityDao = database.utilityDao()
    private val scannedDocumentDao = database.scannedDocumentDao()

    private val prefs: SharedPreferences = context.getSharedPreferences("utility_hub_prefs", Context.MODE_PRIVATE)
    private val themeKey = stringPreferencesKey("theme_mode")
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "LIGHT") ?: "LIGHT")
    val themeMode = _themeMode.asStateFlow()

    private val _imageQuality = MutableStateFlow(prefs.getInt("image_quality", 80))
    val imageQuality = _imageQuality.asStateFlow()

    private val _historyEnabled = MutableStateFlow(prefs.getBoolean("history_enabled", true))
    val historyEnabled = _historyEnabled.asStateFlow()

    private val _isProUnlocked = MutableStateFlow(prefs.getBoolean("is_pro_unlocked", false))
    val isProUnlocked = _isProUnlocked.asStateFlow()

    init {
        // Read stored theme from DataStore Preferences
        repositoryScope.launch {
            try {
                val storedTheme = context.dataStore.data.first()[themeKey]
                if (storedTheme != null) {
                    _themeMode.value = storedTheme
                }
            } catch (e: Exception) {
                // Fall back to SharedPreferences if any
            }

            try {
                if (!prefs.getBoolean("initial_seed_completed", false)) {
                    // Seed reference favorites
                    utilityDao.addFavorite(FavoriteEntity("tool_qr_scanner"))
                    utilityDao.addFavorite(FavoriteEntity("tool_pdf_tools"))
                    utilityDao.addFavorite(FavoriteEntity("tool_image_tools"))
                    utilityDao.addFavorite(FavoriteEntity("tool_file_manager"))

                    // Seed initial history matching reference screenshot
                    val now = System.currentTimeMillis()
                    historyDao.insertHistory(
                        HistoryEntity(
                            toolId = "tool_qr_scanner",
                            title = "QR Code Scanned",
                            details = "https://example.com",
                            timestamp = now - 2 * 60 * 1000
                        )
                    )
                    historyDao.insertHistory(
                        HistoryEntity(
                            toolId = "tool_pdf_tools",
                            title = "PDF Created",
                            details = "Document_2025-04-22.pdf",
                            timestamp = now - 15 * 60 * 1000
                        )
                    )
                    historyDao.insertHistory(
                        HistoryEntity(
                            toolId = "tool_image_tools",
                            title = "Image Compressed",
                            details = "photo_2025.jpg (2.4 MB → 350 KB)",
                            timestamp = now - 32 * 60 * 1000
                        )
                    )

                    // Seed recent operation for Continue Where You Left Off
                    utilityDao.logToolUsage(
                        RecentToolEntity(
                            toolId = "tool_qr_scanner",
                            lastUsedTimestamp = now - 2 * 60 * 1000
                        )
                    )

                    prefs.edit().putBoolean("initial_seed_completed", true).apply()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
        _themeMode.value = mode
        repositoryScope.launch {
            try {
                context.dataStore.edit { preferences ->
                    preferences[themeKey] = mode
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setImageQuality(quality: Int) {
        prefs.edit().putInt("image_quality", quality).apply()
        _imageQuality.value = quality
    }

    fun setHistoryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("history_enabled", enabled).apply()
        _historyEnabled.value = enabled
    }

    fun setProUnlocked(unlocked: Boolean) {
        prefs.edit().putBoolean("is_pro_unlocked", unlocked).apply()
        _isProUnlocked.value = unlocked
    }

    // Expense
    fun getAllExpenses(): Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()
    suspend fun insertExpense(expense: ExpenseEntity) = expenseDao.insertExpense(expense)
    suspend fun updateExpense(expense: ExpenseEntity) = expenseDao.updateExpense(expense)
    suspend fun deleteExpense(expense: ExpenseEntity) = expenseDao.deleteExpense(expense)

    // Notes
    fun getAllNotes(): Flow<List<NoteEntity>> = noteDao.getAllNotes()
    suspend fun insertNote(note: NoteEntity) = noteDao.insertNote(note)
    suspend fun updateNote(note: NoteEntity) = noteDao.updateNote(note)
    suspend fun deleteNote(note: NoteEntity) = noteDao.deleteNote(note)

    // History
    fun getAllHistory(): Flow<List<HistoryEntity>> = historyDao.getAllHistory()
    suspend fun addHistory(toolId: String, title: String, details: String) {
        if (_historyEnabled.value) {
            historyDao.insertHistory(
                HistoryEntity(toolId = toolId, title = title, details = details)
            )
        }
    }
    suspend fun deleteHistory(id: Long) = historyDao.deleteById(id)
    suspend fun clearHistory() = historyDao.clearHistory()

    // Favorites & Recent
    fun getFavoriteToolIds(): Flow<List<String>> = utilityDao.getFavoriteToolIds()
    suspend fun toggleFavorite(toolId: String, isCurrentlyFavorite: Boolean) {
        if (isCurrentlyFavorite) {
            utilityDao.removeFavorite(toolId)
        } else {
            utilityDao.addFavorite(FavoriteEntity(toolId))
        }
    }

    fun getRecentTools(): Flow<List<RecentToolEntity>> = utilityDao.getRecentTools()
    suspend fun recordToolUsage(toolId: String) {
        utilityDao.logToolUsage(RecentToolEntity(toolId = toolId, lastUsedTimestamp = System.currentTimeMillis()))
    }

    // Scanned Documents (Room)
    fun getAllScannedDocuments(): Flow<List<ScannedDocumentEntity>> = scannedDocumentDao.getAllDocuments()
    fun searchScannedDocuments(query: String): Flow<List<ScannedDocumentEntity>> = scannedDocumentDao.searchDocuments(query)
    suspend fun getScannedDocumentById(id: Long): ScannedDocumentEntity? = scannedDocumentDao.getDocumentById(id)
    suspend fun insertScannedDocument(doc: ScannedDocumentEntity): Long = scannedDocumentDao.insertDocument(doc)
    suspend fun updateScannedDocument(doc: ScannedDocumentEntity) = scannedDocumentDao.updateDocument(doc)
    suspend fun deleteScannedDocument(doc: ScannedDocumentEntity) = scannedDocumentDao.deleteDocument(doc)
    suspend fun deleteScannedDocumentById(id: Long) = scannedDocumentDao.deleteDocumentById(id)
}
