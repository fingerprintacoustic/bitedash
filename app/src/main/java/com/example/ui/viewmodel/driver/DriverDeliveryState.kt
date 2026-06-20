package com.example.ui.viewmodel.driver

import com.google.firebase.Timestamp

/**
 * Driver delivery status values.
 */
enum class DriverDeliveryStatus(
    val value: String,
    val displayName: String,
    val description: String
) {
    UNASSIGNED(
        value = "UNASSIGNED",
        displayName = "Unassigned",
        description = "Order awaiting driver assignment"
    ),
    ASSIGNED(
        value = "ASSIGNED",
        displayName = "Assigned",
        description = "Driver assigned, awaiting pickup"
    ),
    PICKED_UP(
        value = "PICKED_UP",
        displayName = "Picked Up",
        description = "Order picked up, en route"
    ),
    DELIVERED(
        value = "DELIVERED",
        displayName = "Delivered",
        description = "Order delivered successfully"
    );

    companion object {
        /**
         * Get status from string value.
         */
        fun fromString(value: String): DriverDeliveryStatus {
            return entries.find { it.value == value } ?: UNASSIGNED
        }
        
        /**
         * Get statuses visible to drivers.
         */
        fun driverVisibleStatuses(): List<DriverDeliveryStatus> {
            return listOf(UNASSIGNED, ASSIGNED, PICKED_UP, DELIVERED)
        }
        
        /**
         * Get actionable statuses for drivers.
         */
        fun driverActionableStatuses(): List<DriverDeliveryStatus> {
            return listOf(UNASSIGNED)
        }
    }
}

/**
 * Driver order item model.
 */
data class DriverOrderItem(
    val itemId: String = "",
    val itemName: String = "",
    val quantity: Int = 0,
    val price: Double = 0.0
)

/**
 * Driver delivery order for display.
 * 
 * Displays order information needed for driver delivery.
 */
data class DriverDeliveryOrder(
    val orderId: String = "",
    val restaurantId: String = "",
    val restaurantName: String = "",
    val restaurantAddress: String = "",
    val restaurantPhone: String = "",
    val customerName: String = "",
    val customerAddress: String = "",
    val customerPhone: String = "",
    val items: List<DriverOrderItem> = emptyList(),
    val itemsSummary: String = "",
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val totalCost: Double = 0.0,
    val orderStatus: String = "",
    val deliveryStatus: DriverDeliveryStatus = DriverDeliveryStatus.UNASSIGNED,
    val driverId: String = "",
    val driverName: String = "",
    val paymentMethod: String = "",
    val paymentRef: String = "",
    val paymentStatus: String = "",
    val createdAt: Timestamp? = null,
    val assignedAt: Timestamp? = null
)

/**
 * UI state for driver delivery management.
 */
data class DriverDeliveryUiState(
    val availableOrders: List<DriverDeliveryOrder> = emptyList(),
    val myDeliveries: List<DriverDeliveryOrder> = emptyList(),
    val selectedOrder: DriverDeliveryOrder? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val actionInProgress: String? = null,
    val successMessage: String? = null
)
