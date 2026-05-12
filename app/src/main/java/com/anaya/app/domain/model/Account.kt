package com.anaya.app.domain.model

data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType = AccountType.CASH,
    val initialBalance: Long = 0,
    val color: Int? = null,
    val sortOrder: Int = 0,
    val archived: Boolean = false
)

enum class AccountType {
    CASH,
    BANK,
    CREDIT_CARD,
    ALIPAY,
    WECHAT
}
