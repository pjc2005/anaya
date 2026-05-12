package com.anaya.app.presentation.transaction.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anaya.app.domain.model.AccountType
import com.anaya.app.domain.model.Category
import com.anaya.app.domain.model.TransactionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: TransactionEditorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val categories by viewModel.filteredCategories.collectAsStateWithLifecycle()
    val accounts by viewModel.activeAccounts.collectAsStateWithLifecycle()

    var showSourcePicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.saveState) {
        if (state.saveState == SaveState.IDLE && !state.isEditing
            && state.amountDisplay != "0"
            && (state.transactionType == TransactionType.TRANSFER || state.selectedCategory != null)) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "编辑账单" else "记一笔") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = state.amountDisplay != "0" && (
                            state.transactionType == TransactionType.TRANSFER ||
                            state.selectedCategory != null
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Type Selector ──
            TypeSelector(
                selected = state.transactionType,
                onSelected = { viewModel.onTypeSelected(it) }
            )

            // ── Amount Display ──
            AmountDisplay(text = state.amountDisplay)

            // ── Number Pad ──
            NumberPad(
                onDigit = { viewModel.onDigitTap(it) },
                onDecimal = { viewModel.onDecimalTap() },
                onDelete = { viewModel.onDeleteTap() }
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // ── Source Account / 转出账户 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (state.transactionType == TransactionType.TRANSFER) "转出账户"
                    else "账户",
                    style = MaterialTheme.typography.labelLarge
                )
                TextButton(onClick = {
                    viewModel.setAccountPickerMode(AccountPickerMode.SOURCE)
                    showSourcePicker = true
                }) {
                    Text(state.selectedAccount?.name ?: "选择账户")
                    state.selectedAccount?.let {
                        Text("  ¥${it.balance / 100}")
                    }
                }
            }

            // ── Target Account / 转入账户 ──
            if (state.transactionType == TransactionType.TRANSFER) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("转入账户", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = {
                        viewModel.setAccountPickerMode(AccountPickerMode.TARGET)
                        showTargetPicker = true
                    }) {
                        Text(state.targetAccount?.name ?: "选择转入账户")
                        state.targetAccount?.let {
                            Text("  ¥${it.balance / 100}")
                        }
                    }
                }
            }

            // ── Date ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("日期", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { showDatePicker = true }) {
                    Text(java.text.SimpleDateFormat("MM月dd日 HH:mm", java.util.Locale.CHINA).format(java.util.Date(state.selectedDate)))
                }
            }

            // ── Note ──
            OutlinedTextField(
                value = state.note,
                onValueChange = { viewModel.onNoteChanged(it) },
                label = { Text("备注（可选）") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true
            )

            // ── Category (only for non-transfer) ──
            if (state.transactionType != TransactionType.TRANSFER) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "分类",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    userScrollEnabled = false
                ) {
                    items(categories, key = { it.id }) { category ->
                        CategoryItem(
                            category = category,
                            isSelected = state.selectedCategory?.id == category.id,
                            onClick = { viewModel.onCategorySelected(category) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // ── Source Account Picker Dialog ──
    if (showSourcePicker) {
        AccountPickerDialog(
            title = "选择${if (state.transactionType == TransactionType.TRANSFER) "转出" else ""}账户",
            accounts = accounts,
            onSelect = { account ->
                viewModel.onAccountSelected(account)
                showSourcePicker = false
            },
            onDismiss = { showSourcePicker = false }
        )
    }

    // ── Target Account Picker Dialog ──
    if (showTargetPicker) {
        AccountPickerDialog(
            title = "选择转入账户",
            accounts = accounts.filter { it.id != state.selectedAccount?.id },
            onSelect = { account ->
                viewModel.onAccountSelected(account)
                showTargetPicker = false
            },
            onDismiss = { showTargetPicker = false }
        )
    }

    // ── Date Picker Dialog ──
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.selectedDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.onDateSelected(it) }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ── Delete Confirmation Dialog ──
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("确认删除") },
            text = { Text("删除后无法恢复，确定要删除这条记账记录吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTransaction()
                        showDeleteDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // ── Error Snackbar ──
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
}

@Composable
private fun TypeSelector(selected: TransactionType, onSelected: (TransactionType) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TransactionType.entries.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelected(type) },
                label = {
                    Text(
                        when (type) {
                            TransactionType.EXPENSE -> "支出"
                            TransactionType.INCOME -> "收入"
                            TransactionType.TRANSFER -> "转账"
                        }
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = when (type) {
                        TransactionType.EXPENSE -> MaterialTheme.colorScheme.errorContainer
                        TransactionType.INCOME -> MaterialTheme.colorScheme.primaryContainer
                        TransactionType.TRANSFER -> MaterialTheme.colorScheme.tertiaryContainer
                    },
                    selectedLabelColor = when (type) {
                        TransactionType.EXPENSE -> MaterialTheme.colorScheme.onErrorContainer
                        TransactionType.INCOME -> MaterialTheme.colorScheme.onPrimaryContainer
                        TransactionType.TRANSFER -> MaterialTheme.colorScheme.onTertiaryContainer
                    }
                )
            )
        }
    }
}

@Composable
private fun AccountPickerDialog(
    title: String,
    accounts: List<com.anaya.app.domain.model.Account>,
    onSelect: (com.anaya.app.domain.model.Account) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                accounts.forEach { account ->
                    val icon = when (account.type) {
                        AccountType.CASH -> "💵"
                        AccountType.BANK -> "🏦"
                        AccountType.CREDIT_CARD -> "💳"
                        AccountType.ALIPAY -> "🔵"
                        AccountType.WECHAT -> "💚"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(account) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("$icon  ${account.name}", modifier = Modifier.weight(1f))
                        Text("¥${account.balance / 100}", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun AmountDisplay(text: String) {
    val displayText = if (text == "0") "0.00" else text
    Text(
        text = "¥ $displayText",
        style = MaterialTheme.typography.displaySmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 40.sp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun NumberPad(
    onDigit: (Int) -> Unit,
    onDecimal: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (row in listOf(listOf(7, 8, 9), listOf(4, 5, 6), listOf(1, 2, 3))) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { digit ->
                    NumButton(text = digit.toString(), onClick = { onDigit(digit) })
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NumButton(text = ".", onClick = onDecimal)
            NumButton(text = "0", onClick = { onDigit(0) })
            NumButton(text = "\u232B", onClick = onDelete)
        }
    }
}

@Composable
private fun RowScope.NumButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1.8f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CategoryItem(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected) Modifier.border(
                    2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)
                ) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = category.icon ?: "📄",
            fontSize = 24.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = category.name.split("/").lastOrNull() ?: category.name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}
