package de.maxr1998.diskord.services.resolver.sources

import de.maxr1998.diskord.model.database.CommandEntryEntity
import de.maxr1998.diskord.services.resolver.ImageResolver
import de.maxr1998.diskord.services.resolver.ImageSource
import de.maxr1998.diskord.utils.http.loadJsoupDocument
import io.ktor.client.HttpClient
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.http.URLBuilder
import io.ktor.http.URLParserException
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class NaverEntertainImageSource(
    httpClient: HttpClient,
) : ImageSource(httpClient) {

    override fun supports(url: Url): Boolean =
        (url.host == NAVER_HOST && url.encodedPath == NAVER_PATH) ||
            (url.host == NAVER_MOBILE_HOST && url.encodedPath.matches(NAVER_MOBILE_PATH_REGEX))

    override suspend fun resolve(url: Url): Result<ImageResolver.Resolved> {
        val normalizedUrl = URLBuilder(
            protocol = URLProtocol.HTTPS,
            host = NAVER_HOST,
            encodedPath = NAVER_PATH,
            parameters = ParametersBuilder().apply {
                val matchGroups = NAVER_MOBILE_PATH_REGEX.matchEntire(url.encodedPath)?.groupValues
                (url.parameters["oid"] ?: matchGroups?.getOrNull(1))?.let { oid ->
                    append("oid", oid)
                }
                (url.parameters["aid"] ?: matchGroups?.getOrNull(2))?.let { aid ->
                    append("aid", aid)
                }
            },
        ).build()

        val document = httpClient.loadJsoupDocument(normalizedUrl)
            ?: return ImageResolver.Status.ParsingFailed()

        val articleContent = document.body().selectFirst("#content .end_ct .end_ct_area .end_body_wrp")
            ?: return ImageResolver.Status.ParsingFailed()

        val imageElements = articleContent.select(".end_photo_org img")
        val imageUrls = imageElements.mapNotNull { element ->
            val imageSrc = element.attr("src")
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
            logger.debug("Resolved ${imageUrls.size} images from Naver Entertain post")

            Result.success(ImageResolver.Resolved(normalizedUrl, imageUrls))
        } else {
            ImageResolver.Status.Unknown()
        }
    }

    companion object {
        private const val NAVER_HOST = "entertain.naver.com"
        private const val NAVER_MOBILE_HOST = "n.news.naver.com"
        private const val NAVER_PATH = "/read"
        private val NAVER_MOBILE_PATH_REGEX = Regex("""/entertain/article/(\d+)/(\d+)""")
    }
}