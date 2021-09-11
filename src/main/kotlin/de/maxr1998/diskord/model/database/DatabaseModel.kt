package de.maxr1998.diskord.model.database

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption

private const val MAX_ID_LENGTH = 20 // ceil(log(2^64))
private const val MAX_COMMAND_LENGTH = 32
private const val MAX_CONTENT_LENGTH = 1024

object Commands : IntIdTable("commands") {
    val guild = varchar("server", MAX_ID_LENGTH).index()
    val command = varchar("command", MAX_COMMAND_LENGTH)

    init {
        uniqueIndex(guild, command)
    }
}

object EntryType {
    const val UNKNOWN = -1
    const val TEXT = 0
    const val LINK = 1
    const val IMAGE = 2
    const val GIF = 3
    const val VIDEO = 4
}

object CommandEntries : LongIdTable("command_entries") {
    val command = reference("command", Commands, ReferenceOption.CASCADE, ReferenceOption.CASCADE).index()
    val content = varchar("content", MAX_CONTENT_LENGTH)
    val type = integer("type").default(EntryType.UNKNOWN).index()
    val width = integer("width").default(0)
    val height = integer("height").default(0)

    init {
        uniqueIndex(command, content)
    }
}