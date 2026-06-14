package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.MenuItem
import com.example.model.Restaurant

@Entity(tableName = "restaurants")
data class RestaurantEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val rating: Double,
    val deliveryTime: String,
    val deliveryFee: Double,
    val category: String,
    val location: String,
    val menuItems: List<MenuItem>,
    val imageKeyword: String,
    val displayOrder: Int = 0,
    val ownerUsername: String = "owner",
    val ownerPassword: String = "password"
) {
    fun toDomain(): Restaurant = Restaurant(
        id = id,
        name = name,
        description = description,
        rating = rating,
        deliveryTime = deliveryTime,
        deliveryFee = deliveryFee,
        category = category,
        location = location,
        menuItems = menuItems,
        imageKeyword = imageKeyword,
        displayOrder = displayOrder,
        ownerUsername = ownerUsername,
        ownerPassword = ownerPassword
    )
}

fun Restaurant.toEntity(): RestaurantEntity = RestaurantEntity(
    id = id,
    name = name,
    description = description,
    rating = rating,
    deliveryTime = deliveryTime,
    deliveryFee = deliveryFee,
    category = category,
    location = location,
    menuItems = menuItems,
    imageKeyword = imageKeyword,
    displayOrder = displayOrder,
    ownerUsername = ownerUsername,
    ownerPassword = ownerPassword
)
