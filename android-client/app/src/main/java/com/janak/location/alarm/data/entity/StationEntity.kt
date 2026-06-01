package com.janak.location.alarm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val code: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val state: String? = null,
    val zone: String? = null,
    val address: String? = null
)
