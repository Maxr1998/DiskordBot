package de.maxr1998.diskord

import com.jessecorbett.diskord.api.common.Emoji
import com.jessecorbett.diskord.api.common.stringified

fun Config.getAckEmoji(): String =
    ackEmojiId?.let { id -> Emoji(id = id).stringified } ?: Constants.DEFAULT_ACK_EMOJI