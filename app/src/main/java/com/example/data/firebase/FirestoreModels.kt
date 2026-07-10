package com.example.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Firestore document models for BiteDash Firebase integration.
 * These represent the data structure stored in Firebase Firestore.
 * 
 * Collections:
 * - users: User profiles (customers, restaurant owners, drivers, admins)
 * - restaurants: Restaurant information
 * - menu_items: Standalone menu items (for querying by category/price)
 * - carts: User shopping carts
 * - orders: Order records
 * - payments: Payment transaction records
 * - drivers: Driver profiles
 */

// ==================== USER COLLECTION ====================

data class FirestoreUser(
    @DocumentId
    val id: String = "",
    val uid: String = "", // Firebase Auth UID
    val email: String = "",
    val displayName: String = "",
    val phone: String = "",
    val address: String = "",
    val role: String = "customer", // customer, restaurant, driver, admin
    // Restaurant specific
    val restaurantName: String = "",
    // Driver specific
    val vehicleType: String = "", // Bicycle, Motorbike, Car
    // Payment info
    val ecoCashNumber: String = "",
    val oneMoneyNumber: String = "",
    // Timestamps
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null,
    // Preferences
    val isActive: Boolean = true
)

// ==================== RESTAURANT COLLECTION ====================

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
    // Owner credentials (for restaurant owner login)
    val ownerUsername: String = "owner",
    val ownerPassword: String = "password",
    val ownerUserId: String = "", // Reference to users collection
    // Menu reference
    val menuItemIds: List<String> = emptyList(),
    // Payout info
    val ecoCashNumber: String = "",
    val bankAccount: String = "",
    val bankName: String = "",
    // Metadata
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null,
    val isActive: Boolean = true
)

// ==================== MENU ITEMS COLLECTION ====================

data class FirestoreMenuItem(
    @DocumentId
    val id: String = "",
    val restaurantId: String = "",
    val name: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val category: String = "", // Mains, Sides, Drinks, Desserts, etc.
    val isAvailable: Boolean = true,
    val imageUrl: String = "",
    val preparationTime: Int = 15, // in minutes
    // Metadata
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
)

// ==================== CART COLLECTION ====================

data class FirestoreCart(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val restaurantId: String = "",
    val items: List<FirestoreCartItem> = emptyList(),
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val total: Double = 0.0,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
)

data class FirestoreCartItem(
    val menuItemId: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val quantity: Int = 1,
    val notes: String = ""
)

// ==================== ORDER COLLECTION ====================

data class FirestoreOrder(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val restaurantId: String = "",
    val restaurantName: String = "",
    val restaurantAddress: String = "",
    val restaurantPhone: String = "",
    val customerName: String = "",
    val customerAddress: String = "",
    val customerPhone: String = "",
    // Order items
    val itemsSummary: String = "",
    val items: List<FirestoreOrderItem> = emptyList(),
    // Pricing
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val driverTip: Double = 0.0,
    val totalCost: Double = 0.0,
    // Status tracking
    val status: String = "PENDING_ACCEPTANCE", // PENDING_ACCEPTANCE, PAID, ACCEPTED, REJECTED, PREPARING, READY_FOR_PICKUP, OUT_FOR_DELIVERY, COMPLETED, CANCELLED
    val statusHistory: List<OrderStatusChange> = emptyList(),
    // Driver assignment
    val driverId: String? = null,
    val driverName: String? = null,
    // Delivery status (for driver workflow)
    val deliveryStatus: String = "UNASSIGNED", // UNASSIGNED, ASSIGNED, PICKED_UP, DELIVERED
    // Payment
    val paymentMethod: String = "", // ECO_CASH, ONE_MONEY, INNBUCKS, CASH_ON_DELIVERY
    val paymentRef: String = "",
    val paymentStatus: String = "PENDING", // PENDING, PROCESSING, COMPLETED, FAILED
    // Payout tracking
    val isSettled: Boolean = false,
    val restaurantPayoutAmount: Double = 0.0,
    val driverPayoutAmount: Double = 0.0,
    val platformFee: Double = 0.0,
    // Timestamps
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null,
    val acceptedAt: Timestamp? = null,
    val assignedAt: Timestamp? = null,
    val completedAt: Timestamp? = null
)

data class FirestoreOrderItem(
    val menuItemId: String = "",
    val itemId: String = "",
    val name: String = "",
    val itemName: String = "",
    val price: Double = 0.0,
    val quantity: Int = 1,
    val notes: String = ""
)

data class OrderStatusChange(
    val status: String = "",
    val timestamp: Timestamp? = null,
    val changedBy: String = "" // userId or driverId
)

// ==================== PAYMENTS COLLECTION ====================

data class FirestorePayment(
    @DocumentId
    val id: String = "",
    val orderId: String = "",
    val userId: String = "",
    val amount: Double = 0.0,
    val currency: String = "USD",
    val method: String = "", // ECO_CASH, ONE_MONEY, INNBUCKS, CASH_ON_DELIVERY
    val status: String = "PENDING", // PENDING, PROCESSING, COMPLETED, FAILED, REFUNDED
    // Mobile money specific
    val mobileMoneyNumber: String = "",
    val transactionRef: String = "",
    val merchantRef: String = "",
    // Payout tracking
    val platformFee: Double = 0.0,
    val restaurantPayout: Double = 0.0,
    val driverPayout: Double = 0.0,
    // Timestamps
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null,
    val completedAt: Timestamp? = null,
    val failureReason: String = ""
)

// ==================== DRIVERS COLLECTION ====================

data class FirestoreDriver(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val vehicle: String = "", // Bicycle, Motorbike, Car
    val isAvailable: Boolean = true,
    val isActive: Boolean = true,
    // Payout info
    val ecoCashNumber: String = "",
    val oneMoneyNumber: String = "",
    // Stats
    val totalDeliveries: Int = 0,
    val totalEarnings: Double = 0.0,
    val pendingPayout: Double = 0.0,
    val rating: Double = 5.0,
    // Timestamps
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null,
    // Current location (for real-time tracking)
    val currentLatitude: Double = 0.0,
    val currentLongitude: Double = 0.0,
    val lastLocationUpdate: Timestamp? = null
)

// ==================== TRANSACTION AUDIT COLLECTION ====================

data class FirestoreTransaction(
    @DocumentId
    val id: String = "",
    val orderId: String = "",
    val paymentId: String = "",
    val type: String = "", // PAYMENT_RECEIVED, RESTAURANT_PAYOUT, DRIVER_PAYOUT, PLATFORM_FEE
    val amount: Double = 0.0,
    val currency: String = "USD",
    val recipientType: String = "", // RESTAURANT, DRIVER, PLATFORM
    val recipientId: String = "",
    val recipientName: String = "",
    val status: String = "", // PENDING, PROCESSING, COMPLETED, FAILED
    val paymentChannel: String = "", // ECO_CASH, ONE_MONEY, INNBUCKS, ZIPIT, CABS_BANK
    val mobileMoneyRef: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    val completedAt: Timestamp? = null,
    val failureReason: String = ""
)

// ==================== ADMIN SETTINGS ====================

data class FirestoreAdminSettings(
    @DocumentId
    val id: String = "admin_settings",
    val payoutSchedule: String = "Weekly", // Daily, Weekly, BiWeekly, Monthly
    val platformFeePercent: Double = 10.0,
    val minimumPayoutThreshold: Double = 10.0,
    val ecoCashMerchantId: String = "",
    val oneMoneyMerchantId: String = "",
    val innBucksApiKey: String = "",
    val paynowIntegrationId: String = "",
    val paynowIntegrationKey: String = "",
    val isPayoutAutomationEnabled: Boolean = false,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
)
