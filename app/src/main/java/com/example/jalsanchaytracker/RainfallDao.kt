package com.example.jalsanchaytracker

import androidx.room.*

@Dao
interface RainfallDao {

    @Query(
        """
        SELECT year, month,
        CAST(strftime('%d', dateMillis / 1000, 'unixepoch', 'localtime') AS INTEGER) as day,
        SUM(waterCollectedL) as waterCollectedL,
        MIN(dateMillis) as dateMillis, MIN(id) as id,
        MIN(roofAreaSqFt) as roofAreaSqFt, MIN(rainfallMm) as rainfallMm,
        MIN(tankCapacityL) as tankCapacityL
        FROM rainfall_entries
        GROUP BY year, month, day
        ORDER BY year ASC, month ASC, day ASC
    """
    )
    fun getDailySummaries(): List<RainfallEntry>

    // Non-suspend insert/delete — called inside withContext(Dispatchers.IO) in the activity
    @Insert
    fun insert(entry: RainfallEntry)

    @Query("DELETE FROM rainfall_entries WHERE id = :id")
    fun deleteById(id: Int)

    @Query("SELECT * FROM rainfall_entries ORDER BY dateMillis DESC")
    fun getAll(): List<RainfallEntry>

    @Query("SELECT COALESCE(SUM(waterCollectedL), 0.0) FROM rainfall_entries")
    fun getTotalWater(): Double

    @Query("SELECT COALESCE(SUM(waterCollectedL), 0.0) FROM rainfall_entries WHERE dateMillis >= :startOfDay")
    fun getTodayTotal(startOfDay: Long): Double

    @Query("SELECT COALESCE(SUM(waterCollectedL), 0.0) FROM rainfall_entries WHERE year = :year AND month = :month")
    fun getMonthlyTotal(year: Int, month: Int): Double

    @Query(
        """
        SELECT year, month, 1 as day, SUM(waterCollectedL) as waterCollectedL,
        MIN(dateMillis) as dateMillis, MIN(id) as id,
        MIN(roofAreaSqFt) as roofAreaSqFt, MIN(rainfallMm) as rainfallMm,
        MIN(tankCapacityL) as tankCapacityL
        FROM rainfall_entries
        GROUP BY year, month
        ORDER BY year ASC, month ASC
    """
    )
    fun getMonthlySummaries(): List<RainfallEntry>
}