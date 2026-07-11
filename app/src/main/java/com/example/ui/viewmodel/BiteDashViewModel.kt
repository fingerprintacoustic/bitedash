package com.example.ui.viewmodel

import android.app.Application 
import com.example.BuildConfig
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.entity.OrderEntity
import com.example.data.entity.RestaurantEntity
import com.example.data.entity.DriverEntity
import com.example.data.entity.toEntity
import com.example.data.repository.OrderRepository
import com.example.data.repository.RestaurantRepository
import com.example.data.repository.DriverRepository
import com.example.data.firebase.FirestoreAdminSettings
import com.example.data.firebase.FirestoreService
import com.example.model.CartItem
import com.example.model.MenuItem
import com.example.model.Restaurant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PaymentStep {
    object Idle : PaymentStep
    object SendingPush : PaymentStep
    object WaitingForHandsetPin : PaymentStep
    object ProcessingConfirmation : PaymentStep
    data class Success(val transactionRef: String) : PaymentStep
    data class Error(val message: String) : PaymentStep
}

sealed interface UserProfile {
    object Idle : UserProfile
    object Customer : UserProfile
    data class RestaurantOwner(
        val restaurantId: String, 
        val restaurantName: String,
        val firebaseUid: String = "" // Firebase Auth UID when authenticated
    ) : UserProfile
    data class Driver(
        val driverId: Int, 
        val driverName: String,
        val firebaseUid: String = "" // Firebase Auth UID when authenticated
    ) : UserProfile
    data class Admin(
        val firebaseUid: String = "" // Firebase Auth UID when authenticated
    ) : UserProfile
    
    fun getAuthUid(): String? = when (this) {
        is Idle -> null
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
    private var trackingJob: Job? = null

    // Fallback static mock list used strictly as seed data on empty DB boot
    private val defaultRestaurants = listOf(
        Restaurant(
            id = "chicken_inn_belgravia",
            name = "Chicken Inn (Belgravia)",
            description = "Zimbabwe's favorite flame-grilled chicken, burgers, and hand-cut chips.",
            rating = 4.7,
            deliveryTime = "15-25 min",
            deliveryFee = 2.00,
            category = "Fast Food",
            location = "Belgravia, Harare",
            imageKeyword = "chicken",
            displayOrder = 0,
            menuItems = listOf(
                MenuItem("ci_1", "2-Piece & Chips", "Slightly spiced crispy 2-piece chicken with gold standard chips.", 3.50, "Mains"),
                MenuItem("ci_2", "Simbisa Flame-Grilled Quarter", "Flame-grilled succulent quarter chicken in lemon/herb or piri-piri.", 4.00, "Mains"),
                MenuItem("ci_3", "Chicken Inn Spicy Wings (6 Pack)", "Crispy breaded wings tossed in hot chilli shaker seasoning.", 4.50, "Sides"),
                MenuItem("ci_4", "Family Feast (8-Piece & Mega Chips)", "Perfect family bucket of 8 crispy chicken pieces + premium mega chips.", 13.50, "Feasts"),
                MenuItem("ci_5", "Classic Chicken Burger", "Tender breast fillet with crisp lettuce and Simbisa spread.", 3.50, "Mains")
            )
        ),
        Restaurant(
            id = "garwe_traditional",
            name = "Garwe Traditional Restaurant",
            description = "Savor authentic Zimbabwean heritage. High-quality traditional dishes in Eastlea.",
            rating = 4.9,
            deliveryTime = "25-35 min",
            deliveryFee = 3.50,
            category = "Traditional",
            location = "Eastlea, Harare",
            imageKeyword = "sadza",
            displayOrder = 1,
            menuItems = listOf(
                MenuItem("gw_1", "Sadza with Road Runner Chicken", "Authentic hard-body Zimbabwean free-range village chicken in rich domestic gravy with sadza and rape greens.", 6.50, "Traditional"),
                MenuItem("gw_2", "Sadza with Beef Stew & Covo", "Savory slow-cooked Zimbabwean beef stew served with fine white sadza, covo greens, and peanut butter sauce.", 5.00, "Traditional"),
                MenuItem("gw_3", "Madora (Crispy Mopane Worms)", "Traditional pan-fried mopane worms seasoned with salt, onions, and black pepper. Rich in protein!", 4.00, "Snacks"),
                MenuItem("gw_4", "Mupunga weDovi & Rich Oxtail", "Traditional peanut butter rice paired with premium tender slow-simmered oxtail.", 8.50, "Premium"),
                MenuItem("gw_5", "Mazondo (Beef Trotters)", "Gently gelled beef trotters slow cooked for 6 hours in full spicy traditional stock.", 7.00, "Traditional")
            )
        ),
        Restaurant(
            id = "pizza_inn_avondale",
            name = "Pizza Inn (Avondale)",
            description = "Hot, fresh pizza using premium dough and 100% mozzarella cheese.",
            rating = 4.6,
            deliveryTime = "20-30 min",
            deliveryFee = 2.50,
            category = "Pizza & Grills",
            location = "Avondale Shopping Centre, Harare",
            imageKeyword = "pizza",
            displayOrder = 2,
            menuItems = listOf(
                MenuItem("pi_1", "Simbisa BBQ Chicken Pizza (Medium)", "Sweet BBQ sauce base, roasted chicken breast, and red onions.", 7.50, "Pizzas"),
                MenuItem("pi_2", "Pepperoni Feast Pizza (Medium)", "Loads of traditional pepperoni slices loaded with standard mozzarella.", 8.00, "Pizzas"),
                MenuItem("pi_3", "Mega Meat Deluxe (Large)", "Salami, beef, pepperoni, visual bacon crumbs, and rich pizza sauce.", 11.50, "Pizzas"),
                MenuItem("pi_4", "Garlic Cheese Bread", "Freshly baked pizza bread with garlic herb spread and mozzarella.", 3.00, "Sides"),
                MenuItem("pi_5", "Sweet Chilli Chicken Pizza", "Sweet chilli swirl, tender chicken breast, and green peppers.", 8.00, "Pizzas")
            )
        ),
        Restaurant(
            id = "zambezi_cafe",
            name = "Zambezi Cafe",
            description = "Vibrant local eatery blending coffee, game grills, and Victoria Falls tea brews.",
            rating = 4.5,
            deliveryTime = "20-30 min",
            deliveryFee = 3.00,
            category = "Cafes & Drinks",
            location = "Harare CBD, Central",
            imageKeyword = "cafe",
            displayOrder = 3,
            menuItems = listOf(
                MenuItem("zc_1", "Kariba Pan-Fried Bream & Wedges", "Freshly caught Zambezi / Kariba bream fillet with rustic hand-cut wedges.", 9.00, "Mains"),
                MenuItem("zc_2", "Victoria Falls Sunset Latte", "Dual layer espresso with whipped vanilla, spiced nutmeg, and honey.", 3.50, "Drinks"),
                MenuItem("zc_3", "Chimanimani Mountain Herbal Tea", "Organic wild rooibos infusion direct from the scenic Chimanimani hills.", 2.50, "Drinks"),
                MenuItem("zc_4", "Ginger Sadza Scones (3-Pack)", "Unique fusion scones made with white maize flour and fresh ginger molasses.", 3.00, "Bakery")
            )
        )
    )

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

// Seeding mock entries if database is pristine — DEBUG builds only.
// Production installs must start empty; real restaurants/drivers are
// added via sign-up, not fake placeholder data.
if (BuildConfig.DEBUG) {
    viewModelScope.launch {
        if (restaurantRepo.getCount() == 0) {
            restaurantRepo.insertRestaurants(defaultRestaurants.map { it.toEntity() })
        }
        if (driverRepo.getCount() == 0) {
            val seedDrivers = listOf(
                DriverEntity(name = "Tinashe", phone = "0771234567", vehicle = "Motorbike"),
                DriverEntity(name = "Chipo", phone = "0783214569", vehicle = "Bicycle"),
                DriverEntity(name = "Farai", phone = "0711556677", vehicle = "Honda Fit"),
                DriverEntity(name = "Nyasha", phone = "0734889900", vehicle = "Motorbike")
            )
            driverRepo.insertDrivers(seedDrivers)
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
            restaurantRepo.insertRestaurant(restaurant.copy(displayOrder = maxOrder + 1).toEntity())
        }
    }

    fun removeRestaurant(restaurantId: String) {
        viewModelScope.launch {
            restaurantRepo.deleteRestaurantById(restaurantId)
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
            
            val newCurrentOrder = if (currentOrder == aboveOrder) aboveOrder else aboveOrder
            val newAboveOrder = if (currentOrder == aboveOrder) aboveOrder + 1 else currentOrder
            
            viewModelScope.launch {
                restaurantRepo.insertRestaurant(current.copy(displayOrder = newCurrentOrder).toEntity())
                restaurantRepo.insertRestaurant(above.copy(displayOrder = newAboveOrder).toEntity())
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
                restaurantRepo.insertRestaurant(current.copy(displayOrder = newCurrentOrder).toEntity())
                restaurantRepo.insertRestaurant(below.copy(displayOrder = newBelowOrder).toEntity())
            }
        }
    }

    fun updateRestaurantMenu(restaurantId: String, updatedMenuItems: List<MenuItem>) {
        viewModelScope.launch {
            val existing = restaurantsState.value.find { it.id == restaurantId }
            if (existing != null) {
                val updated = existing.copy(menuItems = updatedMenuItems)
                restaurantRepo.insertRestaurant(updated.toEntity())
                if (_selectedRestaurant.value?.id == restaurantId) {
                    _selectedRestaurant.value = updated
                }
            }
        }
    }

    fun addDriver(name: String, phone: String, vehicle: String) {
        viewModelScope.launch {
            driverRepo.insertDriver(DriverEntity(name = name, phone = phone, vehicle = vehicle))
        }
    }

    fun removeDriver(id: Int) {
        viewModelScope.launch {
            driverRepo.deleteDriverById(id)
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
            "OneMoney" -> if (!_phoneInput.value.startsWith("073")) _phoneInput.value = "073"
            "O'Mari" -> if (!_phoneInput.value.startsWith("077") && !_phoneInput.value.startsWith("078")) _phoneInput.value = "077"
            "Telecash" -> if (!_phoneInput.value.startsWith("071")) _phoneInput.value = "071"
            "ZIPIT" -> if (!_phoneInput.value.startsWith("07")) _phoneInput.value = "07"
            else -> if (!_phoneInput.value.startsWith("07")) _phoneInput.value = "07"
        }
    }

    fun setPhoneInput(phone: String) {
        _phoneInput.value = phone
    }

    // Places order & launches USSD prompt / OTP simulation
    fun processCheckout() {
        val cartItems = _cart.value
        val restaurant = _selectedRestaurant.value ?: return
        if (cartItems.isEmpty()) return

        val paymentPhone = _phoneInput.value
        val method = _checkoutMethod.value
        val sum = getCartTotal()

        // Simple validation
        if (paymentPhone.length < 9) {
            _paymentStep.value = PaymentStep.Error("Please enter a valid Zimbabwean mobile money number.")
            return
        }

        viewModelScope.launch {
            // STEP 1: Sending prompt
            _paymentStep.value = PaymentStep.SendingPush
            delay(1500)

            // STEP 2: Wait for handset PIN simulation
            _paymentStep.value = PaymentStep.WaitingForHandsetPin
            delay(3000)

            // STEP 3: Confirming payment with carrier
            _paymentStep.value = PaymentStep.ProcessingConfirmation
            delay(1500)

            // Create transaction reference
            val randomLetters = ('A'..'Z').map { it }.shuffled().take(4).joinToString("")
            val timestampSec = System.currentTimeMillis() % 100000
            val ref = "BD-${method.uppercase().take(3)}-$randomLetters-$timestampSec"

            // Save to DB
            val itemsSummaryStr = cartItems.joinToString(", ") { "${it.menuItem.name} x${it.quantity}" }
            val initialStatus = if (_isManualMode.value) "PENDING_ACCEPTANCE" else "PREPARING"
            val newOrder = OrderEntity(
                restaurantName = restaurant.name,
                itemsSummary = itemsSummaryStr,
                totalCost = sum,
                paymentMethod = method,
                paymentPhone = paymentPhone,
                status = initialStatus,
                driverTip = _driverTip.value
            )

            val orderId = repository.insertOrder(newOrder)
            val insertedOrder = newOrder.copy(id = orderId.toInt())

            _paymentStep.value = PaymentStep.Success(ref)
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

    fun claimOrderManual(orderId: Int, driverId: Int, driverName: String) {
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
            "PREPARING" -> "Kitchen preparing your freshly cooked meal..."
            "READY_FOR_PICKUP" -> "Meal is ready! Waiting for rider pickup..."
            "OUT_FOR_DELIVERY" -> "Rider in transit down Samora Machel Avenue..."
            "COMPLETED" -> "Meal successfully delivered! Savor BiteDash meal."
            else -> "Processing..."
        }
        _trackingProgress.value = when (status) {
            "PENDING_ACCEPTANCE" -> 0.0f
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
