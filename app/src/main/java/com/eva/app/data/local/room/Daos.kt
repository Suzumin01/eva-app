package com.eva.app.data.local.room

import androidx.room.*

@Dao
interface DoctorCacheDao {
    @Query("SELECT * FROM cached_doctors WHERE isActive = 1 ORDER BY rating DESC")
    suspend fun getAll(): List<CachedDoctor>

    @Query("SELECT * FROM cached_doctors WHERE isActive = 1 AND (fullName LIKE :query OR specializationName LIKE :query)")
    suspend fun search(query: String): List<CachedDoctor>

    @Query("SELECT * FROM cached_doctors WHERE isActive = 1 AND specializationName = :spec")
    suspend fun getBySpec(spec: String): List<CachedDoctor>

    @Upsert
    suspend fun upsertAll(doctors: List<CachedDoctor>)

    @Query("DELETE FROM cached_doctors")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM cached_doctors")
    suspend fun count(): Int
}

@Dao
interface ClinicCacheDao {
    @Query("SELECT * FROM cached_clinics ORDER BY clinicName ASC")
    suspend fun getAll(): List<CachedClinic>

    @Upsert
    suspend fun upsertAll(clinics: List<CachedClinic>)

    @Query("DELETE FROM cached_clinics")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM cached_clinics")
    suspend fun count(): Int
}