package de.maxr1998.diskord.model

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table

private const val MAX_ID_LENGTH = 20 // ceil(log(2^64))
private const val MAX_COMMAND_LENGTH = 32
private const val MAX_CONTENT_LENGTH = 1024

const val GUILD_GLOBAL = "global"

object Commands : IntIdTable("commands") {
    val guild = varchar("server", MAX_ID_LENGTH).index()
    val command = varchar("command", MAX_COMMAND_LENGTH)
    val hidden = bool("hidden").default(false)

    init {
        uniqueIndex(guild, command)
    }

    val isGlobal: Op<Boolean>
        get() = guild eq GUILD_GLOBAL
}

object EntryType {
    const val UNKNOWN = -1
    const val TEXT = 0
    const val LINK = 1
    const val IMAGE = 2
    const val GIF = 3
    const val VIDEO = 4
}

object CommandEntries : Table("command_entries") {
    val command = reference("command", Commands, ReferenceOption.CASCADE, ReferenceOption.CASCADE).index()
    val entry = reference("entry", Entries, ReferenceOption.CASCADE, ReferenceOption.CASCADE).index()

    init {
        uniqueIndex(command, entry)
    }
}

object Entries : LongIdTable("entries") {
    val content = varchar("content", MAX_CONTENT_LENGTH).uniqueIndex()
    val contentSource = varchar("source", MAX_CONTENT_LENGTH).nullable().default(null).index()
    val type = integer("type").default(EntryType.UNKNOWN).index()
    val width = integer("width").default(0)
    val height = integer("height").default(0)
    val flags = integer("flags").default(0)
}