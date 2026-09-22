package com.example.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

// snapshot.toObjects() is all-or-nothing: a single document whose field types
// don't match the model (e.g. a number saved as a string from the web admin,
// another client, or a manual Firebase Console edit) throws and kills the
// ENTIRE listener, freezing the whole list at whatever is in the local cache
// while the web admin keeps showing everything. Converting document-by-document
// confines the damage to the one bad document.
private inline fun <reified T : Any> DocumentSnapshot.toObjectOrNull(collection: String): T? {
    return try {
        toObject(T::class.java)
    } catch (e: Exception) {
        android.util.Log.e(
            "BiteDashSync",
            "Skipping unreadable $collection document $id: ${e.message}",
            e
        )
        null
    }
}

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

    // The business's own EcoCash/OneMoney/InnBucks/Telecash numbers, shown to a customer
    // paying manually so they know where to send the money. Public read (any signed-in
    // user), admin-only write — see firestore.rules match /public_settings/{settingsId}.
    // Keys match the app's payment-method labels ("EcoCash", "OneMoney", ...).
    suspend fun getPublicPaymentNumbers(): Map<String, String> {
        val snapshot = db.collection("public_settings").document("payment").get().await()
        @Suppress("UNCHECKED_CAST")
        return (snapshot.data as? Map<String, String>) ?: emptyMap()
    }

    suspend fun setPublicPaymentNumbers(numbers: Map<String, String>): Boolean {
        return try {
            db.collection("public_settings").document("payment").set(numbers).await()
            true
        } catch (e: Exception) {
            false
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
        // No orderBy here: the local Room cache already sorts by displayOrder.
        // Combining an equality filter on isActive with orderBy on displayOrder
        // requires a composite Firestore index — when that index doesn't exist
        // the listener fails outright (FAILED_PRECONDITION) and never delivers
        // anything, and orderBy additionally drops any document missing the
        // field. A single-field filter needs no composite index.
        return db.collection(COLLECTION_RESTAURANTS)
            .whereEqualTo("isActive", true)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObjectOrNull<FirestoreRestaurant>(COLLECTION_RESTAURANTS)
                }
            }
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
        // No orderBy here (see getRestaurantsFlow): equality filters alone only
        // need the built-in single-field indexes. Callers sort by category.
        return db.collection(COLLECTION_MENU_ITEMS)
            .whereEqualTo("restaurantId", restaurantId)
            .whereEqualTo("isAvailable", true)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObjectOrNull<FirestoreMenuItem>(COLLECTION_MENU_ITEMS)
                }
            }
    }

    // Unlike getMenuItemsFlow, includes sold-out (isAvailable == false)
    // items too — for the restaurant owner's own "Manage Menu" screen,
    // where a sold-out item needs to stay visible so it can be toggled
    // back on, not disappear the moment it's marked unavailable.
    fun getAllMenuItemsFlow(restaurantId: String): Flow<List<FirestoreMenuItem>> {
        return db.collection(COLLECTION_MENU_ITEMS)
            .whereEqualTo("restaurantId", restaurantId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObjectOrNull<FirestoreMenuItem>(COLLECTION_MENU_ITEMS)
                }
            }
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

    // A fresh document id for a menu item that doesn't exist yet, so the
    // caller knows the item's real id before (and regardless of whether) the
    // write succeeds.
    fun newMenuItemId(): String = db.collection(COLLECTION_MENU_ITEMS).document().id

    // Writes one menu item to the document with its own id: updates it in
    // place if it exists, creates it if it doesn't, and never creates a
    // second document for the same item. Only the fields the owner edits in
    // "Manage Menu" are written, merged, so imageUrl, preparationTime and
    // createdAt on an existing item are left alone.
    suspend fun saveMenuItem(restaurantId: String, menuItem: FirestoreMenuItem, isNew: Boolean): Boolean {
        return try {
            val fields = mutableMapOf<String, Any>(
                "restaurantId" to restaurantId,
                "name" to menuItem.name,
                "description" to menuItem.description,
                "price" to menuItem.price,
                "category" to menuItem.category,
                "isAvailable" to menuItem.isAvailable,
                "updatedAt" to Timestamp.now()
            )
            if (isNew) {
                fields["imageUrl"] = menuItem.imageUrl
                fields["preparationTime"] = menuItem.preparationTime
                fields["createdAt"] = Timestamp.now()
            }
            db.collection(COLLECTION_MENU_ITEMS)
                .document(menuItem.id)
                .set(fields, SetOptions.merge())
                .await()
            if (isNew) {
                val restaurant = getRestaurant(restaurantId)
                if (restaurant != null && menuItem.id !in restaurant.menuItemIds) {
                    updateRestaurantField(
                        restaurantId,
                        "menuItemIds",
                        restaurant.menuItemIds + menuItem.id
                    )
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    // Permanently removes a menu item and drops its id from the restaurant's
    // menuItemIds. Sold-out is a separate state (isAvailable == false) and
    // must never be used to mean "deleted".
    suspend fun deleteMenuItem(menuItemId: String, restaurantId: String): Boolean {
        return try {
            db.collection(COLLECTION_MENU_ITEMS)
                .document(menuItemId)
                .delete()
                .await()
            val restaurant = getRestaurant(restaurantId)
            if (restaurant != null && menuItemId in restaurant.menuItemIds) {
                updateRestaurantField(
                    restaurantId,
                    "menuItemIds",
                    restaurant.menuItemIds - menuItemId
                )
            }
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

    // Same as getOrdersFlowByUser but unordered: that one filters on userId and
    // sorts on createdAt, which needs a composite index that firestore.indexes.json
    // doesn't declare, so it fails with FAILED_PRECONDITION. An equality filter
    // alone only needs automatic indexes, and callers that just want the current
    // status of each order don't care about the order.
    fun getUserOrderUpdatesFlow(userId: String): Flow<List<FirestoreOrder>> {
        return db.collection(COLLECTION_ORDERS)
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(FirestoreOrder::class.java) }
    }

    fun getRestaurantOrdersFlow(restaurantId: String): Flow<List<FirestoreOrder>> {
        // Sorting done client-side rather than via orderBy() — a filter on
        // one field plus orderBy on a different field can require a
        // composite index depending on how Firestore evaluates it, and
        // this avoids that dependency entirely, matching the same
        // defensive pattern already used for getActiveOrdersFlow().
        return db.collection(COLLECTION_ORDERS)
            .whereEqualTo("restaurantId", restaurantId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(FirestoreOrder::class.java)
                    .sortedByDescending { it.createdAt?.seconds ?: 0 }
            }
    }


    fun getAvailableDeliveriesFlow(): Flow<List<FirestoreOrder>> {
        return db.collection(COLLECTION_ORDERS)
            .whereEqualTo("deliveryStatus", "UNASSIGNED")
            .snapshots()
            .map { snapshot -> snapshot.toObjects(FirestoreOrder::class.java) }
    }

    fun getDriverDeliveriesFlow(driverId: String): Flow<List<FirestoreOrder>> {
        return db.collection(COLLECTION_ORDERS)
            .whereEqualTo("driverId", driverId)
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
        // Firestore only allows one inequality (!=) filter per query — two
        // whereNotEqualTo() calls on the same field throws
        // IllegalArgumentException at query-build time, synchronously,
        // before any data is even fetched. whereNotIn expresses the same
        // "status is neither of these" condition as a single valid clause.
        //
        // Sorting is done client-side rather than via orderBy(), since
        // Firestore requires an orderBy field used alongside whereNotIn to
        // match the filtered field (or a composite index) — sorting here
        // avoids that requirement and gives a more useful newest-first
        // order than sorting by status would.
        return db.collection(COLLECTION_ORDERS)
            .whereNotIn("status", listOf("COMPLETED", "CANCELLED"))
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(FirestoreOrder::class.java)
                    .sortedByDescending { it.createdAt?.seconds ?: 0 }
            }
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

    // Admin has checked the manual mobile-money transfer actually arrived — this is what
    // finally lets the restaurant see the order (see getRestaurantOrdersFlow's filter).
    suspend fun confirmManualPayment(orderId: String): Boolean {
        return try {
            db.collection(COLLECTION_ORDERS).document(orderId).update(
                mapOf(
                    "paymentStatus" to "PAID",
                    "updatedAt" to Timestamp.now()
                )
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // No matching transfer was found. Also cancels the order (status, not just
    // paymentStatus) so it drops off both the admin's and the restaurant's active lists —
    // otherwise it would sit forever as neither payable nor actionable.
    suspend fun rejectManualPayment(orderId: String): Boolean {
        return try {
            db.collection(COLLECTION_ORDERS).document(orderId).update(
                mapOf(
                    "paymentStatus" to "FAILED",
                    "status" to "CANCELLED",
                    "updatedAt" to Timestamp.now()
                )
            ).await()
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

                // Calculate payouts now that the order is fulfilled.
                // Restaurant keeps (subtotal - platform commission).
                // Driver keeps the full delivery fee plus any tip.
                // Admin's platformFeePercent is configurable in Admin Settings (default 10%).
                val order = getOrder(orderId)
                if (order != null) {
                    val settings = getAdminSettings()
                    val feePercent = settings?.platformFeePercent ?: 10.0
                    val platformFee = order.subtotal * (feePercent / 100.0)
                    val restaurantPayout = order.subtotal - platformFee
                    val driverPayout = order.deliveryFee + order.driverTip

                    updates["platformFee"] = platformFee
                    updates["restaurantPayoutAmount"] = restaurantPayout
                    updates["driverPayoutAmount"] = driverPayout
                    updates["isSettled"] = false
                }
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
                        "deliveryStatus" to "ASSIGNED",
                        "updatedAt" to Timestamp.now()
                    )
                )
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateDeliveryStatus(orderId: String, deliveryStatus: String): Boolean {
        return try {
            db.collection(COLLECTION_ORDERS)
                .document(orderId)
                .update(
                    mapOf(
                        "deliveryStatus" to deliveryStatus,
                        "updatedAt" to Timestamp.now()
                    )
                )
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Mark a delivery as complete: deliveryStatus -> DELIVERED AND
     * status -> COMPLETED, in the one update a driver's firestore.rules
     * grant actually allows (hasOnly ['status','deliveryStatus',
     * 'updatedAt','completedAt']).
     *
     * Deliberately does NOT compute payout amounts here (unlike
     * updateOrderStatus's COMPLETED branch) — a driver's own device isn't
     * trusted to calculate what it and the restaurant get paid, the same
     * reason a client can't self-report a Paynow payment as PAID. Payout
     * calculation needs its own admin/Cloud-Function-driven path; until
     * that exists, completed orders are visible everywhere but stay
     * unsettled (isSettled stays at its default false).
     */
    suspend fun completeDelivery(orderId: String): Boolean {
        return try {
            val now = Timestamp.now()
            db.collection(COLLECTION_ORDERS)
                .document(orderId)
                .update(
                    mapOf(
                        "deliveryStatus" to "DELIVERED",
                        "status" to "COMPLETED",
                        "completedAt" to now,
                        "updatedAt" to now
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
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObjectOrNull<FirestoreDriver>(COLLECTION_DRIVERS)
                }
            }
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

    // Self-registration: the driver's own account creates their profile,
    // using their Firebase UID as the document ID so firestore.rules can
    // verify ownership without a separate lookup field.
    suspend fun registerDriverSelf(uid: String, driver: FirestoreDriver): Boolean {
        return try {
            db.collection(COLLECTION_DRIVERS)
                .document(uid)
                .set(driver.copy(userId = uid))
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateDriverField(driverId: String, field: String, value: Any): Boolean {
        return try {
            db.collection(COLLECTION_DRIVERS)
                .document(driverId)
                .update(field, value, "updatedAt", Timestamp.now())
                .await()
            true
        } catch (e: Exception) {
            false
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

    suspend fun deleteDriver(driverId: String): Boolean {
        return try {
            db.collection(COLLECTION_DRIVERS)
                .document(driverId)
                .update("isActive", false, "updatedAt", Timestamp.now())
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

    suspend fun getUnsettledCompletedOrders(): List<FirestoreOrder> {
        return try {
            db.collection(COLLECTION_ORDERS)
                .whereEqualTo("status", "COMPLETED")
                .whereEqualTo("isSettled", false)
                .get()
                .await()
                .toObjects(FirestoreOrder::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun settleRestaurantPayout(restaurantId: String): Int {
        return try {
            val orders = db.collection(COLLECTION_ORDERS)
                .whereEqualTo("status", "COMPLETED")
                .whereEqualTo("isSettled", false)
                .whereEqualTo("restaurantId", restaurantId)
                .get()
                .await()

            var count = 0
            for (document in orders) {
                document.reference.update("restaurantSettled", true)
                maybeFullySettle(document.reference)
                count++
            }
            count
        } catch (e: Exception) {
            0
        }
    }

    suspend fun settleDriverPayout(driverId: String): Int {
        return try {
            val orders = db.collection(COLLECTION_ORDERS)
                .whereEqualTo("status", "COMPLETED")
                .whereEqualTo("isSettled", false)
                .whereEqualTo("driverId", driverId)
                .get()
                .await()

            var count = 0
            for (document in orders) {
                document.reference.update("driverSettled", true)
                maybeFullySettle(document.reference)
                count++
            }
            count
        } catch (e: Exception) {
            0
        }
    }

    private suspend fun maybeFullySettle(docRef: com.google.firebase.firestore.DocumentReference) {
        val snapshot = docRef.get().await()
        val restaurantSettled = snapshot.getBoolean("restaurantSettled") ?: false
        val driverSettled = snapshot.getBoolean("driverSettled") ?: false
        val hasDriver = !snapshot.getString("driverId").isNullOrBlank()
        // An order is fully settled once its restaurant portion is paid,
        // and its driver portion is paid too (if the order even had a driver).
        if (restaurantSettled && (driverSettled || !hasDriver)) {
            docRef.update("isSettled", true)
        }
    }

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
