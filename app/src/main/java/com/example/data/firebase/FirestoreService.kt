package com.example.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/**
 * BiteDash Firestore Service
 * Handles all Firebase Firestore operations for the BiteDash food delivery app.
 * 
 * Collections:
 * - restaurants: Restaurant documents
 * - drivers: Driver documents
 * - orders: Order documents
 * - transactions: Transaction audit records
 * - admin_settings: Platform configuration
 */
class FirestoreService {
    private val db = FirebaseFirestore.getInstance()

    companion object {
        const val COLLECTION_RESTAURANTS = "restaurants"
        const val COLLECTION_DRIVERS = "drivers"
        const val COLLECTION_ORDERS = "orders"
        const val COLLECTION_TRANSACTIONS = "transactions"
        const val COLLECTION_ADMIN_SETTINGS = "admin_settings"
    }

    // ==================== RESTAURANT OPERATIONS ====================
    
    /**
     * Get all active restaurants as a Flow for real-time updates
     */
    fun getRestaurantsFlow(): Flow<List<FirestoreRestaurant>> {
        return db.collection(COLLECTION_RESTAURANTS)
            .whereEqualTo("isActive", true)
            .orderBy("displayOrder", Query.Direction.ASCENDING)
            .snapshots()
            .let { flow ->
                kotlinx.coroutines.flow.map(flow) { snapshot ->
                    snapshot.toObjects(FirestoreRestaurant::class.java)
                }
            }
    }

    /**
     * Get a single restaurant by ID
     */
    suspend fun getRestaurant(restaurantId: String): FirestoreRestaurant? {
        return try {
            db.collection(COLLECTION_RESTAURANTS)
                .document(restaurantId)
                .get()
                .await()
                .toObject(FirestoreRestaurant::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Add a new restaurant
     */
    suspend fun addRestaurant(restaurant: FirestoreRestaurant): String? {
        return try {
            val docRef = db.collection(COLLECTION_RESTAURANTS)
                .add(restaurant)
                .await()
            docRef.id
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Update an existing restaurant
     */
    suspend fun updateRestaurant(restaurant: FirestoreRestaurant): Boolean {
        return try {
            db.collection(COLLECTION_RESTAURANTS)
                .document(restaurant.id)
                .set(restaurant)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Update restaurant menu items
     */
    suspend fun updateRestaurantMenu(restaurantId: String, menuItems: List<FirestoreMenuItem>): Boolean {
        return try {
            db.collection(COLLECTION_RESTAURANTS)
                .document(restaurantId)
                .update("menuItems", menuItems)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==================== DRIVER OPERATIONS ====================

    /**
     * Get all active drivers as a Flow
     */
    fun getDriversFlow(): Flow<List<FirestoreDriver>> {
        return db.collection(COLLECTION_DRIVERS)
            .whereEqualTo("isActive", true)
            .snapshots()
            .let { flow ->
                kotlinx.coroutines.flow.map(flow) { snapshot ->
                    snapshot.toObjects(FirestoreDriver::class.java)
                }
            }
    }

    /**
     * Add a new driver
     */
    suspend fun addDriver(driver: FirestoreDriver): String? {
        return try {
            val docRef = db.collection(COLLECTION_DRIVERS)
                .add(driver)
                .await()
            docRef.id
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Update driver info
     */
    suspend fun updateDriver(driver: FirestoreDriver): Boolean {
        return try {
            db.collection(COLLECTION_DRIVERS)
                .document(driver.id)
                .set(driver)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Update driver payout info (EcoCash, OneMoney numbers)
     */
    suspend fun updateDriverPayoutInfo(
        driverId: String,
        ecoCashNumber: String,
        oneMoneyNumber: String
    ): Boolean {
        return try {
            db.collection(COLLECTION_DRIVERS)
                .document(driverId)
                .update(
                    mapOf(
                        "ecoCashNumber" to ecoCashNumber,
                        "oneMoneyNumber" to oneMoneyNumber
                    )
                )
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==================== ORDER OPERATIONS ====================

    /**
     * Get all orders as a Flow
     */
    fun getOrdersFlow(): Flow<List<FirestoreOrder>> {
        return db.collection(COLLECTION_ORDERS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .let { flow ->
                kotlinx.coroutines.flow.map(flow) { snapshot ->
                    snapshot.toObjects(FirestoreOrder::class.java)
                }
            }
    }

    /**
     * Get orders for a specific restaurant
     */
    fun getRestaurantOrdersFlow(restaurantId: String): Flow<List<FirestoreOrder>> {
        return db.collection(COLLECTION_ORDERS)
            .whereEqualTo("restaurantId", restaurantId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .let { flow ->
                kotlinx.coroutines.flow.map(flow) { snapshot ->
                    snapshot.toObjects(FirestoreOrder::class.java)
                }
            }
    }

    /**
     * Get orders for a specific driver
     */
    fun getDriverOrdersFlow(driverId: String): Flow<List<FirestoreOrder>> {
        return db.collection(COLLECTION_ORDERS)
            .whereEqualTo("driverId", driverId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .let { flow ->
                kotlinx.coroutines.flow.map(flow) { snapshot ->
                    snapshot.toObjects(FirestoreOrder::class.java)
                }
            }
    }

    /**
     * Create a new order
     */
    suspend fun createOrder(order: FirestoreOrder): String? {
        return try {
            val docRef = db.collection(COLLECTION_ORDERS)
                .add(order)
                .await()
            docRef.id
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Update order status
     */
    suspend fun updateOrderStatus(orderId: String, status: String): Boolean {
        return try {
            db.collection(COLLECTION_ORDERS)
                .document(orderId)
                .update(
                    mapOf(
                        "status" to status,
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )
                )
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Assign driver to order
     */
    suspend fun assignDriverToOrder(orderId: String, driverId: String, driverName: String): Boolean {
        return try {
            db.collection(COLLECTION_ORDERS)
                .document(orderId)
                .update(
                    mapOf(
                        "driverId" to driverId,
                        "driverName" to driverName
                    )
                )
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Mark order as settled (for payout tracking)
     */
    suspend fun markOrderSettled(orderId: String): Boolean {
        return try {
            db.collection(COLLECTION_ORDERS)
                .document(orderId)
                .update("isSettled", true)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==================== TRANSACTION OPERATIONS ====================

    /**
     * Record a new transaction
     */
    suspend fun recordTransaction(transaction: FirestoreTransaction): String? {
        return try {
            val docRef = db.collection(COLLECTION_TRANSACTIONS)
                .add(transaction)
                .await()
            docRef.id
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get unsettled transactions for payout processing
     */
    fun getUnsettledTransactionsFlow(): Flow<List<FirestoreTransaction>> {
        return db.collection(COLLECTION_TRANSACTIONS)
            .whereEqualTo("status", "COMPLETED")
            .whereEqualTo("isSettled", false)
            .snapshots()
            .let { flow ->
                kotlinx.coroutines.flow.map(flow) { snapshot ->
                    snapshot.toObjects(FirestoreTransaction::class.java)
                }
            }
    }

    // ==================== ADMIN SETTINGS ====================

    /**
     * Get admin settings
     */
    suspend fun getAdminSettings(): FirestoreAdminSettings? {
        return try {
            db.collection(COLLECTION_ADMIN_SETTINGS)
                .document("admin_settings")
                .get()
                .await()
                .toObject(FirestoreAdminSettings::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Update admin settings
     */
    suspend fun updateAdminSettings(settings: FirestoreAdminSettings): Boolean {
        return try {
            db.collection(COLLECTION_ADMIN_SETTINGS)
                .document("admin_settings")
                .set(settings)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==================== BATCH OPERATIONS ====================

    /**
     * Mark all completed orders as settled (payout settlement)
     */
    suspend fun settleCompletedOrders(): Int {
        return try {
            val completedOrders = db.collection(COLLECTION_ORDERS)
                .whereEqualTo("status", "COMPLETED")
                .whereEqualTo("isSettled", false)
                .get()
                .await()

            var count = 0
            for (document in completedOrders) {
                document.reference.update("isSettled", true)
                count++
            }
            count
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Seed initial data (for first-time setup)
     */
    suspend fun seedInitialData(
        restaurants: List<FirestoreRestaurant>,
        drivers: List<FirestoreDriver>
    ): Boolean {
        return try {
            // Seed restaurants
            for (restaurant in restaurants) {
                db.collection(COLLECTION_RESTAURANTS)
                    .document(restaurant.id)
                    .set(restaurant)
                    .await()
            }
            
            // Seed drivers
            for (driver in drivers) {
                db.collection(COLLECTION_DRIVERS)
                    .document(driver.id)
                    .set(driver)
                    .await()
            }
            
            // Initialize admin settings
            db.collection(COLLECTION_ADMIN_SETTINGS)
                .document("admin_settings")
                .set(FirestoreAdminSettings())
                .await()
            
            true
        } catch (e: Exception) {
            false
        }
    }
}
