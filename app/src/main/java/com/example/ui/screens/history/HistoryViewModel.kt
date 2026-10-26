package com.example.ui.screens.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.UtilityHubApp
import com.example.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as UtilityHubApp).repository

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val historyList: StateFlow<List<HistoryEntity>> = combine(
        repository.getAllHistory(),
        _searchQuery
    ) { allItems, query ->
        if (query.isBlank()) {
            allItems
        } else {
            allItems.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.details.contains(query, ignoreCase = true) ||
                it.toolId.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            val item = repository.getAllHistory().firstOrNull()?.firstOrNull { it.id == id }
            if (item != null) {
                com.example.scanner.engine.ScanStorageManager.extractScanId(item.details)?.let { scanId ->
                    com.example.scanner.engine.ScanStorageManager.deleteScan(getApplication(), scanId)
                }
            }
            repository.deleteHistory(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
