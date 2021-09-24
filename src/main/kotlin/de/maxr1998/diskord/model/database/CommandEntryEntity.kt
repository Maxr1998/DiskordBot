package de.maxr1998.diskord.model.database

import de.maxr1998.diskord.utils.isUrl
import org.jetbrains.exposed.dao.id.EntityID

data class CommandEntryEntity(
    val id: EntityID<Int>?,
    val content: String,
    val type: Int,
    val width: Int,
    val height: Int,
) {
    companion object {
        fun tryUrl(content: String) = CommandEntryEntity(
            id = null,
            content = content,
            type = if (content.isUrl()) EntryType.LINK else EntryType.TEXT,
            width = 0,
            height = 0
        )

        fun image(url: String, width: Int = 0, height: Int = 0) = CommandEntryEntity(
            id = null,
            content = url,
            type = EntryType.IMAGE,
            width = width,
            height = height
        )

        fun gif(url: String, width: Int = 0, height: Int = 0) = CommandEntryEntity(
            id = null,
            content = url,
            type = EntryType.GIF,
            width = width,
            height = height
        )

        fun video(url: String, width: Int = 0, height: Int = 0) = CommandEntryEntity(
            id = null,
            content = url,
            type = EntryType.VIDEO,
            width = width,
            height = height
        )
    }
}