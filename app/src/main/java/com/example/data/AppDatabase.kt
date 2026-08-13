package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.dao.OrderDao
import com.example.data.dao.RestaurantDao
import com.example.data.dao.DriverDao
import com.example.data.entity.OrderEntity
import com.example.data.entity.RestaurantEntity
import com.example.data.entity.DriverEntity

// version bumped 7 -> 8: RestaurantEntity gained ownerUserId, staffEmails,
// and isApproved across several commits after version was last set here.
// Room computes a schema "identity hash" from the entity classes at build
// time; when that changes without a matching version bump, Room can't tell
// it needs to migrate — it just finds a mismatch between what it expects
// for "version 7" and what's actually on disk, and throws
// IllegalStateException("Room cannot verify the data integrity...") on
// every single app open, before any UI even renders. This is what caused
// the crash-within-seconds reported by multiple testers.
// fallbackToDestructiveMigration() (below) means this bump is enough on
// its own — no explicit Migration needed, Room just rebuilds the local
// cache fresh, which is fine since it only ever mirrors Firestore anyway.
@Database(entities = [OrderEntity::class, RestaurantEntity::class, DriverEntity::class], version = 8, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
    abstract fun restaurantDao(): RestaurantDao
    abstract fun driverDao(): DriverDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bitedash_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
