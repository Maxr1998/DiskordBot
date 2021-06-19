package de.maxr1998.diskord

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    @SerialName("bot_token")
    val botToken: String,
    @SerialName("owner_id")
    val ownerId: String,
    @SerialName("admin_ids")
    val adminIds: MutableSet<String> = HashSet(),
    @SerialName("manager_ids")
    val managerIds: MutableSet<String> = HashSet(),

    val commands: MutableMap<String, MutableSet<String>> = HashMap(),
    @SerialName("ack_emoji_id")
    val ackEmojiId: String? = null,
    @SerialName("bot_status")
    var botStatus: String? = null,
)