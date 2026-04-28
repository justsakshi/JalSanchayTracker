package com.example.jalsanchaytracker

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rainfall_entries")
data class RainfallEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val dateMillis: Long,
    val roofAreaSqFt: Double,
    val rainfallMm: Double,
    val tankCapacityL: Double,
    val waterCollectedL: Double,
    val year: Int,
    val month: Int,
    @ColumnInfo(defaultValue = "1")
    val day: Int = 1
)