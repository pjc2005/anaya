package com.anaya.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val icon: String? = null,
    val color: Int? = null,
    val type: String,
    val parentId: Long? = null,
    val sortOrder: Int = 0
)
