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
    PENDING_ACCEPTANCE(
        value = "PENDING_ACCEPTANCE",
        displayName = "Pending Acceptance",
        description = "New order awaiting restaurant action"
    ),
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
    ),
    OUT_FOR_DELIVERY(
        value = "OUT_FOR_DELIVERY",
        displayName = "Out for Delivery",
        description = "Order is on its way with the driver"
    ),
    COMPLETED(
        value = "COMPLETED",
        displayName = "Completed",
        description = "Delivered — order complete"
    ),
    CANCELLED(
        value = "CANCELLED",
        displayName = "Cancelled",
        description = "Order was cancelled"
    );

    companion object {
        /**
         * Get status from string value.
         *
         * Falls back to PENDING_ACCEPTANCE for anything unrecognized —
         * that's the real starting status every checkout-created order
         * has (see FirestoreOrder.status default), so an unexpected value
         * lands on "new order awaiting action" rather than falsely
         * claiming payment (previously this fell back to PAID, which made
         * a brand-new, unpaid Cash on Delivery order display as "Paid").
         */
        fun fromString(value: String): RestaurantOrderStatus {
            return entries.find { it.value == value } ?: PENDING_ACCEPTANCE
        }

        /**
         * Get statuses visible to restaurant.
         */
        fun restaurantVisibleStatuses(): List<RestaurantOrderStatus> {
            return listOf(PENDING_ACCEPTANCE, PAID, ACCEPTED, REJECTED, PREPARING, READY_FOR_PICKUP, OUT_FOR_DELIVERY, COMPLETED, CANCELLED)
        }

        /**
         * Get actionable statuses for restaurant.
         */
        fun restaurantActionableStatuses(): List<RestaurantOrderStatus> {
            return listOf(PENDING_ACCEPTANCE, PAID)
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
