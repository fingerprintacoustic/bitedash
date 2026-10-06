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
    val driverTip: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val driverId: String? = null,
    val driverName: String? = null,
    val isSettled: Boolean = false,
    val firestoreOrderId: String? = null,
    // The account that placed the order. The local cache used to have no owner, so on a
    // shared phone the next account to sign in saw the previous one's orders.
    val userId: String = "",
    // Mirrors FirestoreOrder.paymentStatus (CASH_ON_DELIVERY, AWAITING_MANUAL_CONFIRMATION,
    // PAID, FAILED, ...), so Tracking can tell "checking your payment" apart from
    // "waiting for the restaurant", and a rejected order knows whether it was paid.
    val paymentStatus: String = ""
)
