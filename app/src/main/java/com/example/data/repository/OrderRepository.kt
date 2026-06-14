package com.example.data.repository

import com.example.data.dao.OrderDao
import com.example.data.entity.OrderEntity
import kotlinx.coroutines.flow.Flow

class OrderRepository(private val orderDao: OrderDao) {
    val allOrders: Flow<List<OrderEntity>> = orderDao.getAllOrders()
    val activeOrders: Flow<List<OrderEntity>> = orderDao.getActiveOrders()

    suspend fun insertOrder(order: OrderEntity): Long {
        return orderDao.insertOrder(order)
    }

    suspend fun updateOrder(order: OrderEntity) {
        orderDao.updateOrder(order)
    }

    suspend fun updateOrderStatus(orderId: Int, status: String) {
        orderDao.updateOrderStatus(orderId, status)
    }

    suspend fun claimOrder(orderId: Int, driverId: Int, driverName: String, status: String) {
        orderDao.claimOrder(orderId, driverId, driverName, status)
    }

    suspend fun markCompletedOrdersAsSettled() {
        orderDao.markCompletedOrdersAsSettled()
    }

    suspend fun getOrderById(orderId: Int): OrderEntity? {
        return orderDao.getOrderById(orderId)
    }
}
