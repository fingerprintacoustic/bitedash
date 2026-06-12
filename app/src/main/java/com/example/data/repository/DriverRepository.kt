package com.example.data.repository

import com.example.data.dao.DriverDao
import com.example.data.entity.DriverEntity
import kotlinx.coroutines.flow.Flow

class DriverRepository(private val driverDao: DriverDao) {
    val allDrivers: Flow<List<DriverEntity>> = driverDao.getAllDrivers()

    suspend fun insertDriver(driver: DriverEntity) {
        driverDao.insertDriver(driver)
    }

    suspend fun insertDrivers(drivers: List<DriverEntity>) {
        driverDao.insertDrivers(drivers)
    }

    suspend fun deleteDriverById(id: Int) {
        driverDao.deleteDriverById(id)
    }

    suspend fun getCount(): Int {
        return driverDao.getCount()
    }
}
