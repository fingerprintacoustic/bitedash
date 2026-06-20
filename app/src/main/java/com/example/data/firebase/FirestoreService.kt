package com.example.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * BiteDash Firestore Service
 * Handles all Firebase Firestore operations for the BiteDash food delivery app.
 * 
 * Collections:
 * - users: User profiles
 * - restaurants: Restaurant information
 * - menu_items: Standalone menu items
 * - carts: User shopping carts
 * - orders: Order records
 * - payments: Payment transaction records
 * - drivers: Driver profiles
 * - transactions: Transaction audit records
 * - admin_settings: Platform configuration
 */
class FirestoreService {
    private val db = FirebaseFirestore.getInstance()

    companion object {
        const val COLLECTION_USERS = "users"
        const val COLLECTION_RESTAURANTS = "restaurants"
        const val COLLECTION_MENU_ITEMS = "menu_items"
        const val COLLECTION_CARTS = "carts"
        const val COLLECTION_ORDERS = "orders"
        const val COLLECTION_PAYMENTS = "payments"
        const val COLLECTION_DRIVERS = "drivers"
        const val COLLECTION_TRANSACTIONS = "transactions"
        const val COLLECTION_ADMIN_SETTINGS = "admin_settings"
    }

    // ==================== USER OPERATIONS ====================

    fun getUsersFlow(): Flow<List<FirestoreUser>> {
        return db.collection(COLLECTION_USERS)
            .whereEqualTo("isActive", true)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(FirestoreUser::class.java) }
    }

    fun getUsersFlowByRole(role: String): Flow<List<FirestoreUser>> {
        return db.collection(COLLECTION_USERS)
            .whereEqualTo("role", role)
            .whereEqualTo("isActive", true)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(FirestoreUser::class.java) }
    }

    suspend fun getUser(userId: String): FirestoreUser? {
        return try {
            db.collection(COLLECTION_USERS)
                .document(userId)
                .get()
                .await()
                .toObject(FirestoreUser::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getUserByEmail(email: String): FirestoreUser? {
        return try {
            val querySnapshot = db.collection(COLLECTION_USERS)
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .await()
            
            if (!querySnapshot.isEmpty) {
                querySnapshot.documents.firstOrNull()?.toObject(FirestoreUser::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getUserByPhone(phone: String): FirestoreUser? {
        return try {
            val querySnapshot = db.collection(COLLECTION_USERS)
                .whereEqualTo("phone", phone)
                .limit(1)
                .get()
                .await()
            
            if (!querySnapshot.isEmpty) {
                querySnapshot.documents.firstOrNull()?.toObject(FirestoreUser::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createUser(user: FirestoreUser): String? {
        return try {
            val docRef = db.collection(COLLECTION_USERS).add(user).await()
            docRef.id
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateUser(user: FirestoreUser): Boolean {
        return try {
            db.collection(COLLECTION_USERS).document(user.id).set(user).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateUserField(userId: String, field: String, value: Any): Boolean {
        return try {
            db.collection(COLLECTION_USERS)
                .document(userId)
                .update(field, value, "updatedAt", Timestamp.now())
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==================== RESTAURANT OPERATIONS ====================

    fun getRestaurantsFlow(): Flow<List<FirestoreRestaurant>> {
        return db.collection(COLLECTION_RESTAURANTS)
            .whereEqualTo("isActive", true)
            .orderBy("displayOrder", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(FirestoreRestaurant::class.java) }
    }

    fun getRestaurantsFlowByCategory(category: String): Flow<List<FirestoreRestaurant>> {
        return db.collection(COLLECTION_RESTAURANTS)
            .whereEqualTo("category", category)
            .whereEqualTo("isActive", true)
            .orderBy("displayOrder", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(FirestoreRestaurant::class.java) }
    }

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

    suspend fun createRestaurant(restaurant: FirestoreRestaurant): String? {
        return try {
            val docRef = db.collection(COLLECTION_RESTAURANTS).add(restaurant).await()
            docRef.id
        } catch (e: Exception) {
            null
        }
    }

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

    suspend fun updateRestaurantField(restaurantId: String, field: String, value: Any): Boolean {
        return try {
            db.collection(COLLECTION_RESTAURANTS)
                .document(restaurantId)
                .update(field, value, "updatedAt", Timestamp.now())
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteRestaurant(restaurantId: String): Boolean {
        return try {
            db.collection(COLLECTION_RESTAURANTS)
                .document(restaurantId)
                .update("isActive", false, "updatedAt", Timestamp.now())
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==================== MENU ITEM OPERATIONS ====================

    fun getMenuItemsFlow(restaurantId: String): Flow<List<FirestoreMenuItem>> {
        return db.collection(COLLECTION_MENU_ITEMS)
            .whereEqualTo("restaurantId", restaurantId)
            .whereEqualTo("isAvailable", true)
            .orderBy("category")
            .snapshots()
            .map { snapshot -> snapshot.toObjects(FirestoreMenuItem::class.java) }
    }

    fun getMenuItemsFlowByCategory(category: String): Flow<List<FirestoreMenuItem>> {
        return db.collection(COLLECTION_MENU_ITEMS)
            .whereEqualTo("category", category)
            .whereEqualTo("isAvailable", true)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(FirestoreMenuItem::class.java) }
    }

    suspend fun getMenuItem(menuItemId: String): FirestoreMenuItem? {
        return try {
            db.collection(COLLECTION_MENU_ITEMS)
                .document(menuItemId)
                .get()
                .await()
                .toObject(FirestoreMenuItem::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createMenuItem(menuItem: FirestoreMenuItem): String? {
        return try {
            val docRef = db.collection(COLLECTION_MENU_ITEMS).add(menuItem).await()
            // Update restaurant's menuItemIds
            val restaurant = getRestaurant(menuItem.restaurantId)
            if (restaurant != null) {
                updateRestaurantField(
                    menuItem.restaurantId,
                    "menuItemIds",
                    restaurant.menuItemIds + docRef.id
                )
            }
            docRef.id
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateMenuItem(menuItem: FirestoreMenuItem): Boolean {
        return try {
            db.collection(COLLECTION_MENU_ITEMS)
                .document(menuItem.id)
                .set(menuItem)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateMenuItemField(menuItemId: String, field: String, value: Any): Boolean {
        return try {
            db.collection(COLLECTION_MENU_ITEMS)
                .document(menuItemId)
                .update(field, value, "updatedAt", Timestamp.now())
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==================== CART OPERATIONS ====================

    fun getCartFlow(userId: String): Flow<FirestoreCart?> {
        return db.collection(COLLECTION_CARTS)
            .whereEqualTo("userId", userId)
            .limit(1)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.firstOrNull()?.toObject(FirestoreCart::class.java)
            }
    }

    suspend fun getCart(userId: String): FirestoreCart? {
        return try {
            val snapshot = db.collection(COLLECTION_CARTS)
                .whereEqualTo("userId", userId)
                .limit(1)
                .get()
                .await()
            snapshot.documents.firstOrNull()?.toObject(FirestoreCart::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveCart(cart: FirestoreCart): String? {
        return try {
            // Check if cart exists for user
            val existingCart = getCart(cart.userId)
            if (existingCart != null) {
                // Update existing cart
                db.collection(COLLECTION_CARTS)
                    .document(existingCart.id)
                    .set(cart.copy(id = existingCart.id))
                    .await()
                existingCart.id
            } else {
                // Create new cart
                val docRef = db.collection(COLLECTION_CARTS).add(cart).await()
                docRef.id
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun clearCart(userId: String): Boolean {
        return try {
            val cart = getCart(userId)
            if (cart != null) {
                db.collection(COLLECTION_CARTS)
                    .document(cart.id)
                    .delete()
                    .await()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==================== ORDER OPERATIONS ====================

    fun getOrdersFlow(): Flow<List<FirestoreOrder>> {
        return db.collection(COLLECTION_ORDERS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(FirestoreOrder::class.java) }
    }

    fun getOrdersFlowByUser(userId: String): Flow<List<FirestoreOrder>> {
        return db.collection(COLLECTION_ORDERS)
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(FirestoreOrder::class.java) }
    }

    fun getRestaurantOrdersFlow(restaurantId: String): Flow<List<FirestoreOrder>> {
        return db.collection(COLLECTION_ORDERS)
            .whereEqualTo("restaurantId", restaurantId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(FirestoreOrder::class.java) }
    }

    fun getDriverOrdersFlow(driverId: String): Flow<List<FirestoreOrder>> {
        return db.collection(COLLECTION_ORDERS)
            .whereEqualTo("driverId", driverId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(FirestoreOrder::class.java) }
    }

    fun getActiveOrdersFlow(): Flow<List<FirestoreOrder>> {
        return db.collection(COLLECTION_ORDERS)
            .whereNotEqualTo("status", "COMPLETED")
            .whereNotEqualTo("status", "CANCELLED")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(FirestoreOrder::class.java) }
    }

    suspend fun getOrder(orderId: String): FirestoreOrder? {
        return try {
            db.collection(COLLECTION_ORDERS)
                .document(orderId)
                .get()
                .await()
                .toObject(FirestoreOrder::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createOrder(order: FirestoreOrder): String? {
        return try {
            val docRef = db.collection(COLLECTION_ORDERS).add(order).await()
            docRef.id
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateOrder(order: FirestoreOrder): Boolean {
        return try {
            db.collection(COLLECTION_ORDERS)
                .document(order.id)
                .set(order)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateOrderStatus(orderId: String, status: String): Boolean {
        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to status,
                "updatedAt" to Timestamp.now()
            )
            if (status == "ACCEPTED") {
                updates["acceptedAt"] = Timestamp.now()
            } else if (status == "COMPLETED") {
                updates["completedAt"] = Timestamp.now()
            }
            db.collection(COLLECTION_ORDERS)
                .document(orderId)
                .update(updates)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun assignDriverToOrder(orderId: String, driverId: String, driverName: String): Boolean {
        return try {
            db.collection(COLLECTION_ORDERS)
                .document(orderId)
                .update(
                    mapOf(
                        "driverId" to driverId,
                        "driverName" to driverName,
                        "updatedAt" to Timestamp.now()
                    )
                )
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

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

    // ==================== PAYMENT OPERATIONS ====================

    fun getPaymentsFlow(orderId: String): Flow<List<FirestorePayment>> {
        return db.collection(COLLECTION_PAYMENTS)
            .whereEqualTo("orderId", orderId)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(FirestorePayment::class.java) }
    }

    fun getPaymentsFlowByUser(userId: String): Flow<List<FirestorePayment>> {
        return db.collection(COLLECTION_PAYMENTS)
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(FirestorePayment::class.java) }
    }

    suspend fun createPayment(payment: FirestorePayment): String? {
        return try {
            val docRef = db.collection(COLLECTION_PAYMENTS).add(payment).await()
            docRef.id
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updatePaymentStatus(paymentId: String, status: String, transactionRef: String = ""): Boolean {
        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to status,
                "updatedAt" to Timestamp.now()
            )
            if (transactionRef.isNotEmpty()) {
                updates["transactionRef"] = transactionRef
            }
            if (status == "COMPLETED") {
                updates["completedAt"] = Timestamp.now()
            } else if (status == "FAILED") {
                updates["failureReason"] = "Payment failed"
            }
            db.collection(COLLECTION_PAYMENTS)
                .document(paymentId)
                .update(updates)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==================== DRIVER OPERATIONS ====================

    fun getDriversFlow(): Flow<List<FirestoreDriver>> {
        return db.collection(COLLECTION_DRIVERS)
            .whereEqualTo("isActive", true)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(FirestoreDriver::class.java) }
    }

    fun getAvailableDriversFlow(): Flow<List<FirestoreDriver>> {
        return db.collection(COLLECTION_DRIVERS)
            .whereEqualTo("isActive", true)
            .whereEqualTo("isAvailable", true)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(FirestoreDriver::class.java) }
    }

    suspend fun getDriver(driverId: String): FirestoreDriver? {
        return try {
            db.collection(COLLECTION_DRIVERS)
                .document(driverId)
                .get()
                .await()
                .toObject(FirestoreDriver::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createDriver(driver: FirestoreDriver): String? {
        return try {
            val docRef = db.collection(COLLECTION_DRIVERS).add(driver).await()
            docRef.id
        } catch (e: Exception) {
            null
        }
    }

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

    suspend fun updateDriverAvailability(driverId: String, isAvailable: Boolean): Boolean {
        return try {
            db.collection(COLLECTION_DRIVERS)
                .document(driverId)
                .update(
                    mapOf(
                        "isAvailable" to isAvailable,
                        "updatedAt" to Timestamp.now()
                    )
                )
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateDriverLocation(driverId: String, latitude: Double, longitude: Double): Boolean {
        return try {
            db.collection(COLLECTION_DRIVERS)
                .document(driverId)
                .update(
                    mapOf(
                        "currentLatitude" to latitude,
                        "currentLongitude" to longitude,
                        "lastLocationUpdate" to Timestamp.now()
                    )
                )
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

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
                        "oneMoneyNumber" to oneMoneyNumber,
                        "updatedAt" to Timestamp.now()
                    )
                )
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==================== TRANSACTION OPERATIONS ====================

    suspend fun recordTransaction(transaction: FirestoreTransaction): String? {
        return try {
            val docRef = db.collection(COLLECTION_TRANSACTIONS).add(transaction).await()
            docRef.id
        } catch (e: Exception) {
            null
        }
    }

    fun getTransactionsFlow(): Flow<List<FirestoreTransaction>> {
        return db.collection(COLLECTION_TRANSACTIONS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(FirestoreTransaction::class.java) }
    }

    // ==================== ADMIN SETTINGS ====================

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

    suspend fun seedInitialData(
        restaurants: List<FirestoreRestaurant>,
        menuItems: List<FirestoreMenuItem>,
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

            // Seed menu items
            for (menuItem in menuItems) {
                db.collection(COLLECTION_MENU_ITEMS)
                    .document(menuItem.id)
                    .set(menuItem)
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
