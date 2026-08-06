package com.example.data.firebase

import com.example.data.entity.DriverEntity
import com.example.data.entity.RestaurantEntity
import com.example.model.MenuItem

/**
 * Conversions between Room entities (local cache) and Firestore models
 * (source of truth). Used by BiteDashViewModel to keep the local database
 * in sync with the shared cloud database, so every device sees the same
 * restaurants and drivers instead of each device having its own private,
 * disconnected copy.
 */

fun RestaurantEntity.toFirestoreRestaurant(): FirestoreRestaurant {
    return FirestoreRestaurant(
        id = id,
        name = name,
        description = description,
        rating = rating,
        deliveryTime = deliveryTime,
        deliveryFee = deliveryFee,
        category = category,
        location = location,
        imageKeyword = imageKeyword,
        displayOrder = displayOrder,
        ownerUserId = ownerUserId,
        staffEmails = staffEmails,
        isApproved = isApproved,
        menuItemIds = menuItems.map { it.id },
        isActive = true
    )
}

fun MenuItem.toFirestoreMenuItem(restaurantId: String): FirestoreMenuItem {
    return FirestoreMenuItem(
        id = id,
        restaurantId = restaurantId,
        name = name,
        description = description,
        price = price,
        category = category
    )
}

fun FirestoreMenuItem.toMenuItem(): MenuItem {
    return MenuItem(
        id = id,
        name = name,
        description = description,
        price = price,
        category = category
    )
}

// menuItems must be supplied separately since Firestore stores them in a
// sibling subcollection, not embedded on the restaurant document itself.
fun FirestoreRestaurant.toRoomEntity(menuItems: List<MenuItem>): RestaurantEntity {
    return RestaurantEntity(
        id = id,
        name = name,
        description = description,
        rating = rating,
        deliveryTime = deliveryTime,
        deliveryFee = deliveryFee,
        category = category,
        location = location,
        imageKeyword = imageKeyword,
        displayOrder = displayOrder,
        ownerUserId = ownerUserId,
        staffEmails = staffEmails,
        isApproved = isApproved,
        menuItems = menuItems
    )
}

fun DriverEntity.toFirestoreDriver(): FirestoreDriver {
    return FirestoreDriver(
        id = id,
        name = name,
        phone = phone,
        vehicle = vehicle,
        isAvailable = isAvailable,
        isActive = true
    )
}

fun FirestoreDriver.toRoomEntity(): DriverEntity {
    return DriverEntity(
        id = id,
        name = name,
        phone = phone,
        vehicle = vehicle,
        isAvailable = isAvailable
    )
}
