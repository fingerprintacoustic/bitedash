package com.example.model

// Prefix of the temporary id the "Manage Menu" editor gives an item the owner
// has just added. Items loaded from Firestore carry their real document id
// instead, so this prefix is how a save tells "not written yet" from "already
// exists".
const val NEW_MENU_ITEM_ID_PREFIX = "item_"

data class MenuItem(
    val id: String,
    val name: String,
    val description: String,
    val price: Double, // in USD
    val category: String,
    val isAvailable: Boolean = true // false = "Sold Out" — hidden from customers, still visible/editable by the owner
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
    val displayOrder: Int = 0,
    val ownerUserId: String = "", // Firebase Auth UID of the account that owns this restaurant
    val staffEmails: List<String> = emptyList(), // Extra accounts (by email) the owner has granted dashboard access to
    val isApproved: Boolean = true // false = pending admin review, shown to customers as "Coming Soon"
)

data class CartItem(
    val menuItem: MenuItem,
    var quantity: Int
)
