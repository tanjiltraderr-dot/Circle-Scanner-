package com.example.ui.screens.finance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.UtilityHubApp
import com.example.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ExpenseSummary(
    val totalBalance: Double = 0.0,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0
)

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as UtilityHubApp).repository

    private val _filterType = MutableStateFlow("ALL") // "ALL", "INCOME", "EXPENSE"
    val filterType = _filterType.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val rawExpenses = repository.getAllExpenses()

    val summary: StateFlow<ExpenseSummary> = rawExpenses.map { list ->
        val income = list.filter { it.isIncome }.sumOf { it.amount }
        val expense = list.filter { !it.isIncome }.sumOf { it.amount }
        ExpenseSummary(
            totalBalance = income - expense,
            totalIncome = income,
            totalExpense = expense
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExpenseSummary())

    val filteredExpenses: StateFlow<List<ExpenseEntity>> = combine(
        rawExpenses,
        _filterType,
        _searchQuery
    ) { list, filter, query ->
        list.filter { item ->
            val matchFilter = when (filter) {
                "INCOME" -> item.isIncome
                "EXPENSE" -> !item.isIncome
                else -> true
            }
            val matchQuery = query.isBlank() ||
                    item.title.contains(query, ignoreCase = true) ||
                    item.category.contains(query, ignoreCase = true) ||
                    item.note.contains(query, ignoreCase = true)
            matchFilter && matchQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilterType(filter: String) {
        _filterType.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addExpense(title: String, amount: Double, category: String, isIncome: Boolean, note: String) {
        viewModelScope.launch {
            repository.insertExpense(
                ExpenseEntity(
                    title = title,
                    amount = amount,
                    category = category,
                    isIncome = isIncome,
                    note = note
                )
            )
            repository.addHistory(
                "expense_tracker",
                if (isIncome) "Added Income: $title" else "Added Expense: $title",
                "Amount: $$amount ($category)"
            )
        }
    }

    fun deleteExpense(item: ExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(item)
        }
    }
}
