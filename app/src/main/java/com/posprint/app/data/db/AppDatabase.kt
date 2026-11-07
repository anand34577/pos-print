package com.posprint.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PrinterEntity::class, TemplateEntity::class, HistoryEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun printerDao(): PrinterDao
    abstract fun templateDao(): TemplateDao
    abstract fun historyDao(): HistoryDao
}
