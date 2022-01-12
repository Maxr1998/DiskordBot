package de.maxr1998.diskord.services.resolver.sources

import de.maxr1998.diskord.model.database.CommandEntryEntity
import de.maxr1998.diskord.services.resolver.ImageResolver
import de.maxr1998.diskord.services.resolver.ImageSource
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.get
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.URLParserException
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.jsoup.Jsoup

private val logger = KotlinLogging.logger {}

class NaverPostImageSource(
    httpClient: HttpClient,
) : ImageSource(httpClient) {

    override fun supports(url: Url): Boolean =
        url.host.matches(NAVER_HOST_REGEX) && url.encodedPath.matches(NAVER_PATH_REGEX)

    override suspend fun resolve(url: Url): Result<ImageResolver.Resolved> {
        val normalizedUrl = url.copy(
            protocol = URLProtocol.HTTPS,
            host = NAVER_HOST,
            parameters = Parameters.build {
                url.parameters["volumeNo"]?.let { volumeNo ->
                    append("volumeNo", volumeNo)
                }
            },
            fragment = "",
            trailingQuery = false,
        )

        val document = httpClient.get<HttpStatement>(normalizedUrl).execute { response ->
            if (response.status.isSuccess() && response.contentType()?.withoutParameters() == ContentType.Text.Html) {
                response.receive<ByteReadChannel>().toInputStream().use { stream ->
                    withContext(Dispatchers.IO) {
                        @Suppress("BlockingMethodInNonBlockingContext")
                        Jsoup.parse(stream, null, normalizedUrl.toString())
                    }
                }
            } else null
        } ?: return ImageResolver.Status.ParsingFailed()

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
                Url(imageSrc).copy(parameters = Parameters.Empty, trailingQuery = false)
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