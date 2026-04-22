package com.eva.app.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CachedDoctor::class],
    version  = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun doctorCacheDao(): DoctorCacheDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // v1→v2: added cachedAt column
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE cached_doctors ADD COLUMN cachedAt INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "eva_cache.db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}