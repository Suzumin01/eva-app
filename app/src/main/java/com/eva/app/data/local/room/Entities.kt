package com.eva.app.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_doctors")
data class CachedDoctor(
    @PrimaryKey val doctorId: Int,
    val fullName: String,
    val specializationName: String,
    val clinicName: String,
    val clinicAddress: String,
    val rating: String?,
    val experienceYears: Int?,
    val reviewsCount: Int,
    val bio: String?,
    val isActive: Boolean,
    val cachedAt: Long = System.currentTimeMillis()
)