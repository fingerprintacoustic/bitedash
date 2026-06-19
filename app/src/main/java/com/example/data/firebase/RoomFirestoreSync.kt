package com.example.data.firebase

import com.example.data.dao.DriverDao
import com.example.data.dao.OrderDao
import com.example.data.dao.RestaurantDao
import com.example.data.entity.DriverEntity
import com.example.data.entity.OrderEntity
import com.example.data.entity.RestaurantEntity
import com.example.data.entity.toEntity
import com.example.model.MenuItem
import com.example.model.Restaurant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Room-Firestore Synchronization Service
 * 
 * This service maintains consistency between the local Room database and Firebase Firestore.
 * - On app startup: Seeds Room from Firestore (if Firestore has data) or vice versa
 * - On data changes: Keeps both databases in sync
 * 
 * This allows the app to work offline with Room while syncing to Firestore for
 * multi-device support and real-time updates.
 */
class RoomFirestoreSync(
    private val firestoreService: FirestoreService,
    private val restaurantDao: RestaurantDao,
    private val driverDao: DriverDao,
    private val orderDao: OrderDao
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        // Sync modes
        const val SYNC_MODE_FIRESTORE_TO_ROOM = "firestore_to_room" // Prefer cloud data
        const val SYNC_MODE_ROOM_TO_FIRESTORE = "room_to_firestore" // Prefer local data
        const val SYNC_MODE_MERGE = "merge" // Merge both, Firestore wins on conflict
    }

    // ==================== INITIALIZATION ====================

    /**
     * Initialize sync - run on app startup
     * Checks both databases and syncs as needed
     */
    fun initializeSync(mode: String = SYNC_MODE_MERGE) {
        scope.launch {
            try {
                when (mode) {
                    SYNC_MODE_FIRESTORE_TO_ROOM -> syncFromCloud()
                    SYNC_MODE_ROOM_TO_FIRESTORE -> syncToCloud()
                    SYNC_MODE_MERGE -> mergeData()
                }
            } catch (e: Exception) {
                // Log error but don't crash - app can work with local data
                e.printStackTrace()
            }
        }
    }

    /**
     * Sync from Firestore to Room (cloud-first approach)
     */
    private suspend fun syncFromCloud() {
        // Check if Firestore has data
        val firestoreRestaurants = firestoreService.getRestaurantsFlow().first()
        val firestoreDrivers = firestoreService.getDriversFlow().first()

        if (firestoreRestaurants.isNotEmpty()) {
            // Firestore has data - use it
            syncRestaurantsToRoom(firestoreRestaurants)
        } else {
            // Firestore is empty - sync from Room to cloud
            val roomRestaurants = restaurantDao.getAllRestaurants().first()
            if (roomRestaurants.isNotEmpty()) {
                syncRestaurantsToFirestore(roomRestaurants)
            }
        }

        if (firestoreDrivers.isNotEmpty()) {
            syncDriversToRoom(firestoreDrivers)
        } else {
            val roomDrivers = driverDao.getAllDrivers().first()
            if (roomDrivers.isNotEmpty()) {
                syncDriversToFirestore(roomDrivers)
            }
        }
    }

    /**
     * Sync from Room to Firestore (local-first approach)
     */
    private suspend fun syncToCloud() {
        val roomRestaurants = restaurantDao.getAllRestaurants().first()
        if (roomRestaurants.isNotEmpty()) {
            syncRestaurantsToFirestore(roomRestaurants)
        }

        val roomDrivers = driverDao.getAllDrivers().first()
        if (roomDrivers.isNotEmpty()) {
            syncDriversToFirestore(roomDrivers)
        }
    }

    /**
     * Merge data from both sources
     */
    private suspend fun mergeData() {
        // For restaurants - prefer Firestore data, but fill gaps from Room
        val firestoreRestaurants = firestoreService.getRestaurantsFlow().first()
        val roomRestaurants = restaurantDao.getAllRestaurants().first()

        if (firestoreRestaurants.isEmpty() && roomRestaurants.isEmpty()) {
            // Both empty - nothing to do
            return
        }

        if (firestoreRestaurants.isNotEmpty()) {
            // Use Firestore as source of truth for restaurants
            syncRestaurantsToRoom(firestoreRestaurants)
        } else if (roomRestaurants.isNotEmpty()) {
            // No cloud data - push local to cloud
            syncRestaurantsToFirestore(roomRestaurants)
        }

        // Same approach for drivers
        val firestoreDrivers = firestoreService.getDriversFlow().first()
        val roomDrivers = driverDao.getAllDrivers().first()

        if (firestoreDrivers.isNotEmpty()) {
            syncDriversToRoom(firestoreDrivers)
        } else if (roomDrivers.isNotEmpty()) {
            syncDriversToFirestore(roomDrivers)
        }
    }

    // ==================== RESTAURANT SYNC ====================

    /**
     * Sync restaurants from Room to Firestore
     */
    suspend fun syncRestaurantsToFirestore(restaurants: List<RestaurantEntity>) {
        for (entity in restaurants) {
            val firestoreRestaurant = entity.toFirestoreRestaurant()
            firestoreService.createRestaurant(firestoreRestaurant)
        }
    }

    /**
     * Sync restaurants from Firestore to Room
     */
    private suspend fun syncRestaurantsToRoom(firestoreRestaurants: List<FirestoreRestaurant>) {
        val entities = firestoreRestaurants.map { it.toRoomEntity() }
        restaurantDao.insertRestaurants(entities)
    }

    /**
     * Add a new restaurant - syncs to both databases
     */
    suspend fun addRestaurantSync(restaurant: Restaurant): String? {
        // Add to Room
        val roomEntity = restaurant.toEntity()
        restaurantDao.insertRestaurant(roomEntity)

        // Add to Firestore
        val firestoreId = firestoreService.createRestaurant(roomEntity.toFirestoreRestaurant())

        return firestoreId ?: roomEntity.id
    }

    /**
     * Update restaurant - syncs to both databases
     */
    suspend fun updateRestaurantSync(restaurant: Restaurant): Boolean {
        // Update Room
        restaurantDao.insertRestaurant(restaurant.toEntity())

        // Update Firestore
        return firestoreService.updateRestaurant(restaurant.toEntity().toFirestoreRestaurant())
    }

    // ==================== DRIVER SYNC ====================

    /**
     * Sync drivers from Room to Firestore
     */
    suspend fun syncDriversToFirestore(drivers: List<DriverEntity>) {
        for (entity in drivers) {
            val firestoreDriver = entity.toFirestoreDriver()
            firestoreService.createDriver(firestoreDriver)
        }
    }

    /**
     * Sync drivers from Firestore to Room
     */
    private suspend fun syncDriversToRoom(firestoreDrivers: List<FirestoreDriver>) {
        val entities = firestoreDrivers.map { it.toRoomEntity() }
        driverDao.insertDrivers(entities)
    }

    /**
     * Add a new driver - syncs to both databases
     */
    suspend fun addDriverSync(name: String, phone: String, vehicle: String): Int? {
        // Add to Room
        val roomDriver = DriverEntity(
            name = name,
            phone = phone,
            vehicle = vehicle
        )
        driverDao.insertDriver(roomDriver)
        val roomId = roomDriver.id

        // Add to Firestore
        firestoreService.createDriver(
            FirestoreDriver(
                id = "driver_$roomId",
                name = name,
                phone = phone,
                vehicle = vehicle
            )
        )

        return roomId
    }

    // ==================== ORDER SYNC ====================

    /**
     * Create order - syncs to both databases
     */
    suspend fun createOrderSync(order: OrderEntity): Long {
        // Add to Room (primary for orders)
        val orderId = orderDao.insertOrder(order)

        // Add to Firestore
        firestoreService.createOrder(order.toFirestoreOrder(orderId.toString()))

        return orderId
    }

    /**
     * Update order status - syncs to both databases
     */
    suspend fun updateOrderStatusSync(orderId: Int, status: String): Boolean {
        // Update Room
        orderDao.updateOrderStatus(orderId, status)

        // Update Firestore
        return firestoreService.updateOrderStatus("order_$orderId", status)
    }

    /**
     * Claim order for driver - syncs to both databases
     */
    suspend fun claimOrderSync(orderId: Int, driverId: Int, driverName: String): Boolean {
        // Update Room
        orderDao.claimOrder(orderId, driverId, driverName, "OUT_FOR_DELIVERY")

        // Update Firestore
        return firestoreService.assignDriverToOrder("order_$orderId", "driver_$driverId", driverName)
    }

    /**
     * Mark completed orders as settled - syncs to both databases
     */
    suspend fun settleOrdersSync(): Int {
        // Mark in Room
        orderDao.markCompletedOrdersAsSettled()

        // Mark in Firestore
        return firestoreService.settleCompletedOrders()
    }

    // ==================== REAL-TIME SYNC (WATCHERS) ====================

    /**
     * Start watching Firestore for restaurant changes
     * Updates Room when Firestore changes
     */
    fun watchFirestoreRestaurants(onComplete: () -> Unit = {}) {
        scope.launch {
            firestoreService.getRestaurantsFlow().collect { restaurants ->
                syncRestaurantsToRoom(restaurants)
            }
            onComplete()
        }
    }

    /**
     * Start watching Firestore for driver changes
     * Updates Room when Firestore changes
     */
    fun watchFirestoreDrivers(onComplete: () -> Unit = {}) {
        scope.launch {
            firestoreService.getDriversFlow().collect { drivers ->
                syncDriversToRoom(drivers)
            }
            onComplete()
        }
    }

    // ==================== DATA CONVERTERS ====================

    /**
     * Convert Room RestaurantEntity to Firestore FirestoreRestaurant
     */
    private fun RestaurantEntity.toFirestoreRestaurant(): FirestoreRestaurant {
        return FirestoreRestaurant(
            id = id,
            name = name,
            description = description,
            rating = rating,
            deliveryTime = deliveryTime,
            deliveryFee = deliveryFee,
            category = category,
            location = location,
            imageKeyword = imageKeyword,
            displayOrder = displayOrder,
            ownerUsername = ownerUsername,
            ownerPassword = ownerPassword,
            menuItems = menuItems.map { it.toFirestoreMenuItem() },
            isActive = true
        )
    }

    /**
     * Convert Room MenuItem to Firestore FirestoreMenuItem
     */
    private fun MenuItem.toFirestoreMenuItem(): FirestoreMenuItem {
        return FirestoreMenuItem(
            id = id,
            name = name,
            description = description,
            price = price,
            category = category
        )
    }

    /**
     * Convert Firestore FirestoreRestaurant to Room RestaurantEntity
     */
    private fun FirestoreRestaurant.toRoomEntity(): RestaurantEntity {
        return RestaurantEntity(
            id = id,
            name = name,
            description = description,
            rating = rating,
            deliveryTime = deliveryTime,
            deliveryFee = deliveryFee,
            category = category,
            location = location,
            imageKeyword = imageKeyword,
            displayOrder = displayOrder,
            ownerUsername = ownerUsername,
            ownerPassword = ownerPassword,
            menuItems = menuItems.map { it.toMenuItem() }
        )
    }

    /**
     * Convert Firestore FirestoreMenuItem to Room MenuItem
     */
    private fun FirestoreMenuItem.toMenuItem(): MenuItem {
        return MenuItem(
            id = id,
            name = name,
            description = description,
            price = price,
            category = category
        )
    }

    /**
     * Convert Room DriverEntity to Firestore FirestoreDriver
     */
    private fun DriverEntity.toFirestoreDriver(): FirestoreDriver {
        return FirestoreDriver(
            id = "driver_$id",
            name = name,
            phone = phone,
            vehicle = vehicle,
            isAvailable = isAvailable,
            isActive = true
        )
    }

    /**
     * Convert Firestore FirestoreDriver to Room DriverEntity
     */
    private fun FirestoreDriver.toRoomEntity(): DriverEntity {
        // Extract numeric ID from Firestore ID (e.g., "driver_123" -> 123)
        val numericId = id.removePrefix("driver_").toIntOrNull() ?: 0
        return DriverEntity(
            id = numericId,
            name = name,
            phone = phone,
            vehicle = vehicle,
            isAvailable = isAvailable
        )
    }

    /**
     * Convert Room OrderEntity to Firestore FirestoreOrder
     */
    private fun OrderEntity.toFirestoreOrder(firestoreId: String): FirestoreOrder {
        return FirestoreOrder(
            id = firestoreId,
            restaurantName = restaurantName,
            itemsSummary = itemsSummary,
            totalCost = totalCost,
            deliveryFee = deliveryFee,
            driverTip = driverTip,
            status = status,
            driverId = driverId?.toString(),
            driverName = driverName,
            isSettled = isSettled,
            paymentMethod = paymentMethod,
            paymentRef = paymentPhone
        )
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Force full sync from cloud to local
     */
    fun forceRefreshFromCloud() {
        scope.launch {
            try {
                val firestoreRestaurants = firestoreService.getRestaurantsFlow().first()
                val firestoreDrivers = firestoreService.getDriversFlow().first()

                syncRestaurantsToRoom(firestoreRestaurants)
                syncDriversToRoom(firestoreDrivers)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Force full sync from local to cloud
     */
    fun forcePushToCloud() {
        scope.launch {
            try {
                val roomRestaurants = restaurantDao.getAllRestaurants().first()
                val roomDrivers = driverDao.getAllDrivers().first()

                for (restaurant in roomRestaurants) {
                    firestoreService.updateRestaurant(restaurant.toFirestoreRestaurant())
                }

                for (driver in roomDrivers) {
                    firestoreService.updateDriver(driver.toFirestoreDriver())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
