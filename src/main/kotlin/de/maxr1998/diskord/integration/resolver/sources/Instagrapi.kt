package de.maxr1998.diskord.integration.resolver.sources

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object Instagrapi {
    @Serializable
    data class MediaInfo(
        val code: String,
        @SerialName("thumbnail_url")
        val thumbnailUrl: String?,
        @SerialName("video_url")
        val videoUrl: String?,
        @SerialName("media_type")
        val mediaType: Int,
        val resources: List<Resource>,
    ) {
        @Serializable
        data class Resource(
            @SerialName("thumbnail_url")
            val thumbnailUrl: String?,
            @SerialName("video_url")
            val videoUrl: String?,
            @SerialName("media_type")
            val mediaType: Int,
        )
    }
}