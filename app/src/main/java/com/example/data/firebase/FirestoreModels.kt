package com.example.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Firestore document models for BiteDash Firebase integration.
 * These represent the data structure stored in Firebase Firestore.
 */

// Restaurant document stored in Firestore
data class FirestoreRestaurant(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val rating: Double = 0.0,
    val deliveryTime: String = "",
    val deliveryFee: Double = 0.0,
    val category: String = "",
    val location: String = "",
    val imageKeyword: String = "",
    val displayOrder: Int = 0,
    val ownerUsername: String = "owner",
    val ownerPassword: String = "password",
    val menuItems: List<FirestoreMenuItem> = emptyList(),
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    val isActive: Boolean = true
)

// Menu item embedded in restaurant document
data class FirestoreMenuItem(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val category: String = ""
)

// Driver document stored in Firestore
data class FirestoreDriver(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val vehicle: String = "",
    val isActive: Boolean = true,
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    // Payout info
    val ecoCashNumber: String = "",
    val oneMoneyNumber: String = "",
    val totalDeliveries: Int = 0,
    val totalEarnings: Double = 0.0,
    val pendingPayout: Double = 0.0
)

// Order document stored in Firestore
data class FirestoreOrder(
    @DocumentId
    val id: String = "",
    val restaurantId: String = "",
    val restaurantName: String = "",
    val customerName: String = "",
    val customerAddress: String = "",
    val itemsSummary: String = "",
    val totalCost: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val driverTip: Double = 0.0,
    val status: String = "PENDING_ACCEPTANCE", // PENDING_ACCEPTANCE, PREPARING, READY_FOR_PICKUP, OUT_FOR_DELIVERY, COMPLETED, CANCELLED
    val driverId: String? = null,
    val driverName: String? = null,
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null,
    // Payout tracking
    val isSettled: Boolean = false,
    val restaurantPayoutAmount: Double = 0.0,
    val driverPayoutAmount: Double = 0.0,
    val platformFee: Double = 0.0,
    // Payment info
    val paymentMethod: String = "", // ECO_CASH, ONE_MONEY, INNBUCKS, CASH_ON_DELIVERY
    val paymentRef: String = ""
)

// Transaction record for audit trail
data class FirestoreTransaction(
    @DocumentId
    val id: String = "",
    val orderId: String = "",
    val type: String = "", // PAYMENT_RECEIVED, RESTAURANT_PAYOUT, DRIVER_PAYOUT, PLATFORM_FEE
    val amount: Double = 0.0,
    val currency: String = "USD",
    val recipientType: String = "", // RESTAURANT, DRIVER, PLATFORM
    val recipientId: String = "",
    val status: String = "", // PENDING, PROCESSING, COMPLETED, FAILED
    val paymentChannel: String = "", // ECO_CASH, ONE_MONEY, INNBUCKS, ZIPIT, CABS_BANK
    val mobileMoneyRef: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    val completedAt: Timestamp? = null,
    val failureReason: String = ""
)

// Admin settings document
data class FirestoreAdminSettings(
    @DocumentId
    val id: String = "admin_settings",
    val payoutSchedule: String = "Weekly", // Daily, Weekly, BiWeekly, Monthly
    val platformFeePercent: Double = 10.0,
    val minimumPayoutThreshold: Double = 10.0,
    val ecoCashMerchantId: String = "",
    val oneMoneyMerchantId: String = "",
    val innBucksApiKey: String = "",
    val isPayoutAutomationEnabled: Boolean = false,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
)
