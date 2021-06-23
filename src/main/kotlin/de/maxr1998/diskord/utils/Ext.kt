package de.maxr1998.diskord.utils

import com.jessecorbett.diskord.api.common.Attachment
import com.jessecorbett.diskord.api.common.Emoji
import com.jessecorbett.diskord.api.common.Message
import com.jessecorbett.diskord.api.common.stringified
import de.maxr1998.diskord.Constants
import de.maxr1998.diskord.config.Config

fun Config.getAckEmoji(): String =
    ackEmojiId?.let { id -> Emoji(id = id).stringified } ?: Constants.DEFAULT_ACK_EMOJI

val Message.attachmentUrlsOrNull: List<String>?
    get() = with(attachments) {
        if (isNotEmpty()) map(Attachment::url)
        else null
    }

fun wrapListIfNotEmpty(content: String): List<String> = with(content.trim()) {
    if (isNotEmpty()) listOf(this) else emptyList()
}