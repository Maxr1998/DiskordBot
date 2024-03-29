package de.maxr1998.diskord.command.dynamic

import de.maxr1998.diskord.model.CommandEntries
import de.maxr1998.diskord.model.Commands
import de.maxr1998.diskord.model.Entries
import de.maxr1998.diskord.model.EntryType
import de.maxr1998.diskord.model.GUILD_GLOBAL
import de.maxr1998.diskord.util.exposed.suspendingTransaction
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Count
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.insertIgnoreAndGetId
import org.jetbrains.exposed.sql.not
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.update
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

@Suppress("TooManyFunctions")
object DynamicCommandRepository {
    private val random: Random = SecureRandom.getInstanceStrong().asKotlinRandom()

    suspend fun getCommandByGuild(guild: String, command: String): CommandEntity? = suspendingTransaction {
        Commands.slice(Commands.id).select {
            ((Commands.guild eq guild) or Commands.isGlobal) and (Commands.command eq command)
        }.singleOrNull()?.let { row ->
            CommandEntity(
                id = row[Commands.id],
                server = guild,
                command = command,
            )
        }
    }

    suspend fun getCommandsByGuild(
        guild: String,
        type: CommandType,
    ): List<Triple<String, Boolean, Long>> = suspendingTransaction {
        val countAlias = Count(CommandEntries.entry)
        Commands.leftJoin(CommandEntries)
            .slice(Commands.id, Commands.isGlobal, Commands.command, countAlias)
            .select {
                when (type) {
                    CommandType.ALL_VISIBLE -> (Commands.guild eq guild or Commands.isGlobal) and not(Commands.hidden)
                    CommandType.GLOBAL_ONLY -> Commands.isGlobal and not(Commands.hidden)
                    CommandType.HIDDEN -> (Commands.guild eq guild or Commands.isGlobal) and Commands.hidden
                }
            }
            .groupBy(Commands.id, Commands.command)
            .orderBy(Commands.isGlobal to SortOrder.DESC, Commands.command to SortOrder.ASC)
            .map { row ->
                Triple(row[Commands.command], row[Commands.isGlobal], row[countAlias])
            }
    }

    suspend fun addCommandByGuild(guild: String, command: String): Boolean = suspendingTransaction {
        Commands.insertIgnoreAndGetId { insert ->
            insert[Commands.guild] = guild
            insert[Commands.command] = command
        } != null
    }

    suspend fun renameCommandByGuild(guild: String, command: String, target: String): Boolean = suspendingTransaction {
        Commands.update(
            where = {
                (Commands.guild eq guild or Commands.isGlobal) and (Commands.command eq command)
            },
        ) { update ->
            update[Commands.command] = target
        } > 0
    }

    suspend fun publishCommandByGuild(guild: String, command: String): Boolean = suspendingTransaction {
        Commands.update(
            where = {
                (Commands.guild eq guild or Commands.isGlobal) and (Commands.command eq command)
            },
        ) { update ->
            update[Commands.guild] = GUILD_GLOBAL
        } > 0
    }

    suspend fun hideCommandByGuild(guild: String, command: String): Boolean = suspendingTransaction {
        Commands.update(
            where = {
                (Commands.guild eq guild or Commands.isGlobal) and (Commands.command eq command)
            },
        ) { update ->
            update[hidden] = true
        } > 0
    }

    suspend fun removeCommandByGuild(guild: String, command: String): Boolean = suspendingTransaction {
        val removedAny = Commands.deleteWhere {
            (Commands.guild eq guild or Commands.isGlobal) and (Commands.command eq command)
        } > 0
        if (removedAny) {
            cleanEntriesInternal()
        }
        removedAny
    }

    suspend fun addCommandEntry(
        commandEntity: CommandEntity,
        commandEntryEntity: CommandEntryEntity,
    ): Boolean = suspendingTransaction {
        val id = updateExistingEntryInternal(commandEntryEntity)
            ?: Entries.insertIgnoreAndGetId { insert ->
                insert[content] = commandEntryEntity.content
                insert[contentSource] = commandEntryEntity.contentSource
                insert[type] = commandEntryEntity.type
                insert[width] = commandEntryEntity.width
                insert[height] = commandEntryEntity.height
                insert[flags] = commandEntryEntity.flags
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
                flags = entry[Entries.flags],
            )
        }
    }

    suspend fun updateCommandEntries(entries: List<CommandEntryEntity>) = suspendingTransaction {
        for (entry in entries) updateExistingEntryInternal(entry)
    }

    suspend fun setSourceForEntries(source: String, entries: List<String>): Boolean = suspendingTransaction {
        val updatedAny = Entries.update(where = { Entries.content inList entries }) { update ->
            update[contentSource] = source
        } > 0

        updatedAny
    }

    private suspend fun removeCommandEntry(command: CommandEntity, entry: String): Boolean = suspendingTransaction {
        val id = getEntryByContentInternal(entry)?.get(Entries.id) ?: return@suspendingTransaction false
        val removedAny = CommandEntries.deleteWhere {
            (CommandEntries.command eq command.id) and (CommandEntries.entry eq id)
        } > 0
        if (removedAny) {
            cleanEntriesInternal()
        }
        removedAny
    }

    suspend fun removeEntryForGuild(entry: String, guild: String): Boolean = suspendingTransaction {
        val id = getEntryByContentInternal(entry)?.get(Entries.id) ?: return@suspendingTransaction false
        val commandsInGuild = Commands.slice(Commands.id).select { Commands.guild eq guild }
        val removedAny = CommandEntries.deleteWhere {
            (CommandEntries.entry eq id) and (CommandEntries.command inSubQuery commandsInGuild)
        } > 0
        if (removedAny) {
            cleanEntriesInternal()
        }
        removedAny
    }

    /**
     * Adds all [entries] to all commands in [commandEntities]
     *
     * @return true if any changes were made
     */
    suspend fun addCommandEntries(
        commandEntities: List<List<CommandEntity>>,
        entries: List<CommandEntryEntity>,
    ): Boolean = when (commandEntities.size) {
        1 -> {
            var acc = false
            for (commandEntity in commandEntities.first()) {
                for (entry in entries) {
                    acc = addCommandEntry(commandEntity, entry) || acc
                }
            }
            acc
        }
        entries.size -> {
            var acc = false
            commandEntities.forEachIndexed { index, commandSetEntities ->
                val entry = entries[index]
                for (commandEntity in commandSetEntities) {
                    acc = addCommandEntry(commandEntity, entry) || acc
                }
            }
            acc
        }
        else -> error("Mismatch in number of command sets and entries")
    }

    suspend fun removeCommandEntries(commandEntity: CommandEntity, entries: List<String>): Boolean {
        return entries.fold(false) { anySuccess, entry ->
            removeCommandEntry(commandEntity, entry) || anySuccess
        }
    }

    suspend fun getRandomEntry(commandEntity: CommandEntity): String? = suspendingTransaction {
        val count = CommandEntries.select { CommandEntries.command eq commandEntity.id }.count()
        when {
            count > 0L -> {
                val offset = random.nextLong(count)
                CommandEntries.innerJoin(Entries)
                    .select { CommandEntries.command eq commandEntity.id }
                    .limit(1, offset)
                    .singleOrNull()
                    ?.get(Entries.content)
            }
            else -> null
        }
    }

    private fun getEntryByContentInternal(content: String): ResultRow? {
        return Entries.select { Entries.content eq content }.singleOrNull()
    }

    private fun updateExistingEntryInternal(commandEntryEntity: CommandEntryEntity): EntityID<Long>? {
        val existing = getEntryByContentInternal(commandEntryEntity.content) ?: return null

        val updates = mutableListOf<Entries.(UpdateStatement) -> Unit>()
        if (commandEntryEntity.contentSource != null) {
            updates.add { update ->
                update[contentSource] = commandEntryEntity.contentSource
            }
        }
        if (existing[Entries.type] == EntryType.UNKNOWN || commandEntryEntity.type > EntryType.LINK) {
            updates.add { update ->
                update[type] = commandEntryEntity.type
            }
        }
        if (commandEntryEntity.width > 0 && commandEntryEntity.height > 0) {
            updates.add { update ->
                update[width] = commandEntryEntity.width
                update[height] = commandEntryEntity.height
            }
        }

        val id = existing[Entries.id]
        if (updates.isNotEmpty()) {
            Entries.update(where = { Entries.id eq id }) { update ->
                updates.forEach { action -> action(update) }
            }
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

    enum class CommandType {
        ALL_VISIBLE,
        GLOBAL_ONLY,
        HIDDEN,
    }
}