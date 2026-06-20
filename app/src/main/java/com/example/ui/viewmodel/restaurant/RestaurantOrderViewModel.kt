package com.example.ui.viewmodel.restaurant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FirestoreService
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Restaurant Order ViewModel for BiteDash.
 * 
 * Handles:
 * - Loading PAID orders for a restaurant
 * - Accepting orders
 * - Rejecting orders
 * 
 * Does NOT:
 * - Assign drivers
 * - Send notifications
 * - Complete deliveries
 * 
 * @property firestoreService Firestore service instance
 * @property restaurantId The restaurant ID to load orders for
 */
class RestaurantOrderViewModel(
    private val firestoreService: FirestoreService,
    private val restaurantId: String
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(RestaurantOrderUiState())
    val uiState: StateFlow<RestaurantOrderUiState> = _uiState.asStateFlow()
    
    init {
        loadOrders()
    }
    
    // ==================== ORDER LOADING ====================
    
    /**
     * Load PAID orders for the restaurant.
     */
    fun loadOrders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            try {
                firestoreService.getRestaurantOrdersFlow(restaurantId)
                    .collect { firestoreOrders ->
                        // Filter to only PAID orders that need restaurant action
                        val paidOrders = firestoreOrders
                            .filter { it.paymentStatus == "PAID" }
                            .map { it.toRestaurantOrder() }
                        
                        _uiState.update {
                            it.copy(
                                orders = paidOrders,
                                isLoading = false
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load orders: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Refresh orders manually.
     */
    fun refreshOrders() {
        loadOrders()
    }
    
    // ==================== ORDER ACTIONS ====================
    
    /**
     * Accept an order.
     * Updates order status to ACCEPTED.
     */
    fun acceptOrder(orderId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = orderId) }
            
            try {
                val success = firestoreService.updateOrderStatus(
                    orderId = orderId,
                    status = RestaurantOrderStatus.ACCEPTED.value
                )
                
                if (success) {
                    // Update local state
                    _uiState.update { state ->
                        state.copy(
                            orders = state.orders.map { order ->
                                if (order.orderId == orderId) {
                                    order.copy(status = RestaurantOrderStatus.ACCEPTED)
                                } else {
                                    order
                                }
                            },
                            actionInProgress = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            actionInProgress = null,
                            errorMessage = "Failed to accept order"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        actionInProgress = null,
                        errorMessage = "Failed to accept order: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Reject an order.
     * Updates order status to REJECTED.
     */
    fun rejectOrder(orderId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = orderId) }
            
            try {
                val success = firestoreService.updateOrderStatus(
                    orderId = orderId,
                    status = RestaurantOrderStatus.REJECTED.value
                )
                
                if (success) {
                    // Update local state - remove from list or mark as rejected
                    _uiState.update { state ->
                        state.copy(
                            orders = state.orders.filter { it.orderId != orderId },
                            actionInProgress = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            actionInProgress = null,
                            errorMessage = "Failed to reject order"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        actionInProgress = null,
                        errorMessage = "Failed to reject order: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Select an order for detail view.
     */
    fun selectOrder(order: RestaurantOrder) {
        _uiState.update { it.copy(selectedOrder = order) }
    }
    
    /**
     * Clear selected order.
     */
    fun clearSelectedOrder() {
        _uiState.update { it.copy(selectedOrder = null) }
    }
    
    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    // ==================== MAPPING ====================
    
    /**
     * Extension to convert FirestoreOrder to RestaurantOrder.
     */
    private fun com.example.data.firebase.FirestoreOrder.toRestaurantOrder(): RestaurantOrder {
        return RestaurantOrder(
            orderId = this.id,
            userId = this.userId,
            restaurantId = this.restaurantId,
            restaurantName = this.restaurantName,
            customerName = this.customerName,
            customerAddress = this.customerAddress,
            customerPhone = this.customerPhone,
            items = this.items.map { item ->
                RestaurantOrderItem(
                    itemId = item.itemId,
                    itemName = item.itemName,
                    quantity = item.quantity,
                    price = item.price,
                    notes = item.notes ?: ""
                )
            },
            itemsSummary = this.itemsSummary,
            subtotal = this.subtotal,
            deliveryFee = this.deliveryFee,
            totalCost = this.totalCost,
            status = RestaurantOrderStatus.fromString(this.status),
            paymentMethod = this.paymentMethod,
            paymentRef = this.paymentRef,
            paymentStatus = this.paymentStatus,
            createdAt = this.createdAt,
            acceptedAt = this.acceptedAt
        )
    }
}
