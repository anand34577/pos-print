package com.posprint.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PrinterDao {
    @Query("SELECT * FROM printers ORDER BY isDefault DESC, name COLLATE NOCASE")
    abstract fun observeAll(): Flow<List<PrinterEntity>>

    @Query("SELECT * FROM printers WHERE isDefault = 1 LIMIT 1")
    abstract fun observeDefault(): Flow<PrinterEntity?>

    @Query("SELECT * FROM printers WHERE id = :id")
    abstract suspend fun get(id: Long): PrinterEntity?

    @Query("SELECT * FROM printers WHERE address = :address LIMIT 1")
    abstract suspend fun findByAddress(address: String): PrinterEntity?

    @Query("SELECT COUNT(*) FROM printers")
    abstract suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(entity: PrinterEntity): Long

    @Update
    abstract suspend fun update(entity: PrinterEntity)

    @Query("DELETE FROM printers WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("UPDATE printers SET isDefault = 0")
    protected abstract suspend fun clearDefault()

    @Query("UPDATE printers SET isDefault = 1 WHERE id = :id")
    protected abstract suspend fun markDefault(id: Long)

    @Query(
        "UPDATE printers SET isDefault = 1 WHERE id = (SELECT id FROM printers ORDER BY id LIMIT 1) " +
            "AND NOT EXISTS (SELECT 1 FROM printers WHERE isDefault = 1)",
    )
    abstract suspend fun promoteFirstIfNoDefault()

    @Transaction
    open suspend fun setDefault(id: Long) {
        clearDefault()
        markDefault(id)
    }
}
