package com.example.data.repository

import com.example.data.dao.RestaurantDao
import com.example.data.entity.RestaurantEntity
import kotlinx.coroutines.flow.Flow

class RestaurantRepository(private val restaurantDao: RestaurantDao) {
    val allRestaurants: Flow<List<RestaurantEntity>> = restaurantDao.getAllRestaurants()

    suspend fun insertRestaurant(restaurant: RestaurantEntity) {
        restaurantDao.insertRestaurant(restaurant)
    }

    suspend fun insertRestaurants(restaurants: List<RestaurantEntity>) {
        restaurantDao.insertRestaurants(restaurants)
    }

    suspend fun deleteRestaurantById(id: String) {
        restaurantDao.deleteRestaurantById(id)
    }

    suspend fun getCount(): Int {
        return restaurantDao.getCount()
    }
}
