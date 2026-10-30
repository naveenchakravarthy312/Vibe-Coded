package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calculation_history")
data class CalculationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val expression: String,
    val result: String,
    val category: String = CATEGORY_CALCULATOR, // CALCULATOR or CONVERTER
    val details: String = "",                    // E.g., "100 km → 62.137 mi" or formula
    val note: String = "",                       // Optional user note e.g. "Grocery budget"
    val isFavorite: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val CATEGORY_CALCULATOR = "CALCULATOR"
        const val CATEGORY_CONVERTER = "CONVERTER"
    }
}
