package de.maxr1998.diskord.integration.resolver.sources

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonClassDiscriminator

object TwitterApi {
    const val TWITTER_IMAGE_BASE_URL = "https://pbs.twimg.com/media/"
    const val TWITTER_VIDEO_BASE_URL = "https://video.twimg.com/"

    interface Tweet {
        val mediaContainer: MediaContainer?

        interface MediaContainer {
            val media: List<MediaObject>

            interface MediaObject {
                val type: String
                val url: String?
                val width: Int
                val height: Int

                companion object {
                    const val TYPE_PHOTO = "photo"
                    const val TYPE_VIDEO = "video"
                }
            }
        }
    }

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    @JsonClassDiscriminator("__typename")
    sealed class TweetEmbedResponse

    @Serializable
    @SerialName("Tweet")
    data class TweetEmbed(
        @SerialName("mediaDetails")
        override val media: List<TweetV1_1.ExtendedEntities.MediaObject> = emptyList(),
    ) : TweetEmbedResponse(), Tweet, Tweet.MediaContainer {
        @Transient
        override val mediaContainer: Tweet.MediaContainer = this
    }

    @Serializable
    @SerialName("TweetTombstone")
    data class TweetTombstone(
        val tombstone: Tombstone,
    ) : TweetEmbedResponse() {
        val text: String get() = tombstone.text.text

        @Serializable
        class Tombstone(val text: Text) {
            @Serializable
            class Text(val text: String)
        }
    }

    @Suppress("ClassName")
    @Serializable
    data class TweetV1_1(
        @SerialName("extended_entities")
        override val mediaContainer: ExtendedEntities? = null,
    ) : Tweet {

        @Serializable
        data class ExtendedEntities(
            override val media: List<MediaObject> = emptyList(),
        ) : Tweet.MediaContainer {

            @Serializable
            data class MediaObject(
                override val type: String,
                @SerialName("media_url_https")
                override val url: String,
                @SerialName("original_info")
                val originalInfo: OriginalInfo? = null,
            ) : Tweet.MediaContainer.MediaObject {
                override val width: Int get() = originalInfo?.width ?: -1
                override val height: Int get() = originalInfo?.height ?: -1

                @Serializable
                data class OriginalInfo(
                    val width: Int,
                    val height: Int,
                )
            }
        }
    }

    @Serializable
    data class TweetV2(
        @SerialName("includes")
        override val mediaContainer: Includes? = null,
    ) : Tweet {

        @Serializable
        data class Includes(
            override val media: List<MediaObject> = emptyList(),
        ) : Tweet.MediaContainer {

            @Serializable
            data class MediaObject(
                override val type: String,
                override val url: String? = null,
                override val width: Int = -1,
                override val height: Int = -1,
            ) : Tweet.MediaContainer.MediaObject
        }
    }
}