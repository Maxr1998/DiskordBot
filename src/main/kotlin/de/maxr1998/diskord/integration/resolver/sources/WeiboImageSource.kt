package de.maxr1998.diskord.integration.resolver.sources

import de.maxr1998.diskord.command.dynamic.CommandEntryEntity
import de.maxr1998.diskord.integration.resolver.ImageResolver
import de.maxr1998.diskord.integration.resolver.ImageSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class WeiboImageSource(
    private val json: Json,
    httpClient: HttpClient,
) : ImageSource(httpClient) {

    override fun supports(url: Url): Boolean =
        url.host == WEIBO_HOST && url.encodedPath.matches(WEIBO_STATUS_PATH_REGEX)

    override suspend fun resolve(url: Url): Result<ImageResolver.Resolved> {
        val normalizedUrl = URLBuilder(url).apply {
            encodedPath = encodedPath.replace("detail", "status").removeSuffix("/")
        }.build()

        // Request and parse post metadata to extract image urls from Weibo
        val imageUrls = try {
            val response = httpClient.get(normalizedUrl) {
                header(HttpHeaders.Accept, ContentType.Text.Html)
            }
            if (!response.status.isSuccess()) return ImageResolver.Status.Unknown()

            val body = response.bodyAsText()
            val startIndex = body.indexOf(RENDER_DATA_START_MARKER) + RENDER_DATA_START_MARKER.length
            if (startIndex < 0) return ImageResolver.Status.ParsingFailed()
            val endIndex = body.indexOf(RENDER_DATA_END_MARKER, startIndex = startIndex)
            if (endIndex < 0) return ImageResolver.Status.ParsingFailed()
            val renderDataString = body.substring(startIndex, endIndex)
            val renderData: JsonObject = json.parseToJsonElement(renderDataString).jsonObject

            val status = requireNotNull(renderData["status"]).jsonObject
            val picIds = requireNotNull(status["pic_ids"]).jsonArray.map { element -> element.jsonPrimitive.content }
            picIds.map { id ->
                val imageUrl = "https://wx4.sinaimg.cn/original/$id.jpg"
                CommandEntryEntity.image(imageUrl, normalizedUrl)
            }
        } catch (e: IllegalArgumentException) {
            logger.error("Couldn't parse response")
            return ImageResolver.Status.ParsingFailed()
        } catch (e: Exception) {
            logger.error("Error while resolving Weibo URL", e)
            return ImageResolver.Status.Unknown()
        }

        logger.debug("Resolved ${imageUrls.size} images from Weibo post")

        // Download images
        return Result.success(ImageResolver.Resolved(normalizedUrl, imageUrls))
    }

    companion object {
        const val WEIBO_HOST = "m.weibo.cn"
        private val WEIBO_STATUS_PATH_REGEX = Regex("""/(?:status|detail)/\w+/?""")
        private const val RENDER_DATA_START_MARKER = "var \$render_data = ["
        private const val RENDER_DATA_END_MARKER = "][0] || {};"
    }
}