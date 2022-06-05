package de.maxr1998.diskord.integration.resolver.sources

import de.maxr1998.diskord.config.ConfigHelpers
import de.maxr1998.diskord.integration.resolver.ImageResolver
import de.maxr1998.diskord.integration.resolver.PersistingImageSource
import de.maxr1998.diskord.util.extension.cleanedCopy
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class InstagramImageSource(
    private val json: Json,
    httpClient: HttpClient,
    configHelpers: ConfigHelpers,
) : PersistingImageSource(httpClient, configHelpers) {

    private val mutex = Mutex()
    private var lastRequestTimeMillis = 0L

    override fun supports(url: Url): Boolean =
        url.host == INSTAGRAM_HOST && url.encodedPath.matches(INSTAGRAM_POST_PATH_REGEX)

    override suspend fun resolve(url: Url): Result<ImageResolver.Resolved> {
        val normalizedUrl = url.cleanedCopy(
            encodedPath = url.encodedPath.replace(INSTAGRAM_POST_PATH_REGEX, "$1/"),
        )

        // Request and parse post metadata from Instagram
        val (shortcode, urls) = try {
            mutex.lock()

            // Apply cooldown, negative delays are ignored
            delay(lastRequestTimeMillis + COOLDOWN_MS - System.currentTimeMillis())

            val response = httpClient.get<HttpResponse>(normalizedUrl) {
                header(HttpHeaders.Accept, ContentType.Text.Html)
            }
            when {
                !response.status.isSuccess() -> return ImageResolver.Status.Unknown()
                response.call.request.url.encodedPath.startsWith("/accounts/login") -> return ImageResolver.Status.RateLimited()
            }

            val body = response.receive<String>()

            parseGraphql(body) ?: parseItemsJson(body) ?: return ImageResolver.Status.ParsingFailed()
        } catch (e: Exception) {
            logger.error("Error while resolving Instagram URL", e)
            return ImageResolver.Status.Unknown()
        } finally {
            lastRequestTimeMillis = System.currentTimeMillis()
            mutex.unlock()
        }

        // Download images
        val imageUrls = persist(urls, normalizedUrl, shortcode)

        return when {
            imageUrls.isNotEmpty() -> {
                logger.debug("Resolved ${imageUrls.size} images or videos from Instagram post")

                Result.success(ImageResolver.Resolved(normalizedUrl, imageUrls))
            }
            else -> ImageResolver.Status.Unknown()
        }
    }

    /**
     * GraphQL JSON embedded in the page
     */
    private fun parseGraphql(body: String): Pair<String, List<String?>>? = try {
        val startIndex = body.indexOf(INSTAGRAM_SHARED_DATA_START_MARKER)
        require(startIndex >= 0) { "Couldn't find start marker" }
        val endIndex = body.indexOf(INSTAGRAM_CONTENT_END_MARKER, startIndex = startIndex)
        require(endIndex >= 0) { "Couldn't find end marker" }
        val sharedDataString = body.substring(startIndex + INSTAGRAM_SHARED_DATA_START_MARKER.length, endIndex)
        val sharedData: JsonObject = json.parseToJsonElement(sharedDataString).jsonObject

        val graphqlRoot = when {
            "graphql" in sharedData -> sharedData
            else -> sharedData["entry_data"]!!
                .jsonObject["PostPage"]!!
                .jsonArray.first()
                .jsonObject
        }

        val shortcodeMedia = graphqlRoot["graphql"]!!.jsonObject["shortcode_media"]!!.jsonObject
        val shortcode = shortcodeMedia["shortcode"]!!.jsonPrimitive.content
        val edges = shortcodeMedia["edge_sidecar_to_children"]?.run { jsonObject["edges"]!!.jsonArray }
        val urls = edges?.map { edge ->
            val node = edge.jsonObject["node"]!!.jsonObject
            when {
                node["is_video"]!!.jsonPrimitive.boolean -> null
                else -> node["display_url"]!!.jsonPrimitive.content
            }
        } ?: listOf(shortcodeMedia["display_url"]!!.jsonPrimitive.content)

        shortcode to urls
    } catch (e: Exception) {
        logger.error("Couldn't parse response", e)
        null
    }

    /**
     * A simpler JSON structure, temporarily used in early 2022 with higher-quality pictures, possibly an A/B test.
     */
    private fun parseItemsJson(body: String): Pair<String, List<String?>>? = try {
        val startIndex = body.indexOf(INSTAGRAM_CONTENT_START_MARKER)
        require(startIndex >= 0) { "Couldn't find start marker" }
        val endIndex = body.indexOf(INSTAGRAM_CONTENT_END_MARKER, startIndex = startIndex)
        require(endIndex >= 0) { "Couldn't find end marker" }
        val additionalDataString = body.substring(startIndex, endIndex).removeSuffix(")")
        val additionalData: JsonObject = json.parseToJsonElement(additionalDataString).jsonObject

        val item = additionalData["items"]!!.jsonArray.first().jsonObject
        val shortcode = item["code"]!!.jsonPrimitive.content

        val mediaElements: List<JsonElement> = item["carousel_media"]?.jsonArray ?: listOf(item)
        val urls = mediaElements.map { mediaElement ->
            val mediaObject = mediaElement.jsonObject
            when (mediaObject["media_type"]!!.jsonPrimitive.int) {
                1 /* image */ -> {
                    val imageCandidates = mediaObject["image_versions2"]!!.jsonObject["candidates"]!!.jsonArray
                    imageCandidates.first().jsonObject["url"]!!.jsonPrimitive.content
                }
                else -> null
            }
        }

        shortcode to urls
    } catch (e: Exception) {
        logger.error("Couldn't parse response", e)
        null
    }

    companion object {
        private const val COOLDOWN_MS = 3000L

        const val INSTAGRAM_HOST = "www.instagram.com"
        private val INSTAGRAM_POST_PATH_REGEX = Regex("""(?:/[a-z\d_.]{1,30})?(/p/[^/]+)/?""")
        private const val INSTAGRAM_SHARED_DATA_START_MARKER = """window._sharedData = """
        private const val INSTAGRAM_CONTENT_START_MARKER = """{"items":["""
        private const val INSTAGRAM_CONTENT_END_MARKER = ";</script>"
    }
}