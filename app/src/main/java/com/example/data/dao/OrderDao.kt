package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.OrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    // Always scoped to one account: the phone may be shared, and the cache outlives sign-out.
    @Query("SELECT * FROM orders WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllOrders(userId: String): Flow<List<OrderEntity>>

    // REJECTED / CANCELLED are as finished as COMPLETED — otherwise an order the
    // restaurant turned down would sit in "active tracking" forever.
    @Query("SELECT * FROM orders WHERE userId = :userId AND status NOT IN ('COMPLETED', 'REJECTED', 'CANCELLED') ORDER BY timestamp DESC")
    fun getActiveOrders(userId: String): Flow<List<OrderEntity>>

    // Drops every other account's orders from this phone once someone else signs in.
    @Query("DELETE FROM orders WHERE userId != :userId")
    suspend fun deleteOrdersNotOwnedBy(userId: String)

    @Query("DELETE FROM orders")
    suspend fun deleteAll()

    @Query("UPDATE orders SET paymentStatus = :paymentStatus WHERE id = :orderId")
    suspend fun updatePaymentStatus(orderId: Int, paymentStatus: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity): Long

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Query("UPDATE orders SET status = :status WHERE id = :orderId")
    suspend fun updateOrderStatus(orderId: Int, status: String)

    @Query("UPDATE orders SET status = :status, driverId = :driverId, driverName = :driverName WHERE id = :orderId")
    suspend fun claimOrder(orderId: Int, driverId: String, driverName: String, status: String)

    @Query("UPDATE orders SET isSettled = 1 WHERE status = 'COMPLETED' AND isSettled = 0")
    suspend fun markCompletedOrdersAsSettled()

    @Query("SELECT * FROM orders WHERE id = :orderId")
    suspend fun getOrderById(orderId: Int): OrderEntity?

    @Query("SELECT * FROM orders WHERE firestoreOrderId = :firestoreOrderId LIMIT 1")
    suspend fun getOrderByFirestoreId(firestoreOrderId: String): OrderEntity?
}
