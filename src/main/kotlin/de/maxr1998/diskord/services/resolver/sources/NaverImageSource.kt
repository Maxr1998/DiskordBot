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

class NaverImageSource(
    httpClient: HttpClient,
) : ImageSource(httpClient) {
    override fun supports(content: String): Boolean =
        content.matches(NAVER_URL_REGEX)

    override suspend fun resolve(content: String): Result<List<CommandEntryEntity>> {
        val url = content.replace(NAVER_URL_REGEX, "https://$1")

        val document = httpClient.get<HttpStatement>(url).execute { response ->
            if (response.status.isSuccess() && response.contentType()?.withoutParameters() == ContentType.Text.Html) {
                response.receive<ByteReadChannel>().toInputStream().use { stream ->
                    withContext(Dispatchers.IO) {
                        @Suppress("BlockingMethodInNonBlockingContext")
                        Jsoup.parse(stream, null, url)
                    }
                }
            } else null
        } ?: return ImageResolver.Status.ParsingFailed()

        val clipContent = document.body().selectFirst("#__clipContent")
            ?: return ImageResolver.Status.ParsingFailed()

        val innerDocument = Jsoup.parse(clipContent.html(), url)

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

            CommandEntryEntity.image(imageUrl.toString())
        }

        return if (imageUrls.isNotEmpty()) {
            logger.debug("Resolved ${imageUrls.size} images from Naver post")

            Result.success(imageUrls)
        } else {
            ImageResolver.Status.Unknown()
        }
    }

    companion object {
        private val NAVER_URL_REGEX = Regex("""https?://(?:m\.)?(post\.naver\.com/viewer/postView\.nhn\?volumeNo=[\d]+)""")
    }
}