package com.anaya.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: String = "CASH",
    val balance: Long = 0,
    val initialBalance: Long = 0,
    val icon: String? = null,
    val color: Int? = null,
    val sortOrder: Int = 0,
    val archived: Boolean = false
)
