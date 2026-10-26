package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.ExpenseDao
import com.example.data.local.dao.HistoryDao
import com.example.data.local.dao.NoteDao
import com.example.data.local.dao.ScannedDocumentDao
import com.example.data.local.dao.UtilityDao
import com.example.data.local.entity.ExpenseEntity
import com.example.data.local.entity.FavoriteEntity
import com.example.data.local.entity.HistoryEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.RecentToolEntity
import com.example.data.local.entity.ScannedDocumentEntity

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ExpenseEntity::class,
        NoteEntity::class,
        HistoryEntity::class,
        FavoriteEntity::class,
        RecentToolEntity::class,
        ScannedDocumentEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun noteDao(): NoteDao
    abstract fun historyDao(): HistoryDao
    abstract fun utilityDao(): UtilityDao
    abstract fun scannedDocumentDao(): ScannedDocumentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scanned_documents ADD COLUMN processedImagePath TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE scanned_documents ADD COLUMN rawImagePath TEXT DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "utility_hub.db"
                ).addMigrations(MIGRATION_2_3)
                 .fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
