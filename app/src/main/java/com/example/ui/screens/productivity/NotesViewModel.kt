package com.example.ui.screens.productivity

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.UtilityHubApp
import com.example.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as UtilityHubApp).repository

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val notes: StateFlow<List<NoteEntity>> = combine(
        repository.getAllNotes(),
        _searchQuery
    ) { allNotes, query ->
        if (query.isBlank()) {
            allNotes
        } else {
            allNotes.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.content.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun saveNote(id: Long, title: String, content: String, isPinned: Boolean, colorTag: Int) {
        viewModelScope.launch {
            val entity = NoteEntity(
                id = id,
                title = title,
                content = content,
                isPinned = isPinned,
                colorTag = colorTag,
                updatedAt = System.currentTimeMillis()
            )
            if (id == 0L) {
                repository.insertNote(entity)
            } else {
                repository.updateNote(entity)
            }
            repository.addHistory("prod_notes", "Note Updated", title.take(40))
        }
    }

    fun togglePin(note: NoteEntity) {
        viewModelScope.launch {
            repository.updateNote(note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }
}
