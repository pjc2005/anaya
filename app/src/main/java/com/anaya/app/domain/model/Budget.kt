package com.anaya.app.domain.model

data class Budget(
    val id: Long = 0,
    val categoryId: Long? = null,
    val amount: Long,
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val startDate: Long,
    val alertThreshold: Int = 80
)

enum class BudgetPeriod {
    WEEKLY,
    MONTHLY,
    YEARLY
}
