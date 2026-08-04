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

    // Replaces the entire local table with the given list — used when
    // syncing down from Firestore, so deletions/updates on other devices
    // are correctly reflected here too, not just additions.
    suspend fun replaceAll(restaurants: List<RestaurantEntity>) {
        restaurantDao.deleteAll()
        restaurantDao.insertRestaurants(restaurants)
    }

    suspend fun getCount(): Int {
        return restaurantDao.getCount()
    }
}
