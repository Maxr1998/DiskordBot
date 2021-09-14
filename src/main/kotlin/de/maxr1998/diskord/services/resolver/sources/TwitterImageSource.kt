package de.maxr1998.diskord.services.resolver.sources

import de.maxr1998.diskord.Constants
import de.maxr1998.diskord.model.database.CommandEntryEntity
import de.maxr1998.diskord.services.UrlNormalizer
import de.maxr1998.diskord.services.resolver.ImageResolver
import de.maxr1998.diskord.services.resolver.ImageSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.userAgent
import mu.KotlinLogging
import org.jsoup.Jsoup

private val logger = KotlinLogging.logger {}

class TwitterImageSource(
    httpClient: HttpClient,
) : ImageSource(httpClient) {

    override fun supports(content: String): Boolean =
        content.matches(TWITTER_URL_REGEX)

    override suspend fun resolve(content: String): Result<List<CommandEntryEntity>> {
        val url = content.replace(TWITTER_URL_REGEX, "https://$1")

        val response = httpClient.get<String>(url) {
            userAgent(Constants.DISCORD_BOT_USER_AGENT)
        }

        val document = Jsoup.parse(response, url)
        val metaTags = document.head().getElementsByTag("meta")

        val imageUrls = metaTags.mapNotNull { element ->
            if (element.attr("property") != "og:image") {
                return@mapNotNull null
            }

            val metaContent = element.attr("content")
            if (!metaContent.startsWith(Constants.TWITTER_IMAGE_BASE_URL)) {
                return@mapNotNull null
            }

            CommandEntryEntity.image(UrlNormalizer.normalizeUrls(metaContent))
        }

        return if (imageUrls.isNotEmpty()) {
            logger.debug("Resolved ${imageUrls.size} images from Twitter post")

            Result.success(imageUrls)
        } else {
            ImageResolver.Status.Unknown()
        }
    }

    companion object {
        private val TWITTER_URL_REGEX = Regex("""https?://(?:(?:www|mobile)\.)?(twitter\.com/[A-Za-z_\d]+/status/[\d]+)(?:/|\?.*)?""")
    }
}