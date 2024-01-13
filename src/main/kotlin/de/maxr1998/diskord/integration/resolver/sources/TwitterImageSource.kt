package de.maxr1998.diskord.integration.resolver.sources

import de.maxr1998.diskord.command.dynamic.CommandEntryEntity
import de.maxr1998.diskord.config.Config
import de.maxr1998.diskord.config.ConfigHelpers
import de.maxr1998.diskord.integration.UrlNormalizer
import de.maxr1998.diskord.integration.resolver.ImageResolver
import de.maxr1998.diskord.integration.resolver.ImageSource
import de.maxr1998.diskord.util.extension.cleanedCopy
import de.maxr1998.diskord.util.extension.removeParameters
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.ParametersBuilder
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.auth.AuthScheme
import kotlinx.serialization.SerializationException
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class TwitterImageSource(
    httpClient: HttpClient,
    configHelpers: ConfigHelpers,
) : ImageSource(httpClient) {

    private val config: Config by configHelpers

    override fun supports(url: Url): Boolean =
        url.host.matches(TWITTER_HOST_REGEX) && url.encodedPath.matches(TWITTER_STATUS_PATH_REGEX)

    @Suppress("CyclomaticComplexMethod")
    override suspend fun resolve(url: Url): Result<ImageResolver.Resolved> {
        val normalizedUrl = url.cleanedCopy(host = TWITTER_HOST)
        val id = TWITTER_STATUS_PATH_REGEX.matchEntire(normalizedUrl.encodedPath)?.groupValues?.getOrNull(1)

        if (id == null) {
            logger.warn("Failed to extract tweet id from $normalizedUrl")
            return ImageResolver.Status.Unknown()
        }

        val tweet = try {
            when (config.twitterApiVersion) {
                1 -> resolveTweetV1_1(id)
                2 -> resolveTweetV2(id)
                0 -> resolveTweetEmbed(id).getOrElse { failure ->
                    return Result.failure(failure as ImageResolver.Status)
                }
                else -> {
                    logger.warn("Unsupported Twitter API version {}", config.twitterApiVersion)
                    return ImageResolver.Status.Unsupported()
                }
            }
        } catch (e: SerializationException) {
            logger.error("Failed to parse Twitter API response", e)
            return ImageResolver.Status.ParsingFailed()
        } catch (e: ClientRequestException) {
            logger.error("Request to Twitter API failed", e)
            return ImageResolver.Status.Unknown()
        }

        val mediaContainer = tweet.mediaContainer

        if (mediaContainer == null) {
            logger.warn("Twitter API returned no includes/extensions for $normalizedUrl")
            return ImageResolver.Status.Unknown()
        }

        val imageUrls = mediaContainer.media.mapNotNull { mediaItem ->
            when (mediaItem.type) {
                TwitterApi.Tweet.MediaContainer.MediaObject.TYPE_PHOTO -> mediaItem.url?.let { mediaUrl ->
                    CommandEntryEntity.image(
                        url = UrlNormalizer.normalizeUrls(mediaUrl),
                        source = normalizedUrl,
                        width = mediaItem.width,
                        height = mediaItem.height,
                    )
                }
                TwitterApi.Tweet.MediaContainer.MediaObject.TYPE_VIDEO -> resolveVideo(normalizedUrl)
                else -> null
            }
        }

        return when {
            imageUrls.isNotEmpty() -> {
                logger.debug("Resolved {} images or videos from {}", imageUrls.size, normalizedUrl)
                Result.success(ImageResolver.Resolved(normalizedUrl, imageUrls))
            }
            else -> {
                logger.warn("Twitter returned no results for $normalizedUrl")
                ImageResolver.Status.Unknown()
            }
        }
    }

    @Suppress("FunctionName")
    private suspend inline fun resolveTweetV1_1(id: String): TwitterApi.Tweet {
        val tweetApiUrl = URLBuilder(
            protocol = URLProtocol.HTTPS,
            host = TWITTER_API_HOST,
            pathSegments = listOf("1.1", "statuses", "show", id),
            parameters = ParametersBuilder().apply {
                append("include_entities", "false")
                append("tweet_mode", "extended")
            }.build(),
        ).build()

        return httpClient.get(tweetApiUrl) {
            header(HttpHeaders.Authorization, "${AuthScheme.Bearer} ${config.twitterToken}")
        }.body<TwitterApi.TweetV1_1>()
    }

    private suspend inline fun resolveTweetV2(id: String): TwitterApi.Tweet {
        val tweetApiUrl = URLBuilder(
            protocol = URLProtocol.HTTPS,
            host = TWITTER_API_HOST,
            pathSegments = listOf("2", "tweets", id),
            parameters = ParametersBuilder().apply {
                append("expansions", "attachments.media_keys")
                append("media.fields", "type,url,width,height")
            }.build(),
        ).build()

        return httpClient.get(tweetApiUrl) {
            header(HttpHeaders.Authorization, "${AuthScheme.Bearer} ${config.twitterToken}")
        }.body<TwitterApi.TweetV2>()
    }

    private suspend inline fun resolveTweetEmbed(id: String): Result<TwitterApi.Tweet> {
        val tweetApiUrl = URLBuilder(
            protocol = URLProtocol.HTTPS,
            host = "cdn.syndication.twimg.com",
            pathSegments = listOf("tweet-result"),
            parameters = ParametersBuilder().apply {
                append("id", id)
                append("lang", "en")
                append("token", generatedEmbedToken(id))
            }.build(),
        ).build()

        return when (val response = httpClient.get(tweetApiUrl).body<TwitterApi.TweetEmbedResponse>()) {
            is TwitterApi.TweetEmbed -> Result.success(response)
            is TwitterApi.TweetTombstone -> {
                if (response.text.startsWith("Age-restricted adult content.")) {
                    logger.warn("Twitter returned age-restricted content for $tweetApiUrl")
                    return ImageResolver.Status.AgeRestricted()
                } else {
                    logger.warn("Twitter returned tombstone \"{}\" for {}", response.text, tweetApiUrl)
                    return ImageResolver.Status.Unknown()
                }
            }
        }
    }

    @Suppress("MagicNumber")
    private fun generatedEmbedToken(tweetId: String): String {
        val tokenDouble = tweetId.toDouble() / 1e15 * Math.PI

        val tokenInt = tokenDouble.toInt()
        var fraction = tokenDouble - tokenInt

        var token = tokenInt.toString(EMBED_TOKEN_RADIX)
        while (token.length < EMBED_TOKEN_LENGTH) {
            fraction *= EMBED_TOKEN_RADIX
            val digit = fraction.toInt()
            fraction -= digit
            token += digit.toString(EMBED_TOKEN_RADIX)
        }

        return token
    }

    /**
     * Try to resolve a static video url from a Twitter post with fxtwitter.com
     */
    private suspend fun resolveVideo(twitterUrl: Url): CommandEntryEntity? {
        val fixedUrl = URLBuilder(twitterUrl).apply {
            host = FXTWITTER_HOST
        }.build()

        val response = httpClient.get(fixedUrl)

        // Normalize redirected url
        val redirectUrl = response.call.request.url.removeParameters().toString()

        return when {
            redirectUrl.startsWith(TwitterApi.TWITTER_VIDEO_BASE_URL) -> {
                CommandEntryEntity.video(redirectUrl, twitterUrl)
            }
            else -> null
        }
    }

    companion object {
        private const val TWITTER_HOST = "twitter.com"
        private const val TWITTER_API_HOST = "api.twitter.com"
        private const val FXTWITTER_HOST = "d.fxtwitter.com"
        private val TWITTER_HOST_REGEX = Regex("""(?:(?:www|mobile)\.)?(?:twitter|x)\.com""")
        private val TWITTER_STATUS_PATH_REGEX = Regex("""/[A-Za-z_\d]+/status/([\d]+)""")
        private const val EMBED_TOKEN_RADIX = 36

        /**
         * The length is usually specified by the double precision in JavaScript,
         * but as it's not verified anyway we can just use a fixed length.
         */
        private const val EMBED_TOKEN_LENGTH = 10
    }
}