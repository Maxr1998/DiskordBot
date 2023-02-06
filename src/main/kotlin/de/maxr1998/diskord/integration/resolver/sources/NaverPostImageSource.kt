package de.maxr1998.diskord.integration.resolver.sources

import de.maxr1998.diskord.command.dynamic.CommandEntryEntity
import de.maxr1998.diskord.integration.resolver.ImageResolver
import de.maxr1998.diskord.integration.resolver.ImageSource
import de.maxr1998.diskord.util.extension.cleanedCopy
import de.maxr1998.diskord.util.extension.loadJsoupDocument
import de.maxr1998.diskord.util.extension.removeParameters
import io.ktor.client.HttpClient
import io.ktor.http.Parameters
import io.ktor.http.URLParserException
import io.ktor.http.Url
import mu.KotlinLogging
import org.jsoup.Jsoup

private val logger = KotlinLogging.logger {}

class NaverPostImageSource(
    httpClient: HttpClient,
) : ImageSource(httpClient) {

    override fun supports(url: Url): Boolean =
        url.host.matches(NAVER_HOST_REGEX) && url.encodedPath.matches(NAVER_PATH_REGEX)

    override suspend fun resolve(url: Url): Result<ImageResolver.Resolved> {
        val normalizedUrl = url.cleanedCopy(
            host = NAVER_HOST,
            parameters = Parameters.build {
                url.parameters["volumeNo"]?.let { volumeNo ->
                    append("volumeNo", volumeNo)
                }
            },
        )

        val document = httpClient.loadJsoupDocument(normalizedUrl) ?: return ImageResolver.Status.ParsingFailed()

        val clipContent = document.body().selectFirst("#__clipContent")
            ?: return ImageResolver.Status.ParsingFailed()

        val innerDocument = Jsoup.parse(clipContent.html(), normalizedUrl.toString())

        val imageElements = innerDocument.select(".se_component img.se_mediaImage")
        val imageUrls = imageElements.mapNotNull { element ->
            val imageSrc = element.attr("data-src")
            if (imageSrc.isEmpty()) {
                return@mapNotNull null
            }

            val imageUrl = try {
                Url(imageSrc).removeParameters()
            } catch (e: URLParserException) {
                logger.error("Could not parse URL $imageSrc", e)
                return@mapNotNull null
            }

            CommandEntryEntity.image(imageUrl.toString(), normalizedUrl)
        }

        return if (imageUrls.isNotEmpty()) {
            logger.debug("Resolved ${imageUrls.size} images from Naver post")

            Result.success(ImageResolver.Resolved(normalizedUrl, imageUrls))
        } else {
            ImageResolver.Status.Unknown()
        }
    }

    companion object {
        private const val NAVER_HOST = "post.naver.com"
        private val NAVER_HOST_REGEX = Regex("""(?:m\.)?post\.naver\.com""")
        private val NAVER_PATH_REGEX = Regex("""/viewer/postView\.(?:naver|nhn)""")
    }
}