package com.example.ui.viewmodel.restaurant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FirestoreService
import com.google.firebase.Timestamp
import kotlinx.coroutines.Job
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
    private var ordersListenerJob: Job? = null
    
    init {
        loadOrders()
    }
    
    // ==================== ORDER LOADING ====================
    
    /**
     * Load orders for the restaurant and keep listening for live updates.
     *
     * Cancels any previously-running listener first — refreshOrders() used
     * to call this without cancelling, so every manual refresh stacked
     * another parallel Firestore listener on top of the one already
     * running since init{}, rather than replacing it.
     */
    fun loadOrders() {
        ordersListenerJob?.cancel()
        ordersListenerJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            try {
                firestoreService.getRestaurantOrdersFlow(restaurantId)
                    .collect { firestoreOrders ->
                        // Previously filtered to paymentStatus == "PAID" only, but
                        // nothing in the app ever sets that (no Paynow confirmation
                        // webhook, and Cash on Delivery is unpaid until the order
                        // arrives) — so that filter silently hid every order from
                        // this screen, regardless of payment method. Show all orders
                        // for this restaurant instead.
                        //
                        // Now that the Paynow webhook does mark online orders PAID, hide
                        // the ones that are still waiting on an online payment. The order
                        // is saved before the customer pays, so an abandoned, failed or
                        // still-pending payment would otherwise show up as a normal new
                        // order that the restaurant could accept and cook for nothing.
                        // Cash on Delivery is payable on arrival, so it always shows.
                        val orders = firestoreOrders
                            .filter { it.paymentMethod == "CASH_ON_DELIVERY" || it.paymentStatus == "PAID" || it.paymentStatus == "COMPLETED" }
                            .map { it.toRestaurantOrder() }
                        
                        _uiState.update {
                            it.copy(
                                orders = orders,
                                isLoading = false
                            )
                        }
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Expected: loadOrders() cancels the previous listener job
                // before starting a new one (see ordersListenerJob above).
                // That cancellation must propagate normally, not be shown
                // as a user-facing error — this is what was producing the
                // false "Failed to load orders: StandaloneCoroutine was
                // cancelled" message on every refresh.
                throw e
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
     * Updates order status to ACCEPTED in Firestore. The live listener in
     * loadOrders() picks up the change and updates the list — no manual
     * local mutation here, so there's only ever one place that decides
     * what the order list looks like.
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
                    _uiState.update { it.copy(actionInProgress = null) }
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
     * Updates order status to REJECTED in Firestore; the live listener
     * picks up the change (Dashboard/Order Management already filter out
     * REJECTED orders from their active views).
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
                    _uiState.update { it.copy(actionInProgress = null) }
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
     * Start preparing an accepted order.
     * Updates order status to PREPARING in Firestore.
     */
    fun startPreparing(orderId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = orderId) }
            
            try {
                val success = firestoreService.updateOrderStatus(
                    orderId = orderId,
                    status = RestaurantOrderStatus.PREPARING.value
                )
                
                if (success) {
                    _uiState.update { it.copy(actionInProgress = null) }
                } else {
                    _uiState.update {
                        it.copy(
                            actionInProgress = null,
                            errorMessage = "Failed to start preparing order"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        actionInProgress = null,
                        errorMessage = "Failed to start preparing: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Mark an order as ready for pickup.
     * Updates order status to READY_FOR_PICKUP in Firestore.
     *
     * This used to also immediately remove the order from the local list
     * ("Remove from restaurant's view since it's now ready"), racing
     * against the live listener's own update for the same write. That's
     * what made an order appear to vanish from both Order Management and
     * Dashboard until a manual refresh — the document and status were
     * always correct in Firestore the whole time. The live listener
     * already reflects READY_FOR_PICKUP correctly (Dashboard shows
     * "Waiting for Rider Pickup..." for it), so no manual removal is
     * needed here.
     */
    fun markReadyForPickup(orderId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = orderId) }
            
            try {
                val success = firestoreService.updateOrderStatus(
                    orderId = orderId,
                    status = RestaurantOrderStatus.READY_FOR_PICKUP.value
                )
                
                if (success) {
                    _uiState.update { it.copy(actionInProgress = null) }
                } else {
                    _uiState.update {
                        it.copy(
                            actionInProgress = null,
                            errorMessage = "Failed to mark order ready"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        actionInProgress = null,
                        errorMessage = "Failed to mark ready: ${e.message}"
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
