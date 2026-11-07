package com.posprint.app.data

import com.posprint.app.data.db.PrinterDao
import com.posprint.app.data.db.toDomain
import com.posprint.app.data.db.toEntity
import com.posprint.app.domain.model.Printer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrinterRepository @Inject constructor(private val dao: PrinterDao) {

    fun observeAll(): Flow<List<Printer>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    fun observeDefault(): Flow<Printer?> = dao.observeDefault().map { it?.toDomain() }

    suspend fun get(id: Long): Printer? = dao.get(id)?.toDomain()
    suspend fun findByAddress(address: String): Printer? = dao.findByAddress(address)?.toDomain()

    /** The first printer ever saved becomes the default automatically. */
    suspend fun add(printer: Printer): Printer {
        val first = dao.count() == 0
        val withDefault = printer.copy(isDefault = first)
        return withDefault.copy(id = dao.insert(withDefault.toEntity()))
    }

    suspend fun update(printer: Printer) = dao.update(printer.toEntity())
    suspend fun setDefault(id: Long) = dao.setDefault(id)

    suspend fun delete(id: Long) {
        dao.delete(id)
        dao.promoteFirstIfNoDefault()
    }
}
