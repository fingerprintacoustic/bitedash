package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.entity.OrderEntity
import com.example.data.entity.DriverEntity
import com.example.data.entity.toEntity
import com.example.data.repository.OrderRepository
import com.example.data.repository.RestaurantRepository
import com.example.data.repository.DriverRepository
import com.example.data.firebase.AuthenticationService
import com.example.data.firebase.FirestoreAdminSettings
import com.example.data.firebase.FirestoreDriver
import com.example.data.firebase.FirestoreOrder
import com.example.data.firebase.FirestoreService
import com.example.data.firebase.toFirestoreDriver
import com.example.data.firebase.toFirestoreMenuItem
import com.example.data.firebase.toFirestoreRestaurant
import com.example.data.firebase.toMenuItem
import com.example.data.firebase.toRoomEntity
import com.example.model.CartItem
import com.example.model.MenuItem
import com.example.model.NEW_MENU_ITEM_ID_PREFIX
import com.example.model.Restaurant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface PaymentStep {
    object Idle : PaymentStep
    object SendingPush : PaymentStep
    /**
     * Mobile money: the customer approves the payment on their own phone (Paynow express
     * checkout), so they stay in the app. [instructions] is Paynow's text for how to approve;
     * for InnBucks, [authorizationCode] is the code to approve in the InnBucks app
     * (valid until [authorizationExpires]).
     */
    data class WaitingForHandsetPin(
        val instructions: String = "",
        val authorizationCode: String = "",
        val authorizationExpires: String = ""
    ) : PaymentStep
    object ProcessingConfirmation : PaymentStep
    /** Paynow hosted checkout is ready — customer needs to open [url] to pay. */
    data class RedirectToPaynow(val url: String) : PaymentStep
    /** [awaitingConfirmation]: a manual mobile-money payment — not actually confirmed paid yet,
     *  an admin still has to check the money arrived. */
    data class Success(val transactionRef: String, val awaitingConfirmation: Boolean = false) : PaymentStep
    data class Error(val message: String) : PaymentStep
}

sealed interface UserProfile {
    object Idle : UserProfile
    // Distinct from Idle so RoleSelectionGate knows this arrival was an
    // explicit "Switch Role" tap from inside a dashboard, not a fresh
    // sign-in. Without that distinction, an already-approved restaurant
    // owner or driver landing back on their own role's tab gets
    // auto-redirected straight back into the dashboard they just tried to
    // leave (the "already registered" LaunchedEffect fires unconditionally),
    // making Switch Role look like it does nothing.
    object SwitchingRole : UserProfile
    object Customer : UserProfile
    data class RestaurantOwner(
        val restaurantId: String, 
        val restaurantName: String,
        val firebaseUid: String = "", // Firebase Auth UID when authenticated
        val isAdminOverride: Boolean = false // true when an admin opened this dashboard on the owner's behalf, not the owner themselves
    ) : UserProfile
    data class Driver(
        val driverId: String, 
        val driverName: String,
        val firebaseUid: String = "" // Firebase Auth UID when authenticated
    ) : UserProfile
    data class Admin(
        val firebaseUid: String = "" // Firebase Auth UID when authenticated
    ) : UserProfile
    
    fun getAuthUid(): String? = when (this) {
        is Idle -> null
        is SwitchingRole -> null
        is Customer -> null
        is RestaurantOwner -> firebaseUid.takeIf { it.isNotEmpty() }
        is Driver -> firebaseUid.takeIf { it.isNotEmpty() }
        is Admin -> firebaseUid.takeIf { it.isNotEmpty() }
    }
}

class BiteDashViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: OrderRepository
    private val restaurantRepo: RestaurantRepository
    private val driverRepo: DriverRepository
    private val firestoreService = FirestoreService()
    // The signed-in user's uid, emitting again on every sign-in / sign-out / account
    // switch. The Firestore listeners below are restarted from it (collectLatest).
    private val authUid = AuthenticationService().observeAuthState().map { it?.uid }.distinctUntilChanged()
    private val paymentRepository = com.example.data.repository.PaymentRepository.getInstance()
    private var trackingJob: Job? = null

    // Flowing States
    private val _currentProfile = MutableStateFlow<UserProfile>(UserProfile.Idle)
    val currentProfile: StateFlow<UserProfile> = _currentProfile.asStateFlow()

    private val _isManualMode = MutableStateFlow(true)
    val isManualMode: StateFlow<Boolean> = _isManualMode.asStateFlow()

    private val _selectedRestaurant = MutableStateFlow<Restaurant?>(null)
    val selectedRestaurant: StateFlow<Restaurant?> = _selectedRestaurant.asStateFlow()

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    private val _driverTip = MutableStateFlow(0.0)
    val driverTip: StateFlow<Double> = _driverTip.asStateFlow()

    fun setDriverTip(tip: Double) {
        _driverTip.value = tip
    }

    private val _checkoutMethod = MutableStateFlow("EcoCash") // EcoCash, InnBucks, Telecash
    val checkoutMethod: StateFlow<String> = _checkoutMethod.asStateFlow()

    private val _phoneInput = MutableStateFlow("")
    val phoneInput: StateFlow<String> = _phoneInput.asStateFlow()

    private val _deliveryAddressInput = MutableStateFlow("")
    val deliveryAddressInput: StateFlow<String> = _deliveryAddressInput.asStateFlow()

    private val _paymentStep = MutableStateFlow<PaymentStep>(PaymentStep.Idle)
    val paymentStep: StateFlow<PaymentStep> = _paymentStep.asStateFlow()

    private val _activeOrder = MutableStateFlow<OrderEntity?>(null)
    val activeOrder: StateFlow<OrderEntity?> = _activeOrder.asStateFlow()

    private val _trackingProgress = MutableStateFlow(0f)
    val trackingProgress: StateFlow<Float> = _trackingProgress.asStateFlow()

    private val _trackingStatusText = MutableStateFlow("Order placed securely")
    val trackingStatusText: StateFlow<String> = _trackingStatusText.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: Browse, 1: Cart, 2: Tracking, 3: History
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _payoutSchedule = MutableStateFlow("Weekly")
    val payoutSchedule: StateFlow<String> = _payoutSchedule.asStateFlow()

    private val _isPayoutInProgress = MutableStateFlow(false)
    val isPayoutInProgress: StateFlow<Boolean> = _isPayoutInProgress.asStateFlow()

    data class RestaurantPayoutSummary(
        val restaurantId: String,
        val restaurantName: String,
        val amountOwed: Double,
        val orderCount: Int
    )

    data class DriverPayoutSummary(
        val driverId: String,
        val driverName: String,
        val amountOwed: Double,
        val orderCount: Int
    )

    private val _restaurantPayouts = MutableStateFlow<List<RestaurantPayoutSummary>>(emptyList())
    val restaurantPayouts: StateFlow<List<RestaurantPayoutSummary>> = _restaurantPayouts.asStateFlow()

    private val _driverPayouts = MutableStateFlow<List<DriverPayoutSummary>>(emptyList())
    val driverPayouts: StateFlow<List<DriverPayoutSummary>> = _driverPayouts.asStateFlow()

    private val _isLoadingPayoutSummary = MutableStateFlow(false)
    val isLoadingPayoutSummary: StateFlow<Boolean> = _isLoadingPayoutSummary.asStateFlow()

    private val _settlingIds = MutableStateFlow<Set<String>>(emptySet())
    val settlingIds: StateFlow<Set<String>> = _settlingIds.asStateFlow()

    fun loadPayoutSummary() {
        viewModelScope.launch {
            _isLoadingPayoutSummary.value = true
            val firestoreService = FirestoreService()
            val unsettled = firestoreService.getUnsettledCompletedOrders()

            _restaurantPayouts.value = unsettled
                .groupBy { it.restaurantId to it.restaurantName }
                .map { (key, orders) ->
                    RestaurantPayoutSummary(
                        restaurantId = key.first,
                        restaurantName = key.second,
                        amountOwed = orders.sumOf { it.restaurantPayoutAmount },
                        orderCount = orders.size
                    )
                }
                .sortedByDescending { it.amountOwed }

            _driverPayouts.value = unsettled
                .filter { !it.driverId.isNullOrBlank() }
                .groupBy { (it.driverId ?: "") to (it.driverName ?: "Unknown Driver") }
                .map { (key, orders) ->
                    DriverPayoutSummary(
                        driverId = key.first,
                        driverName = key.second,
                        amountOwed = orders.sumOf { it.driverPayoutAmount },
                        orderCount = orders.size
                    )
                }
                .sortedByDescending { it.amountOwed }

            _isLoadingPayoutSummary.value = false
        }
    }

    fun markRestaurantPaid(restaurantId: String) {
        viewModelScope.launch {
            _settlingIds.value = _settlingIds.value + restaurantId
            val firestoreService = FirestoreService()
            firestoreService.settleRestaurantPayout(restaurantId)
            loadPayoutSummary()
            _settlingIds.value = _settlingIds.value - restaurantId
        }
    }

    fun markDriverPaid(driverId: String) {
        viewModelScope.launch {
            _settlingIds.value = _settlingIds.value + driverId
            val firestoreService = FirestoreService()
            firestoreService.settleDriverPayout(driverId)
            loadPayoutSummary()
            _settlingIds.value = _settlingIds.value - driverId
        }
    }

    // Dynamic states backed by DB
    private val _restaurantsState = MutableStateFlow<List<Restaurant>>(emptyList())
    val restaurantsState: StateFlow<List<Restaurant>> = _restaurantsState.asStateFlow()

    private val _driversState = MutableStateFlow<List<DriverEntity>>(emptyList())
    val driversState: StateFlow<List<DriverEntity>> = _driversState.asStateFlow()

    var restaurants: List<Restaurant> = emptyList()
    var drivers: List<DriverEntity> = emptyList()

    // Channels that are shown but can't take a payment yet, because they aren't enabled on
    // the Paynow account (OneMoney/Telecash exist only in ZWG, cards need business
    // verification, and O'Mari/ZIPIT aren't enabled). Picking one explains this and blocks
    // the pay button instead of failing at Paynow. To switch a channel on once Paynow
    // enables it, just remove it from this set.
    val unavailableCheckoutMethods = setOf("ZIPIT", "Bank Cards")

    // While the Paynow integration isn't live (see PAYNOW_LIVE), these wallets are paid
    // manually instead: the customer sends the money themselves to the business's own
    // number and reports the reference, and an admin checks it arrived before the
    // restaurant sees the order. Flip PAYNOW_LIVE once Paynow confirms the integration is
    // live, and these go back to the normal automatic Paynow flow with no other changes
    // needed here.
    val manualPaymentMethods = setOf("EcoCash", "OneMoney", "InnBucks", "Telecash", "O'Mari")
    private val PAYNOW_LIVE = false

    private val _manualPaymentReference = MutableStateFlow("")
    val manualPaymentReference: StateFlow<String> = _manualPaymentReference.asStateFlow()
    fun setManualPaymentReference(value: String) { _manualPaymentReference.value = value }

    private val _businessPaymentNumbers = MutableStateFlow<Map<String, String>>(emptyMap())
    val businessPaymentNumbers: StateFlow<Map<String, String>> = _businessPaymentNumbers.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _businessPaymentNumbers.value = firestoreService.getPublicPaymentNumbers()
            } catch (e: Exception) {
                // Best-effort: the reference field still works without the number shown.
            }
        }
    }

    val checkoutMethods = listOf("EcoCash", "InnBucks", "OneMoney", "O'Mari", "Telecash", "ZIPIT", "Bank Cards", "USD Cash")

    init {
        val database = AppDatabase.getDatabase(application)
        repository = OrderRepository(database.orderDao())
        restaurantRepo = RestaurantRepository(database.restaurantDao())
        driverRepo = DriverRepository(database.driverDao())

        // Collect and keep lists synchronized for backwards-compatible lookups
        viewModelScope.launch {
            restaurantRepo.allRestaurants.collect { entities ->
                val domainList = entities.map { it.toDomain() }
                _restaurantsState.value = domainList
                restaurants = domainList
            }
        }

        viewModelScope.launch {
            driverRepo.allDrivers.collect { entities ->
                _driversState.value = entities
                drivers = entities
            }
        }

// These run automatically for every authenticated session, so a single
// bad document or transient Firestore error here must never crash the
// app — and, just as important, must never silently block every OTHER
// restaurant/driver from syncing either. Each item is converted inside
// its own try/catch now, so one bad document is skipped on its own
// instead of blanking the whole list for everyone. Failures are logged
// so a future issue shows up in logcat instead of needing guesswork.
//
// Both listeners are (re)started whenever the signed-in user changes. A
// Firestore snapshot listener that errors is dead for good — e.g. the drivers
// listener, which only an admin may run, dies with "permission denied" when it
// starts under a non-admin account — and so it used to stay dead after
// switching to an admin account in the same session, leaving the admin's lists
// stale until the app was restarted.
viewModelScope.launch {
    authUid.collectLatest {
    try {
        firestoreService.getRestaurantsFlow().collect { firestoreRestaurants ->
            val entities = firestoreRestaurants.mapNotNull { fr ->
                try {
                    val menuItems = try {
                        firestoreService.getMenuItemsFlow(fr.id).first()
                            .map { it.toMenuItem() }
                            .sortedWith(compareBy({ it.category }, { it.name }))
                    } catch (e: Exception) {
                        emptyList()
                    }
                    fr.toRoomEntity(menuItems)
                } catch (e: Exception) {
                    android.util.Log.e("BiteDashSync", "Skipped restaurant ${fr.id}: ${e.message}", e)
                    null
                }
            }
            try {
                restaurantRepo.replaceAll(entities)
            } catch (e: Exception) {
                android.util.Log.e("BiteDashSync", "Failed to save restaurants to local cache: ${e.message}", e)
            }
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("BiteDashSync", "Restaurant listener failed to start: ${e.message}", e)
    }
    }
}

viewModelScope.launch {
    authUid.collectLatest {
    try {
        firestoreService.getDriversFlow().collect { firestoreDrivers ->
            val entities = firestoreDrivers.mapNotNull { fd ->
                try {
                    fd.toRoomEntity()
                } catch (e: Exception) {
                    android.util.Log.e("BiteDashSync", "Skipped driver ${fd.id}: ${e.message}", e)
                    null
                }
            }
            try {
                driverRepo.replaceAll(entities)
            } catch (e: Exception) {
                android.util.Log.e("BiteDashSync", "Failed to save drivers to local cache: ${e.message}", e)
            }
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("BiteDashSync", "Driver listener failed to start: ${e.message}", e)
    }
    }
}

        // Selected restaurant monitoring: auto-clear if it gets deleted by admin
        viewModelScope.launch {
            restaurantsState.collect { list ->
                val selected = _selectedRestaurant.value
                if (selected != null && list.none { it.id == selected.id }) {
                    _selectedRestaurant.value = null
                }
            }
        }
    }

    // Admin Panel Actions
    fun addRestaurant(restaurant: Restaurant) {
        val maxOrder = restaurantsState.value.maxOfOrNull { it.displayOrder } ?: 0
        viewModelScope.launch {
            val toCreate = restaurant.copy(displayOrder = maxOrder + 1)
            val newId = firestoreService.createRestaurant(toCreate.toEntity().toFirestoreRestaurant())
            if (newId != null) {
                toCreate.menuItems.forEach { item ->
                    firestoreService.createMenuItem(item.toFirestoreMenuItem(newId))
                }
            }
        }
    }

    private val terminalOrderStatuses = setOf("COMPLETED", "REJECTED", "CANCELLED")

    // Returns the count of orders still in progress, or -1 if the check
    // itself failed (e.g. no connectivity) — the caller decides how to
    // handle that case rather than silently treating it as "0 active".
    suspend fun getActiveOrderCountForRestaurant(restaurantId: String): Int {
        return try {
            firestoreService.getRestaurantOrdersFlow(restaurantId).first()
                .count { it.status !in terminalOrderStatuses }
        } catch (e: Exception) {
            -1
        }
    }

    suspend fun getActiveOrderCountForDriver(driverId: String): Int {
        return try {
            firestoreService.getDriverOrdersFlow(driverId).first()
                .count { it.status !in terminalOrderStatuses }
        } catch (e: Exception) {
            -1
        }
    }

    // Cancels a real order — the only way to close out a stuck order,
    // since orders can never be deleted (audit trail, enforced by
    // firestore.rules regardless of role). This is what actually
    // unblocks a restaurant/driver delete that's blocked on an active order.
    // Live feed of real orders still in progress, for the admin Orders
    // tab — the only place a stuck order can be cancelled (not deleted;
    // orders can never be deleted, see firestore.rules).
    //
    // Wrapped defensively: getActiveOrdersFlow() previously threw
    // synchronously (an invalid double !=  filter — since fixed) right
    // here during ViewModel construction, crashing the app on every
    // authenticated session before any UI even rendered. Both the
    // construction call and the flow's own collection are now guarded so
    // a future Firestore error here degrades to an empty list instead of
    // taking down the whole app again.
    val activeOrdersForAdmin: StateFlow<List<FirestoreOrder>> = try {
        firestoreService.getActiveOrdersFlow()
    } catch (e: Exception) {
        kotlinx.coroutines.flow.flowOf(emptyList())
    }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun cancelOrder(orderId: String) {
        viewModelScope.launch {
            firestoreService.updateOrderStatus(orderId, "CANCELLED")
        }
    }

    // Manual mobile-money orders waiting on an admin to check the transfer arrived,
    // derived from the same live feed activeOrdersForAdmin already collects.
    val manualPaymentsAwaitingConfirmation: StateFlow<List<FirestoreOrder>> = activeOrdersForAdmin
        .map { orders -> orders.filter { it.paymentStatus == "AWAITING_MANUAL_CONFIRMATION" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun confirmManualPayment(orderId: String) {
        viewModelScope.launch {
            firestoreService.confirmManualPayment(orderId)
        }
    }

    fun rejectManualPayment(orderId: String) {
        viewModelScope.launch {
            firestoreService.rejectManualPayment(orderId)
        }
    }

    fun updateBusinessPaymentNumbers(numbers: Map<String, String>) {
        viewModelScope.launch {
            if (firestoreService.setPublicPaymentNumbers(numbers)) {
                _businessPaymentNumbers.value = numbers
            }
        }
    }

    fun removeRestaurant(restaurantId: String) {
        viewModelScope.launch {
            firestoreService.deleteRestaurant(restaurantId)
        }
    }

    fun approveRestaurant(restaurantId: String) {
        viewModelScope.launch {
            firestoreService.updateRestaurantField(restaurantId, "isApproved", true)
        }
    }

    fun approveDriver(driverId: String) {
        viewModelScope.launch {
            firestoreService.updateDriverField(driverId, "isApproved", true)
        }
    }

    fun updateRestaurantStaff(restaurantId: String, staffEmails: List<String>) {
        viewModelScope.launch {
            firestoreService.updateRestaurantField(restaurantId, "staffEmails", staffEmails)
        }
    }

    fun moveRestaurantUp(restaurant: Restaurant) {
        val list = restaurantsState.value.sortedBy { it.displayOrder }
        val index = list.indexOfFirst { it.id == restaurant.id }
        if (index > 0) {
            val current = list[index]
            val above = list[index - 1]
            
            val currentOrder = current.displayOrder
            val aboveOrder = above.displayOrder
            
            val newCurrentOrder = aboveOrder
            val newAboveOrder = if (currentOrder == aboveOrder) aboveOrder + 1 else currentOrder
            
            viewModelScope.launch {
                firestoreService.updateRestaurantField(current.id, "displayOrder", newCurrentOrder)
                firestoreService.updateRestaurantField(above.id, "displayOrder", newAboveOrder)
            }
        }
    }

    fun moveRestaurantDown(restaurant: Restaurant) {
        val list = restaurantsState.value.sortedBy { it.displayOrder }
        val index = list.indexOfFirst { it.id == restaurant.id }
        if (index >= 0 && index < list.size - 1) {
            val current = list[index]
            val below = list[index + 1]
            
            val currentOrder = current.displayOrder
            val belowOrder = below.displayOrder
            
            val newCurrentOrder = if (currentOrder == belowOrder) belowOrder + 1 else belowOrder
            val newBelowOrder = if (currentOrder == belowOrder) belowOrder else currentOrder
            
            viewModelScope.launch {
                firestoreService.updateRestaurantField(current.id, "displayOrder", newCurrentOrder)
                firestoreService.updateRestaurantField(below.id, "displayOrder", newBelowOrder)
            }
        }
    }

    // Saves the "Manage Menu" editor. Each item is written to the document
    // with its own id — updated in place if it exists, created if it
    // doesn't — and only the items the owner explicitly removed
    // (removedItemIds) are deleted. This deliberately never reads the current
    // documents to work out what changed: that list query can be denied or
    // stale, and acting on an incomplete read either duplicates or deletes
    // the wrong items. "Sold out" (isAvailable == false) is just a field on
    // the item; it must not double as "deleted".
    fun updateRestaurantMenu(
        restaurantId: String,
        updatedMenuItems: List<MenuItem>,
        removedItemIds: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            val existing = restaurantsState.value.find { it.id == restaurantId }
            if (existing != null) {
                // Items the editor just added carry a temporary id; give them
                // their real document id now, so the optimistic copy below
                // (which the editor can fall back to) and the Firestore
                // write agree on it and a later save can't create them twice.
                val itemsWithIds = updatedMenuItems.map { item ->
                    val isNew = item.id.startsWith(NEW_MENU_ITEM_ID_PREFIX)
                    (if (isNew) item.copy(id = firestoreService.newMenuItemId()) else item) to isNew
                }

                // Optimistic local update so the editing admin sees the change
                // instantly; the Firestore listener will reconcile shortly after.
                if (_selectedRestaurant.value?.id == restaurantId) {
                    _selectedRestaurant.value = existing.copy(menuItems = itemsWithIds.map { it.first })
                }

                removedItemIds.forEach { firestoreService.deleteMenuItem(it, restaurantId) }
                itemsWithIds.forEach { (item, isNew) ->
                    firestoreService.saveMenuItem(restaurantId, item.toFirestoreMenuItem(restaurantId), isNew)
                }
            }
        }
    }

    fun addDriver(name: String, phone: String, vehicle: String) {
        viewModelScope.launch {
            firestoreService.createDriver(FirestoreDriver(name = name, phone = phone, vehicle = vehicle))
        }
    }

    fun removeDriver(id: String) {
        viewModelScope.launch {
            firestoreService.deleteDriver(id)
        }
    }



    // Room Database Flows
    val orderHistory: StateFlow<List<OrderEntity>> = repository.allOrders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val dbActiveOrders: StateFlow<List<OrderEntity>> = repository.activeOrders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Collect dbActiveOrders and resume tracking if there's any active item
        viewModelScope.launch {
            dbActiveOrders.collect { activeList ->
                if (activeList.isNotEmpty()) {
                    val currentTrack = activeList.first()
                    _activeOrder.value = currentTrack
                    if (_isManualMode.value) {
                        updateTrackingStateManual(currentTrack.status)
                    } else if (trackingJob == null || !trackingJob!!.isActive) {
                        resumeTracking(currentTrack)
                    }
                } else {
                    _activeOrder.value = null
                }
            }
        }

        // Mirror the real progress of the customer's orders from Firestore into the
        // local cache. The restaurant and driver update the order in Firestore, but the
        // customer's tracking screen reads the local Room copy, which nothing ever
        // refreshed, so a customer stayed on "Waiting for Restaurant to Accept..." for
        // an order that was already delivered. Only in manual (real) mode: the
        // simulation mode fakes its own progress locally and its Firestore copy never
        // moves past PREPARING.
        viewModelScope.launch {
            authUid.collectLatest { uid ->
                if (uid == null) return@collectLatest
                try {
                    firestoreService.getUserOrderUpdatesFlow(uid).collect { remoteOrders ->
                        if (!_isManualMode.value) return@collect
                        remoteOrders.forEach { remote ->
                            try {
                                val local = repository.getOrderByFirestoreId(remote.id) ?: return@forEach
                                // Forward-only, and a finished order stays finished, so a
                                // stale snapshot can never drag an order backwards.
                                val localRank = orderStatusRank(local.status)
                                if (localRank in 0..4 && orderStatusRank(remote.status) > localRank) {
                                    repository.updateOrderStatus(local.id, remote.status)
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                android.util.Log.e("BiteDashSync", "Failed to mirror order ${remote.id}: ${e.message}", e)
                            }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("BiteDashSync", "Order status listener failed to start: ${e.message}", e)
                }
            }
        }
    }

    // How far along an order is, for the forward-only mirroring above. Unknown or
    // not-yet-meaningful statuses (e.g. PAID) rank -1 and are never applied; the two
    // dead ends (rejected / cancelled) rank above everything else.
    private fun orderStatusRank(status: String): Int = when (status) {
        "PENDING_ACCEPTANCE" -> 0
        "ACCEPTED" -> 1
        "PREPARING" -> 2
        "READY_FOR_PICKUP" -> 3
        "OUT_FOR_DELIVERY" -> 4
        "COMPLETED" -> 5
        "REJECTED", "CANCELLED" -> 6
        else -> -1
    }

    // Search and filters
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    fun selectRestaurant(restaurant: Restaurant?) {
        _selectedRestaurant.value = restaurant
    }

    fun selectTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    // Cart actions
    fun addToCart(menuItem: MenuItem) {
        val currentList = _cart.value.toMutableList()
        val existingItem = currentList.find { it.menuItem.id == menuItem.id }
        if (existingItem != null) {
            existingItem.quantity += 1
        } else {
            currentList.add(CartItem(menuItem, 1))
        }
        _cart.value = currentList
    }

    fun removeFromCart(menuItem: MenuItem) {
        val currentList = _cart.value.toMutableList()
        val existingItem = currentList.find { it.menuItem.id == menuItem.id }
        if (existingItem != null) {
            existingItem.quantity -= 1
            if (existingItem.quantity <= 0) {
                currentList.remove(existingItem)
            }
        }
        _cart.value = currentList
    }

    fun clearCart() {
        _cart.value = emptyList()
        _driverTip.value = 0.0
    }

    fun getCartTotal(): Double {
        var total = 0.0
        _cart.value.forEach {
            total += it.menuItem.price * it.quantity
        }
        // Add delivery fee from selected restaurant if any, otherwise 0
        val selectedRest = _selectedRestaurant.value
        if (selectedRest != null && _cart.value.isNotEmpty()) {
            total += selectedRest.deliveryFee
        }
        // Add driver tip
        total += _driverTip.value
        return total
    }

    // Payment Info
    fun setCheckoutMethod(method: String) {
        _checkoutMethod.value = method
        // Pre-populate phone format hints based on standard Zimbabwean mobile money prefixes
        when (method) {
            "EcoCash" -> if (!_phoneInput.value.startsWith("077") && !_phoneInput.value.startsWith("078")) _phoneInput.value = "077"
            "InnBucks" -> if (!_phoneInput.value.startsWith("07")) _phoneInput.value = "07"
            // NetOne (OneMoney) numbers start 071 and Telecel (Telecash) 073; these two
            // prefixes were swapped.
            "OneMoney" -> if (!_phoneInput.value.startsWith("071")) _phoneInput.value = "071"
            "O'Mari" -> if (!_phoneInput.value.startsWith("077") && !_phoneInput.value.startsWith("078")) _phoneInput.value = "077"
            "Telecash" -> if (!_phoneInput.value.startsWith("073")) _phoneInput.value = "073"
            "ZIPIT" -> if (!_phoneInput.value.startsWith("07")) _phoneInput.value = "07"
            // A card can belong to someone outside Zimbabwe, so don't force a Zimbabwean
            // prefix on the contact number here: just clear one that was only a leftover
            // prefill from another channel, and leave anything the customer typed alone.
            "Bank Cards" -> if (_phoneInput.value in setOf("07", "071", "073", "077", "078")) _phoneInput.value = ""
            else -> if (!_phoneInput.value.startsWith("07")) _phoneInput.value = "07"
        }
    }

    fun setPhoneInput(phone: String) {
        _phoneInput.value = phone
    }

    fun setDeliveryAddressInput(address: String) {
        _deliveryAddressInput.value = address
    }

    // Pre-fills the checkout delivery address from the customer's saved
    // profile address (set at signup) the first time checkout opens, but
    // never overwrites something the customer has already typed here —
    // it stays editable per order from there.
    fun loadDeliveryAddressDefaultIfBlank() {
        if (_deliveryAddressInput.value.isNotBlank()) return
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val profile = firestoreService.getUser(uid)
                if (_deliveryAddressInput.value.isBlank() && !profile?.address.isNullOrBlank()) {
                    _deliveryAddressInput.value = profile!!.address
                }
            } catch (e: Exception) {
                // Best-effort prefill only — checkout still works if this fails,
                // the customer can just type their address in manually.
            }
        }
    }

    // Maps this app's payment-channel selector strings to the canonical
    // values FirestoreOrder.paymentMethod expects. Channels without a
    // real mobile-money equivalent (Bank Cards, ZIPIT, etc.) still need
    // *some* stable value, so they fall back to an uppercased/underscored
    // version of their own label rather than being lost.
    private fun mapToFirestorePaymentMethod(method: String): String = when (method) {
        "EcoCash" -> "ECO_CASH"
        "OneMoney" -> "ONE_MONEY"
        "InnBucks" -> "INNBUCKS"
        "USD Cash" -> "CASH_ON_DELIVERY"
        else -> method.uppercase().replace(" ", "_").replace("'", "")
    }

    // What the placeOrder Cloud Function created, with the server's own figures.
    private data class PlacedOrder(
        val orderId: String,
        val itemsSummary: String,
        val status: String,
        val totalCost: Double
    )

    // Calls the placeOrder Cloud Function. Sends only the customer's choices (item
    // ids, quantities, tip, address, payment channel); it prices and saves the order.
    private suspend fun placeOrderOnServer(
        restaurantId: String,
        cartItems: List<CartItem>,
        customerPhone: String,
        method: String,
        manualPayment: Boolean = false,
        paymentReference: String = ""
    ): PlacedOrder {
        val response = com.google.firebase.functions.FirebaseFunctions.getInstance()
            .getHttpsCallable("placeOrder")
            .call(
                hashMapOf(
                    "restaurantId" to restaurantId,
                    "items" to cartItems.map { mapOf("menuItemId" to it.menuItem.id, "quantity" to it.quantity) },
                    "driverTip" to _driverTip.value,
                    "deliveryAddress" to _deliveryAddressInput.value,
                    "customerPhone" to customerPhone,
                    "paymentMethod" to mapToFirestorePaymentMethod(method),
                    "manualMode" to _isManualMode.value,
                    // Payment-manual (customer already sent the money) — unrelated to
                    // manualMode above (the restaurant's accept-orders setting).
                    "manualPayment" to manualPayment,
                    "paymentReference" to paymentReference
                )
            )
            .await()
            .data as? Map<*, *>
            ?: throw IllegalStateException("Unexpected response from the order server")

        return PlacedOrder(
            orderId = response["orderId"] as? String ?: throw IllegalStateException("The order server returned no order id"),
            itemsSummary = response["itemsSummary"] as? String ?: "",
            status = response["status"] as? String ?: "PENDING_ACCEPTANCE",
            totalCost = (response["totalCost"] as? Number)?.toDouble()
                ?: throw IllegalStateException("The order server returned no total")
        )
    }

    // Places the order, then — unless paying Cash on Delivery — takes the
    // customer through a real Paynow payment (hosted checkout redirect +
    // server-verified confirmation) before the order is treated as paid.
    fun processCheckout() {
        val cartItems = _cart.value
        val restaurant = _selectedRestaurant.value ?: return
        if (cartItems.isEmpty()) return

        val paymentPhone = _phoneInput.value
        val method = _checkoutMethod.value
        val isCash = method == "USD Cash"
        val isManualPayment = !PAYNOW_LIVE && method in manualPaymentMethods

        if (method in unavailableCheckoutMethods) {
            _paymentStep.value = PaymentStep.Error(
                "$method payments aren't available yet. Please pay with EcoCash, InnBucks or cash on delivery."
            )
            return
        }

        // Simple validation
        if (!isCash && paymentPhone.length < 9) {
            _paymentStep.value = PaymentStep.Error(
                if (method == "Bank Cards") "Please enter a contact phone number (at least 9 digits)."
                else "Please enter a valid Zimbabwean mobile money number."
            )
            return
        }
        if (isManualPayment && _manualPaymentReference.value.trim().length < 3) {
            _paymentStep.value = PaymentStep.Error(
                "Please enter the reference or confirmation you got after sending the $method payment."
            )
            return
        }
        if (_deliveryAddressInput.value.isBlank()) {
            _paymentStep.value = PaymentStep.Error("Please enter a delivery address so the restaurant and driver know where to bring your order.")
            return
        }

        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            _paymentStep.value = PaymentStep.Error("You need to be signed in to place an order.")
            return
        }

        viewModelScope.launch {
            _paymentStep.value = PaymentStep.SendingPush

            // Best-effort lookup of the customer's profile for name/address on
            // the order — the order still goes through even if this fails.
            val customerProfile = try {
                firestoreService.getUser(uid)
            } catch (e: Exception) {
                null
            }

            // Write the order through to Firestore FIRST, via the placeOrder Cloud
            // Function. That doc is what a restaurant owner's Order Management screen
            // (getRestaurantOrdersFlow) and the admin Orders tab (getActiveOrdersFlow)
            // actually read from — without it an order only ever existed on the
            // customer's own device and no restaurant could ever see or accept it.
            //
            // We only say WHAT the customer chose (item ids and quantities, tip,
            // address); the server prices it from Firestore. Sending prices from here
            // let a tampered client set its own total, which initiatePaynowPayment
            // would then charge as the order's amount.
            val placed = try {
                placeOrderOnServer(
                    restaurant.id, cartItems, paymentPhone, method,
                    manualPayment = isManualPayment,
                    paymentReference = _manualPaymentReference.value.trim()
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: com.google.firebase.functions.FirebaseFunctionsException) {
                // The server's own message is user-readable for these ("Test Wings is
                // sold out", "This restaurant isn't taking orders yet").
                val serverMessage = when (e.code) {
                    com.google.firebase.functions.FirebaseFunctionsException.Code.INVALID_ARGUMENT,
                    com.google.firebase.functions.FirebaseFunctionsException.Code.FAILED_PRECONDITION,
                    com.google.firebase.functions.FirebaseFunctionsException.Code.NOT_FOUND -> e.message
                    else -> null
                }
                _paymentStep.value = PaymentStep.Error(
                    serverMessage ?: "Couldn't reach the server to place your order. Please check your connection and try again."
                )
                return@launch
            } catch (e: Exception) {
                _paymentStep.value = PaymentStep.Error(
                    "Couldn't reach the server to place your order. Please check your connection and try again."
                )
                return@launch
            }

            val firestoreOrderId = placed.orderId
            // The server's numbers, not the cart's: they're what was actually saved and
            // what gets charged, and they can differ if a price changed since the menu loaded.
            val itemsSummaryStr = placed.itemsSummary
            val initialStatus = placed.status
            val orderTotal = placed.totalCost

            // Save this delivery address back to the customer's profile if
            // it's new or changed, so it prefills as the default next time
            // (best-effort — the order itself already succeeded above).
            if (customerProfile?.address != _deliveryAddressInput.value) {
                try {
                    firestoreService.updateUserField(uid, "address", _deliveryAddressInput.value)
                } catch (e: Exception) {
                    // Non-fatal — the order is already placed either way.
                }
            }

            // Reference shown on the success screen: the real Paynow
            // reference once paid, or the order ID itself for Cash on
            // Delivery (which has no Paynow transaction).
            var ref = firestoreOrderId

            if (isManualPayment) {
                // The order is already saved with paymentStatus AWAITING_MANUAL_CONFIRMATION
                // (see placeOrder) and customerPaymentReference set — nothing left to do here.
                // An admin checks the money arrived before the restaurant sees this order.
                _manualPaymentReference.value = ""
            } else if (!isCash) {
                // Still SendingPush here — we're now asking initiatePaynowPayment
                // to actually start the transaction with Paynow.
                val paymentResult = paymentRepository.initiatePayment(
                    com.example.data.payment.PaymentRequest(
                        orderId = firestoreOrderId,
                        userId = uid,
                        amount = orderTotal,
                        method = com.example.data.payment.PaymentMethod.fromString(mapToFirestorePaymentMethod(method)),
                        mobileMoneyNumber = paymentPhone,
                        description = "BiteDash Order $firestoreOrderId"
                    )
                )

                when (paymentResult) {
                    is com.example.data.payment.PaymentResult.Success -> {
                        val transaction = paymentResult.transaction

                        // Mobile money comes back with no page to open: the customer approves
                        // on their phone. Anything else (cards) opens Paynow's hosted page.
                        // Either state stays up for the whole poll below, so approving /
                        // returning to the app after paying resolves it.
                        _paymentStep.value = if (transaction.browserUrl.isBlank()) {
                            PaymentStep.WaitingForHandsetPin(
                                instructions = transaction.instructions,
                                authorizationCode = transaction.authorizationCode,
                                authorizationExpires = transaction.authorizationExpires
                            )
                        } else {
                            PaymentStep.RedirectToPaynow(transaction.browserUrl)
                        }

                        val finalStatus = pollPaynowUntilResolved(transaction.transactionId)
                        when (finalStatus) {
                            com.example.data.payment.PaymentStatus.PAID -> ref = transaction.transactionId
                            com.example.data.payment.PaymentStatus.CANCELLED -> {
                                _paymentStep.value = PaymentStep.Error("Payment was cancelled.")
                                return@launch
                            }
                            com.example.data.payment.PaymentStatus.FAILED -> {
                                _paymentStep.value = PaymentStep.Error(
                                    "The payment didn't go through. It may have been declined or your balance was too low. Please try again."
                                )
                                return@launch
                            }
                            else -> {
                                _paymentStep.value = PaymentStep.Error(
                                    "We couldn't confirm your payment in time. If money left your account, " +
                                        "it'll still be applied automatically shortly — otherwise contact " +
                                        "support with order $firestoreOrderId."
                                )
                                return@launch
                            }
                        }
                    }
                    is com.example.data.payment.PaymentResult.Error -> {
                        _paymentStep.value = PaymentStep.Error(paymentResult.message)
                        return@launch
                    }
                    is com.example.data.payment.PaymentResult.Cancelled -> {
                        _paymentStep.value = PaymentStep.Error("Payment was cancelled.")
                        return@launch
                    }
                }
            }

            // Save to local Room cache (mirrors Firestore; not the source of truth)
            val newOrder = OrderEntity(
                restaurantName = restaurant.name,
                itemsSummary = itemsSummaryStr,
                totalCost = orderTotal,
                paymentMethod = method,
                paymentPhone = paymentPhone,
                status = initialStatus,
                driverTip = _driverTip.value,
                firestoreOrderId = firestoreOrderId
            )

            val orderId = repository.insertOrder(newOrder)
            val insertedOrder = newOrder.copy(id = orderId.toInt())

            _paymentStep.value = PaymentStep.Success(ref, awaitingConfirmation = isManualPayment)
            _activeOrder.value = insertedOrder

            // Clear Cart and select restaurant
            clearCart()
            _selectedRestaurant.value = null

            // Jump to Active delivery tab
            _selectedTab.value = 2

            if (!_isManualMode.value) {
                // Start delivery simulation!
                startTrackingSimulation(insertedOrder)
            } else {
                updateTrackingStateManual(initialStatus)
            }
        }
    }

    fun resetPaymentState() {
        _paymentStep.value = PaymentStep.Idle
    }

    /**
     * Poll checkPaynowPaymentStatus until Paynow reports a terminal status
     * or we give up. The Cloud Function verifies each response with Paynow
     * (hash-checked) and applies it to Firestore itself — this just relays
     * the outcome back so the UI can react.
     */
    private suspend fun pollPaynowUntilResolved(
        transactionId: String,
        maxAttempts: Int = 60,
        intervalMs: Long = 5000L
    ): com.example.data.payment.PaymentStatus {
        repeat(maxAttempts) {
            delay(intervalMs)
            val result = paymentRepository.checkPaymentStatus(transactionId)
            if (result.status != com.example.data.payment.PaymentStatus.PENDING) {
                return result.status
            }
        }
        return com.example.data.payment.PaymentStatus.PENDING
    }

    // Simulated Tracking Timeline
    private fun startTrackingSimulation(order: OrderEntity) {
        trackingJob?.cancel()
        _trackingProgress.value = 0.0f
        _trackingStatusText.value = "Order placed! Preparing your meal..."

        trackingJob = viewModelScope.launch {
            val courier = drivers.filter { it.isAvailable }.randomOrNull() ?: DriverEntity(name = "Tinashe", phone = "0771234567", vehicle = "Motorbike")
            val courierName = courier.name
            val courierVehicle = courier.vehicle

            // Step 1: Cook preparing meal in restaurant kitchen
            repository.updateOrderStatus(order.id, "PREPARING")
            updateActiveOrderLocalState(order.id, "PREPARING")
            _trackingStatusText.value = "Kitchen preparing your freshly cooked meal..."
            _trackingProgress.value = 0.08f
            delay(7000)

            // Step 2: Courier arriving at restaurant
            _trackingStatusText.value = "Courier $courierName ($courierVehicle) has arrived at Restaurant. Packing order..."
            _trackingProgress.value = 0.20f
            delay(6000)

            // Step 3: Out for delivery (rider moves)
            repository.updateOrderStatus(order.id, "OUT_FOR_DELIVERY")
            updateActiveOrderLocalState(order.id, "OUT_FOR_DELIVERY")
            _trackingStatusText.value = "$courierName is cruising down Samora Machel Avenue on their $courierVehicle!"

            // Rider moves from point A to point B
            val totalSteps = 30
            for (i in 1..totalSteps) {
                delay(1000)
                val fractionalProgress = 0.20f + (i.toFloat() / totalSteps) * 0.75f
                _trackingProgress.value = fractionalProgress

                // Change tracking message based on movement progress
                when {
                    fractionalProgress < 0.40f -> {
                        _trackingStatusText.value = "Courier $courierName riding past Julius Nyerere Way..."
                    }
                    fractionalProgress < 0.65f -> {
                        _trackingStatusText.value = "Courier $courierName approaching Leopold Takawira Street..."
                    }
                    fractionalProgress < 0.85f -> {
                        _trackingStatusText.value = "Rider $courierName turning onto Chiedza Road, almost there!"
                    }
                    else -> {
                        _trackingStatusText.value = "Rider unpacking your warm food cargo..."
                    }
                }
            }

            // Step 4: Finished delivery
            _trackingProgress.value = 1.0f
            repository.updateOrderStatus(order.id, "COMPLETED")
            _trackingStatusText.value = "Meal successfully delivered! Enjoy your sadza / chicken!"
            updateActiveOrderLocalState(order.id, "COMPLETED")

            // Give a delay, then clear the active order from active tracking page so user can track new order later, or keep it as completed state
            delay(5000)
            _activeOrder.value = null
        }
    }

    private fun resumeTracking(order: OrderEntity) {
        if (order.status == "COMPLETED") return
        trackingJob?.cancel()
        trackingJob = viewModelScope.launch {
            // Pick up from appropriate progress
            val startProgress = when (order.status) {
                "PREPARING" -> 0.15f
                "OUT_FOR_DELIVERY" -> 0.45f
                else -> 0.05f
            }
            _trackingProgress.value = startProgress
            _trackingStatusText.value = "Resuming real-time tracking for Order #${order.id}..."

            val leftSteps = 20
            for (i in 1..leftSteps) {
                delay(1200)
                val fraction = startProgress + (i.toFloat() / leftSteps) * (1.0f - startProgress - 0.05f)
                _trackingProgress.value = fraction

                when {
                    fraction < 0.35f -> _trackingStatusText.value = "Preparing your dish with care..."
                    fraction < 0.60f -> _trackingStatusText.value = "Rider rushing past Leopold Takawira intersection..."
                    fraction < 0.85f -> _trackingStatusText.value = "Rider cruising through your neighborhood, almost there!"
                    else -> _trackingStatusText.value = "Rider is outside. Ringing the bell!"
                }
            }

            _trackingProgress.value = 1.0f
            repository.updateOrderStatus(order.id, "COMPLETED")
            _trackingStatusText.value = "Delivered! Savor BiteDash meal."
            updateActiveOrderLocalState(order.id, "COMPLETED")

            delay(5000)
            _activeOrder.value = null
        }
    }

    private suspend fun updateActiveOrderLocalState(orderId: Int, newStatus: String) {
        val currentActive = _activeOrder.value
        if (currentActive != null && currentActive.id == orderId) {
            _activeOrder.value = currentActive.copy(status = newStatus)
        }
    }

    // Quick Reorder
    fun reorderPastItems(order: OrderEntity) {
        // Find matching restaurant
        val matchingRestaurant = restaurants.find { it.name == order.restaurantName }
        if (matchingRestaurant != null) {
            _selectedRestaurant.value = matchingRestaurant
            _cart.value = emptyList()

            // Parse items list strings like "2-Piece & Chips x2, Scones x1"
            val parts = order.itemsSummary.split(", ")
            val cartList = mutableListOf<CartItem>()
            for (part in parts) {
                val itemRegex = "(.*) x(\\d+)".toRegex()
                val matchResult = itemRegex.find(part)
                if (matchResult != null) {
                    val name = matchResult.groupValues[1]
                    val qty = matchResult.groupValues[2].toIntOrNull() ?: 1
                    val matchingMenu = matchingRestaurant.menuItems.find { it.name.trim().lowercase() == name.trim().lowercase() }
                    if (matchingMenu != null) {
                        cartList.add(CartItem(matchingMenu, qty))
                    }
                }
            }

            if (cartList.isNotEmpty()) {
                _cart.value = cartList
                _selectedTab.value = 1 // Go to cart
            } else {
                // If items couldn't be parsed well, just open the restaurant page directly
                _selectedTab.value = 0
            }
        } else {
            _selectedTab.value = 0
        }
    }

    fun setProfile(profile: UserProfile) {
        _currentProfile.value = profile
    }

    fun setCheckoutModeIsManual(isManual: Boolean) {
        _isManualMode.value = isManual
    }

    fun claimOrderManual(orderId: Int, driverId: String, driverName: String) {
        viewModelScope.launch {
            repository.claimOrder(orderId, driverId, driverName, "OUT_FOR_DELIVERY")
            val currentActive = _activeOrder.value
            if (currentActive != null && currentActive.id == orderId) {
                _activeOrder.value = currentActive.copy(status = "OUT_FOR_DELIVERY", driverId = driverId, driverName = driverName)
            } else {
                repository.getOrderById(orderId)?.let {
                    _activeOrder.value = it
                }
            }
            if (_isManualMode.value) {
                updateTrackingStateManual("OUT_FOR_DELIVERY")
            }
        }
    }

    fun setPayoutSchedule(schedule: String) {
        _payoutSchedule.value = schedule
    }

    fun savePaynowCredentials(integrationId: String, integrationKey: String) {
        viewModelScope.launch {
            val firestoreService = FirestoreService()
            val current = firestoreService.getAdminSettings() ?: FirestoreAdminSettings()
            firestoreService.updateAdminSettings(
                current.copy(
                    paynowIntegrationId = integrationId.trim(),
                    paynowIntegrationKey = integrationKey.trim()
                )
            )
        }
    }

    fun triggerPayoutSettlement() {
        viewModelScope.launch {
            _isPayoutInProgress.value = true
            delay(2500)
            repository.markCompletedOrdersAsSettled()
            _isPayoutInProgress.value = false
        }
    }

    fun updateOrderStatusManual(orderId: Int, newStatus: String) {
        viewModelScope.launch {
            repository.updateOrderStatus(orderId, newStatus)
            updateActiveOrderLocalState(orderId, newStatus)
            if (_isManualMode.value) {
                updateTrackingStateManual(newStatus)
            }
        }
    }

    fun updateTrackingStateManual(status: String) {
        _trackingStatusText.value = when (status) {
            "PENDING_ACCEPTANCE" -> "Waiting for Restaurant to Accept..."
            "ACCEPTED" -> "Restaurant accepted your order! Starting on it shortly..."
            "PREPARING" -> "Kitchen preparing your freshly cooked meal..."
            "READY_FOR_PICKUP" -> "Meal is ready! Waiting for rider pickup..."
            "OUT_FOR_DELIVERY" -> "Rider in transit down Samora Machel Avenue..."
            "COMPLETED" -> "Meal successfully delivered! Savor BiteDash meal."
            else -> "Processing..."
        }
        _trackingProgress.value = when (status) {
            "PENDING_ACCEPTANCE" -> 0.0f
            "ACCEPTED" -> 0.10f
            "PREPARING" -> 0.25f
            "READY_FOR_PICKUP" -> 0.50f
            "OUT_FOR_DELIVERY" -> 0.75f
            "COMPLETED" -> 1.0f
            else -> 0.0f
        }
    }

    override fun onCleared() {
        super.onCleared()
        trackingJob?.cancel()
    }
}
