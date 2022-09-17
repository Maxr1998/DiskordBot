package de.maxr1998.diskord.util

import de.maxr1998.diskord.model.Entries
import de.maxr1998.diskord.model.EntryFlag
import de.maxr1998.diskord.model.EntryType
import de.maxr1998.diskord.util.exposed.processBatches
import de.maxr1998.diskord.util.exposed.suspendingTransaction
import de.maxr1998.diskord.util.extension.hasNotFlag
import de.maxr1998.diskord.util.extension.toUrlOrNull
import io.ktor.client.HttpClient
import io.ktor.client.features.expectSuccess
import io.ktor.client.request.head
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.sync.Mutex
import mu.KotlinLogging
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.bitwiseOr
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update

private val logger = KotlinLogging.logger { }
private val cleanupMutex = Mutex()

class EntriesProcessor(
    private val httpClient: HttpClient,
) {
    /**
     * Checks all links in the database and flags removed (HTTP 404) entries.
     */
    suspend fun checkAndFlagRemovedLinks() {
        if (!cleanupMutex.tryLock()) return
        try {
            val linkTypes = listOf(EntryType.LINK, EntryType.IMAGE, EntryType.GIF, EntryType.VIDEO)
            val whereOp = (Entries.type inList linkTypes) and (Entries.flags hasNotFlag EntryFlag.DELETED_FROM_SERVER)

            val total = suspendingTransaction {
                Entries.select { whereOp }.count()
            }

            val batchCount = (total / BATCH_SIZE) + 1
            var batchNum = 1
            Entries.slice(Entries.id, Entries.content).processBatches(
                batchSize = BATCH_SIZE,
                where = { whereOp },
            ) { batch ->
                logger.info("Processing batch ${batchNum++} of $batchCount")
                handleBatch(batch)
            }
        } finally {
            cleanupMutex.unlock()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun handleBatch(batch: Iterable<ResultRow>) {
        val flagged = mutableListOf<EntityID<Long>>()

        for (entry in batch) {
            val id = entry[Entries.id]
            val url = entry[Entries.content].toUrlOrNull() ?: continue

            val response = try {
                httpClient.head<HttpResponse>(url) {
                    expectSuccess = false
                }
            } catch (e: Exception) {
                logger.error("Error while checking $url", e)
                continue
            }

            if (response.status == HttpStatusCode.NotFound) {
                flagged += id
            }
        }

        // Mark flagged entries as deleted
        if (flagged.isNotEmpty()) {
            suspendingTransaction {
                Entries.update(where = { Entries.id inList flagged }) { update ->
                    update[flags] = flags bitwiseOr EntryFlag.DELETED_FROM_SERVER
                }
            }
        }
    }

    private companion object {
        private const val BATCH_SIZE = 50
    }
}