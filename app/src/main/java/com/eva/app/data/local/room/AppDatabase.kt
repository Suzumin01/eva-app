package com.eva.app.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CachedDoctor::class, CachedClinic::class],
    version  = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun doctorCacheDao(): DoctorCacheDao
    abstract fun clinicCacheDao(): ClinicCacheDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "eva_cache.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}