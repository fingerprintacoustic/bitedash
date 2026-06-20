package com.example.ui.viewmodel.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FirestoreService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Driver Delivery ViewModel for BiteDash.
 * 
 * Handles:
 * - Loading available orders (READY_FOR_PICKUP)
 * - Accepting deliveries
 * 
 * Does NOT:
 * - Implement GPS tracking
 * - Send notifications
 * - Complete deliveries
 * - Assign drivers automatically
 * 
 * @property firestoreService Firestore service instance
 * @property driverId The current driver's ID
 * @property driverName The current driver's name
 */
class DriverDeliveryViewModel(
    private val firestoreService: FirestoreService,
    private val driverId: String,
    private val driverName: String
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DriverDeliveryUiState())
    val uiState: StateFlow<DriverDeliveryUiState> = _uiState.asStateFlow()
    
    init {
        loadAvailableOrders()
        loadMyDeliveries()
    }
    
    // ==================== ORDER LOADING ====================
    
    /**
     * Load available orders for drivers.
     * Only shows orders with status READY_FOR_PICKUP and delivery UNASSIGNED.
     */
    fun loadAvailableOrders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            try {
                firestoreService.getAvailableDeliveriesFlow()
                    .collect { firestoreOrders ->
                        val availableOrders = firestoreOrders
                            .filter { it.status == "READY_FOR_PICKUP" }
                            .map { it.toDriverDeliveryOrder() }
                        
                        _uiState.update {
                            it.copy(
                                availableOrders = availableOrders,
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
     * Load driver's assigned deliveries.
     */
    fun loadMyDeliveries() {
        viewModelScope.launch {
            try {
                firestoreService.getDriverDeliveriesFlow(driverId)
                    .collect { firestoreOrders ->
                        val myDeliveries = firestoreOrders.map { it.toDriverDeliveryOrder() }
                        
                        _uiState.update {
                            it.copy(myDeliveries = myDeliveries)
                        }
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = "Failed to load deliveries: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Refresh all orders.
     */
    fun refreshOrders() {
        loadAvailableOrders()
        loadMyDeliveries()
    }
    
    // ==================== DELIVERY ACTIONS ====================
    
    /**
     * Accept a delivery.
     * Updates order with:
     * - deliveryStatus = ASSIGNED
     * - driverId = currentDriverId
     * - driverName = currentDriverName
     */
    fun acceptDelivery(orderId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = orderId) }
            
            try {
                val success = firestoreService.assignDriverToOrder(
                    orderId = orderId,
                    driverId = driverId,
                    driverName = driverName
                )
                
                if (success) {
                    // Move order from available to my deliveries
                    _uiState.update { state ->
                        val order = state.availableOrders.find { it.orderId == orderId }
                        val updatedAvailable = state.availableOrders.filter { it.orderId != orderId }
                        
                        state.copy(
                            availableOrders = updatedAvailable,
                            myDeliveries = if (order != null) {
                                state.myDeliveries + order.copy(
                                    deliveryStatus = DriverDeliveryStatus.ASSIGNED,
                                    driverId = driverId,
                                    driverName = driverName
                                )
                            } else {
                                state.myDeliveries
                            },
                            actionInProgress = null,
                            successMessage = "Delivery accepted!"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            actionInProgress = null,
                            errorMessage = "Failed to accept delivery"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        actionInProgress = null,
                        errorMessage = "Failed to accept delivery: ${e.message}"
                    )
                }
            }
        }
    }
    
    // ==================== SELECTION ====================
    
    /**
     * Select an order for detail view.
     */
    fun selectOrder(order: DriverDeliveryOrder) {
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
    
    /**
     * Clear success message.
     */
    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }
    
    // ==================== MAPPING ====================
    
    /**
     * Extension to convert FirestoreOrder to DriverDeliveryOrder.
     */
    private fun com.example.data.firebase.FirestoreOrder.toDriverDeliveryOrder(): DriverDeliveryOrder {
        return DriverDeliveryOrder(
            orderId = this.id,
            restaurantId = this.restaurantId,
            restaurantName = this.restaurantName,
            restaurantAddress = this.restaurantAddress,
            restaurantPhone = this.restaurantPhone,
            customerName = this.customerName,
            customerAddress = this.customerAddress,
            customerPhone = this.customerPhone,
            items = this.items.map { item ->
                DriverOrderItem(
                    itemId = item.itemId,
                    itemName = item.itemName,
                    quantity = item.quantity,
                    price = item.price
                )
            },
            itemsSummary = this.itemsSummary,
            subtotal = this.subtotal,
            deliveryFee = this.deliveryFee,
            totalCost = this.totalCost,
            orderStatus = this.status,
            deliveryStatus = DriverDeliveryStatus.fromString(this.deliveryStatus ?: "UNASSIGNED"),
            driverId = this.driverId ?: "",
            driverName = this.driverName ?: "",
            paymentMethod = this.paymentMethod,
            paymentRef = this.paymentRef,
            paymentStatus = this.paymentStatus,
            createdAt = this.createdAt,
            assignedAt = this.assignedAt
        )
    }
}
