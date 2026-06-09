package com.example.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import com.example.data.Meal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

class HealthConnectManager(private val context: Context) {
    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }
    
    val permissions = setOf(
        HealthPermission.getWritePermission(NutritionRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class)
    )
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected
    
    fun checkAvailability(): Int {
        return HealthConnectClient.getSdkStatus(context, "com.google.android.apps.healthdata")
    }

    suspend fun checkPermissions(): Boolean {
        val granted = healthConnectClient.permissionController.getGrantedPermissions().containsAll(permissions)
        _isConnected.value = granted
        return granted
    }
    
    suspend fun writeMealInfo(meal: Meal) {
        if (!_isConnected.value) return
        
        try {
            val instant = Instant.ofEpochMilli(meal.timestamp)
            val offset = java.time.ZoneId.systemDefault().rules.getOffset(instant)
            val record = NutritionRecord(
                startTime = instant,
                startZoneOffset = offset,
                endTime = Instant.ofEpochMilli(meal.timestamp + 60000), // 1 min duration
                endZoneOffset = offset,
                energy = Energy.kilocalories(meal.calories.toDouble()),
                protein = Mass.grams(meal.protein.toDouble()),
                totalCarbohydrate = Mass.grams(meal.carbs.toDouble()),
                totalFat = Mass.grams(meal.fat.toDouble()),
                name = meal.name
            )
            healthConnectClient.insertRecords(listOf(record))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
