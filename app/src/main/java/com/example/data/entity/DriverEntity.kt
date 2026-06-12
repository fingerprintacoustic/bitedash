package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drivers")
data class DriverEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String,
    val vehicle: String, // e.g. "Bicycle", "Motorbike", "Car"
    val isAvailable: Boolean = true
)
