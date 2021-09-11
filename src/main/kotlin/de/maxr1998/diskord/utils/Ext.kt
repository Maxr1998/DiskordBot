package de.maxr1998.diskord.utils

import com.jessecorbett.diskord.api.common.Emoji
import com.jessecorbett.diskord.api.common.stringified
import de.maxr1998.diskord.Constants
import de.maxr1998.diskord.config.Config

fun Config.getAckEmoji(): String =
    ackEmojiId?.let { id -> Emoji(id = id).stringified } ?: Constants.DEFAULT_ACK_EMOJI