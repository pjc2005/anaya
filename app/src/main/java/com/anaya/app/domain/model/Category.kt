package com.anaya.app.domain.model

data class Category(
    val id: Long = 0,
    val name: String,
    val icon: String? = null,
    val type: CategoryType,
    val parentId: Long? = null,
    val sortOrder: Int = 0
)

enum class CategoryType {
    INCOME,
    EXPENSE
}
