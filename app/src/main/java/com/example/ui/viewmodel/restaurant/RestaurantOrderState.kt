package com.example.ui.viewmodel.restaurant

import com.google.firebase.Timestamp

/**
 * Restaurant order status values.
 */
enum class RestaurantOrderStatus(
    val value: String,
    val displayName: String,
    val description: String
) {
    PENDING_PAYMENT(
        value = "PENDING_PAYMENT",
        displayName = "Pending Payment",
        description = "Waiting for payment confirmation"
    ),
    PAID(
        value = "PAID",
        displayName = "Paid",
        description = "Payment confirmed, awaiting restaurant action"
    ),
    ACCEPTED(
        value = "ACCEPTED",
        displayName = "Accepted",
        description = "Order accepted by restaurant"
    ),
    REJECTED(
        value = "REJECTED",
        displayName = "Rejected",
        description = "Order rejected by restaurant"
    ),
    PREPARING(
        value = "PREPARING",
        displayName = "Preparing",
        description = "Order is being prepared"
    ),
    READY_FOR_PICKUP(
        value = "READY_FOR_PICKUP",
        displayName = "Ready for Pickup",
        description = "Order ready for driver pickup"
    );

    companion object {
        /**
         * Get status from string value.
         */
        fun fromString(value: String): RestaurantOrderStatus {
            return entries.find { it.value == value } ?: PAID
        }
        
        /**
         * Get statuses visible to restaurant.
         */
        fun restaurantVisibleStatuses(): List<RestaurantOrderStatus> {
            return listOf(PAID, ACCEPTED, REJECTED, PREPARING, READY_FOR_PICKUP)
        }
        
        /**
         * Get actionable statuses for restaurant.
         */
        fun restaurantActionableStatuses(): List<RestaurantOrderStatus> {
            return listOf(PAID)
        }
    }
}

/**
 * Restaurant order item model.
 */
data class RestaurantOrderItem(
    val itemId: String = "",
    val itemName: String = "",
    val quantity: Int = 0,
    val price: Double = 0.0,
    val notes: String = ""
)

/**
 * Restaurant order model for display.
 */
data class RestaurantOrder(
    val orderId: String = "",
    val userId: String = "",
    val restaurantId: String = "",
    val restaurantName: String = "",
    val customerName: String = "",
    val customerAddress: String = "",
    val customerPhone: String = "",
    val items: List<RestaurantOrderItem> = emptyList(),
    val itemsSummary: String = "",
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val totalCost: Double = 0.0,
    val status: RestaurantOrderStatus = RestaurantOrderStatus.PAID,
    val paymentMethod: String = "",
    val paymentRef: String = "",
    val paymentStatus: String = "PAID",
    val createdAt: Timestamp? = null,
    val acceptedAt: Timestamp? = null,
    val readyAt: Timestamp? = null
)

/**
 * UI state for restaurant order management.
 */
data class RestaurantOrderUiState(
    val orders: List<RestaurantOrder> = emptyList(),
    val selectedOrder: RestaurantOrder? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val actionInProgress: String? = null
)
