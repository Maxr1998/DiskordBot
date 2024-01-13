package de.maxr1998.diskord.util.exposed

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.FieldSet
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.isAutoInc
import org.jetbrains.exposed.sql.select

suspend fun FieldSet.processBatches(
    batchSize: Int,
    where: SqlExpressionBuilder.() -> Op<Boolean>,
    startOffset: Long = 0L,
    action: suspend (List<ResultRow>) -> Unit,
) {
    val autoIncColumn = try {
        source.columns.first { it.columnType.isAutoInc }
    } catch (_: NoSuchElementException) {
        throw UnsupportedOperationException("Batch processing only works on tables with an autoincrementing column")
    }

    var lastOffset = startOffset
    while (true) {
        val batch = suspendingTransaction {
            select { (autoIncColumn greater lastOffset) and SqlExpressionBuilder.where() }
                .limit(batchSize)
                .orderBy(autoIncColumn, SortOrder.ASC)
                .toList()
        }

        if (batch.isEmpty()) break

        action(batch)

        if (batch.size < batchSize) break

        lastOffset = toLong(checkNotNull(batch.last()[autoIncColumn]) { "Batch element autoIncColumn is null" })
    }
}

private fun toLong(autoIncVal: Any): Long = when (autoIncVal) {
    is EntityID<*> -> toLong(autoIncVal.value)
    is Int -> autoIncVal.toLong()
    else -> autoIncVal as Long
}