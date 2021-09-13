package de.maxr1998.diskord.model.repository

import de.maxr1998.diskord.model.database.CommandEntity
import de.maxr1998.diskord.model.database.CommandEntries
import de.maxr1998.diskord.model.database.CommandEntryEntity
import de.maxr1998.diskord.model.database.Commands
import de.maxr1998.diskord.utils.exposed.suspendingTransaction
import org.jetbrains.exposed.sql.Count
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnoreAndGetId
import org.jetbrains.exposed.sql.select
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
        val countAlias = Count(CommandEntries.content)
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
        Commands.deleteWhere {
            (Commands.guild eq guild) and (Commands.command eq command)
        } > 0
    }

    private suspend fun addCommandEntry(commandEntity: CommandEntity, entry: CommandEntryEntity): Boolean = suspendingTransaction {
        CommandEntries.insertIgnoreAndGetId { insert ->
            insert[command] = commandEntity.id
            insert[content] = entry.content
            insert[type] = entry.type
            insert[width] = entry.width
            insert[height] = entry.height
        } != null
    }

    private suspend fun removeCommandEntry(commandEntity: CommandEntity, entry: String): Boolean = suspendingTransaction {
        CommandEntries.deleteWhere {
            (CommandEntries.command eq commandEntity.id) and (CommandEntries.content eq entry)
        } > 0
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
            CommandEntries
                .select { CommandEntries.command eq commandEntity.id }
                .limit(1, offset)
                .singleOrNull()
                ?.get(CommandEntries.content)
        } else null
    }
}