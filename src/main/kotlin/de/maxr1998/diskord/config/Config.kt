package de.maxr1998.diskord.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    @SerialName("bot_token")
    val botToken: String,
    @SerialName("twitter_token")
    val twitterToken: String,
    @SerialName("twitter_api_version")
    val twitterApiVersion: Int,
    @SerialName("imgur_client_id")
    val imgurClientId: String,
    @SerialName("instagrapi_url")
    val instagrapiUrl: String,
    @SerialName("instagram_session")
    val instagramSession: String,
    @SerialName("owner_id")
    val ownerId: String,
    @SerialName("admin_ids")
    val adminIds: MutableSet<String> = HashSet(),
    @SerialName("manager_ids")
    val managerIds: MutableSet<String> = HashSet(),

    @SerialName("ack_emoji_id")
    val ackEmojiId: String? = null,
    @SerialName("file_server_base_url")
    val fileServerBaseUrl: String? = null,
)