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

    override suspend fun resolve(url: Url): Result<ImageResolver.Resolved> {
        val normalizedUrl = url.cleanedCopy(host = TWITTER_HOST)
        val id = TWITTER_STATUS_PATH_REGEX.matchEntire(normalizedUrl.encodedPath)?.groupValues?.getOrNull(1)

        if (id == null) {
            logger.warn("Failed to extract tweet id from $normalizedUrl")
            return ImageResolver.Status.Unknown()
        }

        val tweetApiUrl = URLBuilder(
            protocol = URLProtocol.HTTPS,
            host = TWITTER_API_HOST,
            pathSegments = listOf("2", "tweets", id),
            parameters = ParametersBuilder().apply {
                append("expansions", "attachments.media_keys")
                append("media.fields", "type,url,width,height")
            }.build(),
        ).build()

        val tweet = try {
            httpClient.get(tweetApiUrl) {
                header(HttpHeaders.Authorization, "${AuthScheme.Bearer} ${config.twitterToken}")
            }.body<TwitterApi.Tweet>()
        } catch (e: SerializationException) {
            logger.error("Failed to parse Twitter API response", e)
            return ImageResolver.Status.ParsingFailed()
        } catch (e: ClientRequestException) {
            logger.error("Request to Twitter API failed", e)
            return ImageResolver.Status.Unknown()
        }

        if (tweet.includes == null) {
            logger.warn("Twitter API returned no includes for $normalizedUrl")
            return ImageResolver.Status.Unknown()
        }

        val imageUrls = tweet.includes.media.mapNotNull { mediaItem ->
            when (mediaItem.type) {
                TwitterApi.MediaObject.TYPE_PHOTO -> when {
                    mediaItem.url != null -> {
                        CommandEntryEntity.image(
                            url = UrlNormalizer.normalizeUrls(mediaItem.url),
                            source = normalizedUrl,
                            width = mediaItem.width,
                            height = mediaItem.height,
                        )
                    }
                    else -> null
                }
                TwitterApi.MediaObject.TYPE_VIDEO -> resolveVideo(normalizedUrl)
                else -> null
            }
        }

        return when {
            imageUrls.isNotEmpty() -> {
                logger.debug("Resolved ${imageUrls.size} images or videos from $normalizedUrl")
                Result.success(ImageResolver.Resolved(normalizedUrl, imageUrls))
            }
            else -> {
                logger.warn("Twitter returned no results for $normalizedUrl")
                ImageResolver.Status.Unknown()
            }
        }
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
        private val TWITTER_HOST_REGEX = Regex("""(?:(?:www|mobile)\.)?twitter\.com""")
        private val TWITTER_STATUS_PATH_REGEX = Regex("""/[A-Za-z_\d]+/status/([\d]+)""")
    }
}