package de.maxr1998.diskord.utils.exposed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Run a database transaction through the IO dispatcher
 */
suspend inline fun <T> suspendingTransaction(noinline block: Transaction.() -> T): T = withContext(Dispatchers.IO) {
    transaction { block() }
}