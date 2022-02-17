package de.maxr1998.diskord.model.dto.twitter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object TwitterApi {
    @Serializable
    data class Tweet(
        val includes: Includes? = null,
    ) {
        @Serializable
        data class Includes(
            val media: List<MediaObject> = emptyList(),
        )
    }

    @Serializable
    data class MediaObject(
        @SerialName("media_key")
        val mediaKey: String,
        val type: String,
        val url: String? = null,
        val width: Int = -1,
        val height: Int = -1,
    ) {
        companion object {
            const val TYPE_PHOTO = "photo"
            const val TYPE_VIDEO = "video"
        }
    }
}