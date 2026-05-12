package com.anaya.app.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anaya.app.data.export.DataExportImport
import com.anaya.app.data.export.ExportFormat
import com.anaya.app.data.export.ExportResult
import com.anaya.app.data.export.ImportResult
import com.anaya.app.data.local.dao.AccountDao
import com.anaya.app.data.local.dao.CategoryDao
import com.anaya.app.data.local.dao.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExportImportUiState(
    val selectedExportFormat: ExportFormat = ExportFormat.JSON,
    val selectedImportFormat: ExportFormat = ExportFormat.JSON,
    val transactionCount: Int = -1,
    val accountCount: Int = 0,
    val categoryCount: Int = 0,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val exportResult: ExportResult? = null,
    val importResult: ImportResult? = null
)

@HiltViewModel
class ExportImportViewModel @Inject constructor(
    private val dataIO: DataExportImport,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao
) : ViewModel() {

    private val _state = MutableStateFlow(ExportImportUiState())
    val uiState: StateFlow<ExportImportUiState> = _state.asStateFlow()

    init {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            val txs = transactionDao.getAllTransactionsSync()
            val accts = accountDao.getAllAccountsSync()
            val cats = categoryDao.getAllCategoriesSync()
            _state.update {
                it.copy(
                    transactionCount = txs.size,
                    accountCount = accts.size,
                    categoryCount = cats.size
                )
            }
        }
    }

    fun selectExportFormat(format: ExportFormat) {
        _state.update { it.copy(selectedExportFormat = format) }
    }

    fun selectImportFormat(format: ExportFormat) {
        _state.update { it.copy(selectedImportFormat = format) }
    }

    fun doExport(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, exportResult = null) }
            val result = dataIO.export(uri, _state.value.selectedExportFormat)
            _state.update { it.copy(isExporting = false, exportResult = result) }
        }
    }

    fun doImport(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, importResult = null) }
            val result = dataIO.import(uri, _state.value.selectedImportFormat)
            _state.update { it.copy(isImporting = false, importResult = result) }
            // 刷新统计
            if (result.success) loadStats()
        }
    }

    fun clearExportResult() {
        _state.update { it.copy(exportResult = null) }
    }

    fun clearImportResult() {
        _state.update { it.copy(importResult = null) }
    }
}
