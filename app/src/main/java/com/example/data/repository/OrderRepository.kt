package com.example.data.repository

import com.example.data.dao.OrderDao
import com.example.data.entity.OrderEntity
import kotlinx.coroutines.flow.Flow

class OrderRepository(private val orderDao: OrderDao) {
    fun allOrders(userId: String): Flow<List<OrderEntity>> = orderDao.getAllOrders(userId)
    fun activeOrders(userId: String): Flow<List<OrderEntity>> = orderDao.getActiveOrders(userId)

    suspend fun deleteOrdersNotOwnedBy(userId: String) = orderDao.deleteOrdersNotOwnedBy(userId)

    suspend fun deleteAll() = orderDao.deleteAll()

    suspend fun updatePaymentStatus(orderId: Int, paymentStatus: String) =
        orderDao.updatePaymentStatus(orderId, paymentStatus)

    suspend fun insertOrder(order: OrderEntity): Long {
        return orderDao.insertOrder(order)
    }

    suspend fun updateOrder(order: OrderEntity) {
        orderDao.updateOrder(order)
    }

    suspend fun updateOrderStatus(orderId: Int, status: String) {
        orderDao.updateOrderStatus(orderId, status)
    }

    suspend fun claimOrder(orderId: Int, driverId: String, driverName: String, status: String) {
        orderDao.claimOrder(orderId, driverId, driverName, status)
    }

    suspend fun markCompletedOrdersAsSettled() {
        orderDao.markCompletedOrdersAsSettled()
    }

    suspend fun getOrderById(orderId: Int): OrderEntity? {
        return orderDao.getOrderById(orderId)
    }

    suspend fun getOrderByFirestoreId(firestoreOrderId: String): OrderEntity? {
        return orderDao.getOrderByFirestoreId(firestoreOrderId)
    }
}
