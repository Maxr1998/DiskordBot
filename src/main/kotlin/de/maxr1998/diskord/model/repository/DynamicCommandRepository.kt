package de.maxr1998.diskord.model.repository

import de.maxr1998.diskord.model.database.CommandEntity
import de.maxr1998.diskord.model.database.CommandEntries
import de.maxr1998.diskord.model.database.CommandEntryEntity
import de.maxr1998.diskord.model.database.Commands
import de.maxr1998.diskord.model.database.Entries
import de.maxr1998.diskord.model.database.EntryType
import de.maxr1998.diskord.utils.exposed.suspendingTransaction
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Count
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.insertIgnoreAndGetId
import org.jetbrains.exposed.sql.not
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.UpdateStatement
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
            Commands.guild eq guild and not(Commands.hidden)
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

    suspend fun hideCommandByGuild(guild: String, command: String): Boolean = suspendingTransaction {
        Commands.update(where = { (Commands.guild eq guild) and (Commands.command eq command) }) { update ->
            update[hidden] = true
        } > 0
    }

    suspend fun removeCommandByGuild(guild: String, command: String): Boolean = suspendingTransaction {
        (Commands.deleteWhere {
            (Commands.guild eq guild) and (Commands.command eq command)
        } > 0).also { removed ->
            if (removed) cleanEntriesInternal()
        }
    }

    private suspend fun addCommandEntry(commandEntity: CommandEntity, commandEntryEntity: CommandEntryEntity): Boolean = suspendingTransaction {
        val id = updateExistingEntryInternal(commandEntryEntity)
            ?: Entries.insertIgnoreAndGetId { insert ->
                insert[content] = commandEntryEntity.content
                insert[contentSource] = commandEntryEntity.contentSource
                insert[type] = commandEntryEntity.type
                insert[width] = commandEntryEntity.width
                insert[height] = commandEntryEntity.height
            }
            ?: return@suspendingTransaction false // possible race-condition?

        CommandEntries.insertIgnore { insert ->
            insert[command] = commandEntity.id
            insert[entry] = id
        }.insertedCount > 0
    }

    suspend fun getCommandEntry(content: String): CommandEntryEntity? = suspendingTransaction {
        getEntryByContentInternal(content)?.let { entry ->
            CommandEntryEntity(
                id = entry[Entries.id],
                content = entry[Entries.content],
                contentSource = entry[Entries.contentSource],
                type = entry[Entries.type],
                width = entry[Entries.width],
                height = entry[Entries.height],
            )
        }
    }

    private suspend fun removeCommandEntry(commandEntity: CommandEntity, entry: String): Boolean = suspendingTransaction {
        val id = getEntryByContentInternal(entry)?.get(Entries.id) ?: return@suspendingTransaction false
        (CommandEntries.deleteWhere {
            (CommandEntries.command eq commandEntity.id) and (CommandEntries.entry eq id)
        } > 0).also { removed ->
            if (removed) cleanEntriesInternal()
        }
    }

    suspend fun removeEntryForGuild(entry: String, guild: String): Boolean = suspendingTransaction {
        val id = getEntryByContentInternal(entry)?.get(Entries.id) ?: return@suspendingTransaction false
        val commandsInGuild = Commands.slice(Commands.id).select { Commands.guild eq guild }
        (CommandEntries.deleteWhere {
            (CommandEntries.entry eq id) and (CommandEntries.command inSubQuery commandsInGuild)
        } > 0).also { removed ->
            if (removed) cleanEntriesInternal()
        }
    }

    /**
     * Adds all [entries] to all commands in [commandEntities]
     *
     * @return true if any changes were made
     */
    suspend fun addCommandEntries(commandEntities: List<CommandEntity>, entries: List<CommandEntryEntity>): Boolean {
        var acc = false
        for (commandEntity in commandEntities) {
            for (entry in entries) {
                acc = addCommandEntry(commandEntity, entry) || acc
            }
        }
        return acc
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

    private fun updateExistingEntryInternal(commandEntryEntity: CommandEntryEntity): EntityID<Long>? {
        val existing = getEntryByContentInternal(commandEntryEntity.content) ?: return null

        val updates = mutableListOf<Entries.(UpdateStatement) -> Unit>()
        if (commandEntryEntity.contentSource != null) updates.add { update ->
            update[contentSource] = commandEntryEntity.contentSource
        }
        if (existing[Entries.type] == EntryType.UNKNOWN || commandEntryEntity.type > EntryType.LINK) updates.add { update ->
            update[type] = commandEntryEntity.type
        }
        if (commandEntryEntity.width > 0 && commandEntryEntity.height > 0) updates.add { update ->
            update[width] = commandEntryEntity.width
            update[height] = commandEntryEntity.height
        }

        val id = existing[Entries.id]
        if (updates.isNotEmpty()) Entries.update(where = { Entries.id eq id }) { update ->
            updates.forEach { action -> action(update) }
        }

        return id
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