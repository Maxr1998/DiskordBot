package de.maxr1998.diskord.utils

import de.maxr1998.diskord.model.database.CommandEntries
import de.maxr1998.diskord.model.database.Commands
import de.maxr1998.diskord.model.database.Entries
import de.maxr1998.diskord.utils.exposed.suspendingTransaction
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.io.File
import java.sql.Connection

class DatabaseHelpers(private val databaseFile: File) {
    fun setup() {
        Database.connect("jdbc:sqlite:${databaseFile.absolutePath}?foreign_keys=on&journal_mode=WAL", "org.sqlite.JDBC")
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
    }

    suspend fun createSchemas() = suspendingTransaction {
        SchemaUtils.create(
            Commands,
            CommandEntries,
            Entries,
        )
    }
}