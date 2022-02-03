package de.maxr1998.diskord.services.resolver.sources

import de.maxr1998.diskord.Constants
import de.maxr1998.diskord.model.database.CommandEntryEntity
import de.maxr1998.diskord.services.UrlNormalizer
import de.maxr1998.diskord.services.UrlNormalizer.cleanedCopy
import de.maxr1998.diskord.services.resolver.ImageResolver
import de.maxr1998.diskord.services.resolver.ImageSource
import io.ktor.client.HttpClient
import io.ktor.client.features.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.userAgent
import mu.KotlinLogging
import org.jsoup.Jsoup

private val logger = KotlinLogging.logger {}

class TwitterImageSource(
    httpClient: HttpClient,
) : ImageSource(httpClient) {

    override fun supports(url: Url): Boolean =
        url.host.matches(TWITTER_HOST_REGEX) && url.encodedPath.matches(TWITTER_STATUS_PATH_REGEX)

    override suspend fun resolve(url: Url): Result<ImageResolver.Resolved> {
        val normalizedUrl = url.cleanedCopy(host = TWITTER_HOST)

        val response = try {
            httpClient.get<String>(normalizedUrl) {
                userAgent(Constants.DISCORD_BOT_USER_AGENT)
            }
        } catch (e: ClientRequestException) {
            return ImageResolver.Status.Unknown()
        }

        val document = Jsoup.parse(response, normalizedUrl.toString())
        val metaTags = document.head().getElementsByTag("meta")
        val ogImageContents = metaTags.mapNotNull { element ->
            when (element.attr("property")) {
                "og:image" -> element.attr("content")
                else -> null
            }
        }

        val imageUrls = ogImageContents.mapNotNull { imageUrl ->
            when {
                imageUrl.startsWith(Constants.TWITTER_IMAGE_BASE_URL) -> {
                    CommandEntryEntity.image(UrlNormalizer.normalizeUrls(imageUrl), normalizedUrl)
                }
                imageUrl.startsWith(Constants.TWITTER_VIDEO_THUMB_BASE_URL) -> {
                    resolveVideo(normalizedUrl)
                }
                else -> null
            }
        }

        return when {
            imageUrls.isNotEmpty() -> {
                logger.debug("Resolved ${imageUrls.size} images or videos from Twitter post")

                Result.success(ImageResolver.Resolved(normalizedUrl, imageUrls))
            }
            else -> ImageResolver.Status.Unknown()
        }
    }

    /**
     * Try to resolve a static video url from a Twitter post with fxtwitter.com
     */
    private suspend fun resolveVideo(twitterUrl: Url): CommandEntryEntity? {
        val fixedUrl = twitterUrl.copy(host = FXTWITTER_HOST)

        val response = httpClient.get<HttpResponse>(fixedUrl)

        // Normalize redirected url
        val redirectUrl = response.call.request.url.copy(
            parameters = Parameters.Empty,
            fragment = "",
            trailingQuery = false,
        ).toString()

        return when {
            redirectUrl.startsWith(Constants.TWITTER_VIDEO_BASE_URL) -> {
                CommandEntryEntity.video(redirectUrl, twitterUrl)
            }
            else -> null
        }
    }

    companion object {
        private const val TWITTER_HOST = "twitter.com"
        private const val FXTWITTER_HOST = "d.fxtwitter.com"
        private val TWITTER_HOST_REGEX = Regex("""(?:(?:www|mobile)\.)?twitter\.com""")
        private val TWITTER_STATUS_PATH_REGEX = Regex("""/[A-Za-z_\d]+/status/[\d]+""")
    }
}