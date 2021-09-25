package de.maxr1998.diskord.model.repository

import de.maxr1998.diskord.model.database.CommandEntity
import de.maxr1998.diskord.model.database.CommandEntries
import de.maxr1998.diskord.model.database.CommandEntryEntity
import de.maxr1998.diskord.model.database.Commands
import de.maxr1998.diskord.model.database.Entries
import de.maxr1998.diskord.model.database.EntryType
import de.maxr1998.diskord.utils.exposed.suspendingTransaction
import org.jetbrains.exposed.sql.Count
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.insertIgnoreAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

object DynamicCommandRepository {
    private val random: Random = SecureRandom().asKotlinRandom()

    suspend fun getCommandByGuild(guild: String, command: String): CommandEntity? = suspendingTransaction {
        Commands.slice(Commands.id).select {
            (Commands.guild eq guild) and (Commands.command eq command)
        }.singleOrNull()?.let { row ->
            CommandEntity(
                id = row[Commands.id],
                server = guild,
                command = command,
            )
        }
    }

    suspend fun getCommandsByGuild(guild: String): List<Pair<String, Long>> = suspendingTransaction {
        val countAlias = Count(CommandEntries.entry)
        Commands.leftJoin(CommandEntries).slice(Commands.id, Commands.command, countAlias).select {
            Commands.guild eq guild
        }.groupBy(Commands.id, Commands.command).orderBy(Commands.command).map { row ->
            row[Commands.command] to row[countAlias]
        }
    }

    suspend fun addCommandByGuild(guild: String, command: String): Boolean = suspendingTransaction {
        Commands.insertIgnoreAndGetId { insert ->
            insert[Commands.guild] = guild
            insert[Commands.command] = command
        } != null
    }

    suspend fun removeCommandByGuild(guild: String, command: String): Boolean = suspendingTransaction {
        (Commands.deleteWhere {
            (Commands.guild eq guild) and (Commands.command eq command)
        } > 0).also { removed ->
            if (removed) cleanEntriesInternal()
        }
    }

    private suspend fun addCommandEntry(commandEntity: CommandEntity, commandEntryEntity: CommandEntryEntity): Boolean = suspendingTransaction {
        val existing = getEntryByContentInternal(commandEntryEntity.content)
        val id = if (existing != null) {
            val id = existing[Entries.id]
            Entries.update(where = { Entries.id eq id }) { update ->
                if (commandEntryEntity.contentSource != null) {
                    update[contentSource] = commandEntryEntity.contentSource
                }
                if (existing[type] == EntryType.UNKNOWN || commandEntryEntity.type > EntryType.LINK) {
                    update[type] = commandEntryEntity.type
                }
                if (commandEntryEntity.width > 0 && commandEntryEntity.height > 0) {
                    update[width] = commandEntryEntity.width
                    update[height] = commandEntryEntity.height
                }
            }
            id
        } else {
            Entries.insertIgnoreAndGetId { insert ->
                insert[content] = commandEntryEntity.content
                insert[contentSource] = commandEntryEntity.contentSource
                insert[type] = commandEntryEntity.type
                insert[width] = commandEntryEntity.width
                insert[height] = commandEntryEntity.height
            } ?: return@suspendingTransaction false // possible race-condition?
        }

        CommandEntries.insertIgnore { insert ->
            insert[command] = commandEntity.id
            insert[entry] = id
        }.insertedCount > 0
    }

    private suspend fun removeCommandEntry(commandEntity: CommandEntity, entry: String): Boolean = suspendingTransaction {
        val id = getEntryByContentInternal(entry)?.get(Entries.id) ?: return@suspendingTransaction false
        (CommandEntries.deleteWhere {
            (CommandEntries.command eq commandEntity.id) and (CommandEntries.entry eq id)
        } > 0).also { removed ->
            if (removed) cleanEntriesInternal()
        }
    }

    suspend fun addCommandEntries(commandEntity: CommandEntity, entries: List<CommandEntryEntity>): Boolean {
        return entries.fold(false) { anySuccess, entry ->
            addCommandEntry(commandEntity, entry) || anySuccess
        }
    }

    suspend fun removeCommandEntries(commandEntity: CommandEntity, entries: List<String>): Boolean {
        return entries.fold(false) { anySuccess, entry ->
            removeCommandEntry(commandEntity, entry) || anySuccess
        }
    }

    suspend fun getRandomEntry(commandEntity: CommandEntity): String? = suspendingTransaction {
        val count = CommandEntries.select { CommandEntries.command eq commandEntity.id }.count()
        if (count > 0L) {
            val offset = random.nextLong(count)
            CommandEntries.innerJoin(Entries)
                .select { CommandEntries.command eq commandEntity.id }
                .limit(1, offset)
                .singleOrNull()
                ?.get(Entries.content)
        } else null
    }

    private fun getEntryByContentInternal(content: String): ResultRow? {
        return Entries.select { Entries.content eq content }.singleOrNull()
    }

    /**
     * Remove orphaned entries
     */
    private fun cleanEntriesInternal() {
        Entries.deleteWhere {
            Entries.id inSubQuery Entries.leftJoin(CommandEntries)
                .slice(Entries.id)
                .select { CommandEntries.command.isNull() }
        }
    }
}