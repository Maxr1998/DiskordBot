package de.maxr1998.diskord.services.resolver.sources

import de.maxr1998.diskord.Constants
import de.maxr1998.diskord.model.database.CommandEntryEntity
import de.maxr1998.diskord.services.UrlNormalizer
import de.maxr1998.diskord.services.UrlNormalizer.cleanedCopy
import de.maxr1998.diskord.services.resolver.ImageResolver
import de.maxr1998.diskord.services.resolver.ImageSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
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

        val response = httpClient.get<String>(normalizedUrl) {
            userAgent(Constants.DISCORD_BOT_USER_AGENT)
        }

        val document = Jsoup.parse(response, normalizedUrl.toString())
        val metaTags = document.head().getElementsByTag("meta")

        val imageUrls = metaTags.mapNotNull { element ->
            if (element.attr("property") != "og:image") {
                return@mapNotNull null
            }

            val metaContent = element.attr("content")
            if (!metaContent.startsWith(Constants.TWITTER_IMAGE_BASE_URL)) {
                return@mapNotNull null
            }

            CommandEntryEntity.image(UrlNormalizer.normalizeUrls(metaContent), normalizedUrl)
        }

        return if (imageUrls.isNotEmpty()) {
            logger.debug("Resolved ${imageUrls.size} images from Twitter post")

            Result.success(ImageResolver.Resolved(normalizedUrl, imageUrls))
        } else {
            ImageResolver.Status.Unknown()
        }
    }

    companion object {
        private const val TWITTER_HOST = "twitter.com"
        private val TWITTER_HOST_REGEX = Regex("""(?:(?:www|mobile)\.)?twitter\.com""")
        private val TWITTER_STATUS_PATH_REGEX = Regex("""/[A-Za-z_\d]+/status/[\d]+""")
    }
}