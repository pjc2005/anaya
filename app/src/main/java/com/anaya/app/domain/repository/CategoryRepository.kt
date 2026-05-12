package com.anaya.app.domain.repository

import com.anaya.app.domain.model.Category
import com.anaya.app.domain.model.CategoryType
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun getAllCategories(): Flow<List<Category>>

    suspend fun getCategoryById(id: Long): Category?

    fun getCategoriesByType(type: CategoryType): Flow<List<Category>>

    suspend fun insert(category: Category): Long

    suspend fun update(category: Category)

    suspend fun delete(category: Category)
}
