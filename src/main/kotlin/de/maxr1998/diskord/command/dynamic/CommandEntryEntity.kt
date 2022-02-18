package de.maxr1998.diskord.command.dynamic

import de.maxr1998.diskord.model.EntryType
import de.maxr1998.diskord.util.extension.isUrl
import io.ktor.http.Url
import org.jetbrains.exposed.dao.id.EntityID

data class CommandEntryEntity(
    val id: EntityID<Long>?,
    val content: String,
    val contentSource: String?,
    val type: Int,
    val width: Int,
    val height: Int,
) {
    companion object {
        fun text(content: String, source: Url?) = CommandEntryEntity(
            id = null,
            content = content,
            contentSource = source?.toString(),
            type = EntryType.TEXT,
            width = 0,
            height = 0,
        )

        fun tryUrl(content: String, source: Url?) = CommandEntryEntity(
            id = null,
            content = content,
            contentSource = source?.toString(),
            type = if (content.isUrl()) EntryType.LINK else EntryType.TEXT,
            width = 0,
            height = 0,
        )

        fun image(url: String, source: Url?, width: Int = 0, height: Int = 0) = CommandEntryEntity(
            id = null,
            content = url,
            contentSource = source?.toString(),
            type = EntryType.IMAGE,
            width = width,
            height = height,
        )

        fun gif(url: String, source: Url?, width: Int = 0, height: Int = 0) = CommandEntryEntity(
            id = null,
            content = url,
            contentSource = source?.toString(),
            type = EntryType.GIF,
            width = width,
            height = height,
        )

        fun video(url: String, source: Url?, width: Int = 0, height: Int = 0) = CommandEntryEntity(
            id = null,
            content = url,
            contentSource = source?.toString(),
            type = EntryType.VIDEO,
            width = width,
            height = height,
        )
    }
}