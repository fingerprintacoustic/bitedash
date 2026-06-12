package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val restaurantName: String,
    val itemsSummary: String,
    val totalCost: Double,
    val paymentMethod: String,
    val paymentPhone: String,
    val status: String,
    val timestamp: Long = System.currentTimeMillis()
)
