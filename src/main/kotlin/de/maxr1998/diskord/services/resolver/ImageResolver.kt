package de.maxr1998.diskord.services.resolver

import de.maxr1998.diskord.Constants
import de.maxr1998.diskord.Constants.INSTAGRAM_BASE_URL
import de.maxr1998.diskord.Constants.TWITTER_IMAGE_BASE_URL
import de.maxr1998.diskord.config.Config
import de.maxr1998.diskord.config.ConfigHelpers
import de.maxr1998.diskord.model.database.CommandEntryEntity
import de.maxr1998.diskord.model.dto.imgur.ImgurImage
import de.maxr1998.diskord.model.dto.imgur.ImgurResponse
import de.maxr1998.diskord.services.UrlNormalizer
import de.maxr1998.diskord.utils.http.downloadFile
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.features.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.http.userAgent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import org.jsoup.Jsoup
import java.io.File

private val logger = KotlinLogging.logger {}

class ImageResolver(
    private val httpClient: HttpClient,
    private val json: Json,
    configHelpers: ConfigHelpers,
) {
    private val config: Config by configHelpers

    private val filePath = File("files").apply(File::mkdir)

    /**
     * Tries to resolve image urls or images from online services
     */
    suspend fun resolve(content: String, maySave: Boolean): Result<List<CommandEntryEntity>> = when {
        content.startsWith(INSTAGRAM_BASE_URL) -> if (maySave) handleInstagramUrl(content) else Status.Forbidden()
        content.matches(TWITTER_URL_REGEX) -> handleTwitterUrl(content.replace(TWITTER_URL_REGEX, "https://$1"))
        content.matches(IMGUR_ALBUM_URL_REGEX) -> handleImgurAlbumUrl(content.replace(IMGUR_ALBUM_URL_REGEX, "$1"))
        else -> Status.Unsupported()
    }

    private suspend fun handleInstagramUrl(url: String): Result<List<CommandEntryEntity>> {
        val (postRegex, replacement) = INSTAGRAM_GRAPH_REPLACEMENT
        val postUrl = url.replace(postRegex, replacement)

        // Request and parse post metadata from Instagram
        val (shortcode, urls) = try {
            val response = httpClient.get<HttpResponse>(postUrl) {}
            when {
                !response.status.isSuccess() -> return Status.Unknown()
                response.call.request.url.encodedPath.startsWith("/accounts/login") -> return Status.RateLimited()
            }

            val content = response.receive<String>()
            val startIndex = content.indexOf(INSTAGRAM_CONTENT_START_MARKER)
            if (startIndex < 0) return Status.ParsingFailed()
            val endIndex = content.indexOf(INSTAGRAM_CONTENT_END_MARKER, startIndex = startIndex)
            if (endIndex < 0) return Status.ParsingFailed()
            val sharedDataString = content.substring(startIndex + INSTAGRAM_CONTENT_START_MARKER.length, endIndex)
            val sharedData: JsonObject = json.parseToJsonElement(sharedDataString).jsonObject

            // Ugly, blame complex response JSON structure
            val shortcodeMedia = sharedData["entry_data"]!!
                .jsonObject["PostPage"]!!
                .jsonArray.first()
                .jsonObject["graphql"]!!
                .jsonObject["shortcode_media"]!!
                .jsonObject
            val shortcode = shortcodeMedia["shortcode"]!!.jsonPrimitive.content
            val edges = shortcodeMedia["edge_sidecar_to_children"]?.run { jsonObject["edges"]!!.jsonArray }
            val urls = edges?.map { edge ->
                edge.jsonObject["node"]!!.jsonObject["display_url"]!!.jsonPrimitive.content
            } ?: listOf(shortcodeMedia["display_url"]!!.jsonPrimitive.content)

            shortcode to urls
        } catch (e: NullPointerException) {
            return Status.ParsingFailed()
        } catch (e: Exception) {
            logger.error("Error while resolving Instagram URL", e)
            return Status.Unknown()
        }

        // Download images
        val downloadResults = coroutineScope {
            val baseUrl = config.fileServerBaseUrl.orEmpty()
            urls.mapIndexed { index, url ->
                val path = "$shortcode-$index.jpg"
                async {
                    if (httpClient.downloadFile(url, File(filePath, path))) CommandEntryEntity.image("$baseUrl/$path") else null
                }
            }
        }.awaitAll().filterNotNull()

        return Result.success(downloadResults)
    }

    private suspend fun handleTwitterUrl(url: String): Result<List<CommandEntryEntity>> {
        val response = httpClient.get<String>(url) {
            userAgent(Constants.DISCORD_BOT_USER_AGENT)
        }

        val document = Jsoup.parse(response, url)
        val metaTags = document.head().getElementsByTag("meta")

        val imageUrls = metaTags.mapNotNull { element ->
            if (element.attr("property") != "og:image") {
                return@mapNotNull null
            }

            val content = element.attr("content")
            if (!content.startsWith(TWITTER_IMAGE_BASE_URL)) {
                return@mapNotNull null
            }

            CommandEntryEntity.image(UrlNormalizer.normalizeUrls(content))
        }

        return if (imageUrls.isNotEmpty()) {
            logger.debug("Resolved ${imageUrls.size} images from Twitter post")

            Result.success(imageUrls)
        } else {
            Status.Unknown()
        }
    }

    private suspend fun handleImgurAlbumUrl(albumId: String): Result<List<CommandEntryEntity>> {
        val response = try {
            httpClient.get<ImgurResponse<ImgurImage>>(IMGUR_API_ALBUM_IMAGES_PATH.format(albumId)) {
                header(HttpHeaders.Authorization, "Client-ID ${config.imgurClientId}")
            }
        } catch (e: ClientRequestException) {
            return Status.Unsupported()
        } catch (e: SerializationException) {
            return Status.ParsingFailed()
        } catch (e: Exception) {
            logger.error("Error while resolving Imgur album URL", e)
            return Status.Unknown()
        }

        val imageUrls = response.data.map(ImgurImage::link).map { imageUrl ->
            CommandEntryEntity.image(imageUrl)
        }

        return if (imageUrls.isNotEmpty()) {
            logger.debug("Resolved ${imageUrls.size} images from Imgur album")

            Result.success(imageUrls)
        } else {
            Status.Unknown()
        }
    }

    sealed class Status : Exception() {
        object Unsupported : Status()
        sealed class Failure : Status()
        object Forbidden : Failure()
        object RateLimited : Failure()
        object ParsingFailed : Failure()
        object Unknown : Failure()

        operator fun <T> invoke() = Result.failure<T>(this)
    }

    companion object {
        private val INSTAGRAM_GRAPH_REPLACEMENT = Regex("""($INSTAGRAM_BASE_URL/p/[^/]+/).*""") to "$1"
        private const val INSTAGRAM_CONTENT_START_MARKER = "window._sharedData = "
        private const val INSTAGRAM_CONTENT_END_MARKER = ";</script>"

        private val TWITTER_URL_REGEX = Regex("""https?://(?:(?:www|mobile)\.)?(twitter\.com/[A-Za-z_\d]+/status/[\d]+)(?:/|\?.*)?""")

        private val IMGUR_ALBUM_URL_REGEX = Regex("""https?://(?:m\.)?imgur.com/a/(\w+)(?:#\w+)?""")
        private const val IMGUR_API_ALBUM_IMAGES_PATH = "https://api.imgur.com/3/album/%s/images"
    }
}