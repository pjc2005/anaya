package com.anaya.app.domain.model

data class Transaction(
    val id: Long = 0,
    val amount: Long,
    val type: TransactionType,
    val categoryId: Long,
    val accountId: Long,
    val targetAccountId: Long? = null,
    val note: String? = null,
    val date: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER
}
