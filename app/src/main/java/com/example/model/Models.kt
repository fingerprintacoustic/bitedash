package com.example.model

data class MenuItem(
    val id: String,
    val name: String,
    val description: String,
    val price: Double, // in USD
    val category: String
)

data class Restaurant(
    val id: String,
    val name: String,
    val description: String,
    val rating: Double,
    val deliveryTime: String, // e.g. "15-25 min"
    val deliveryFee: Double, // in USD
    val category: String, // "Fast Food", "Traditional", "Pizza & Grills", "Cafes & Drinks"
    val location: String, // e.g. "Belgravia", "Avondale", "Harare CBD"
    val menuItems: List<MenuItem>,
    val imageKeyword: String, // Used to decide background colors or graphics if icons aren't available
    val displayOrder: Int = 0
)

data class CartItem(
    val menuItem: MenuItem,
    var quantity: Int
)
