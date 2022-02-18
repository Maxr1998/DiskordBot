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
import kotlinx.serialization.json.JsonObject
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
            val startIndex = body.indexOf(INSTAGRAM_CONTENT_START_MARKER)
            if (startIndex < 0) return ImageResolver.Status.ParsingFailed()
            val endIndex = body.indexOf(INSTAGRAM_CONTENT_END_MARKER, startIndex = startIndex)
            if (endIndex < 0) return ImageResolver.Status.ParsingFailed()
            val sharedDataString = body.substring(startIndex, endIndex).removeSuffix(")")
            val sharedData: JsonObject = json.parseToJsonElement(sharedDataString).jsonObject

            // Ugly, blame complex response JSON structure
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
                edge.jsonObject["node"]!!.jsonObject["display_url"]!!.jsonPrimitive.content
            } ?: listOf(shortcodeMedia["display_url"]!!.jsonPrimitive.content)

            shortcode to urls
        } catch (e: NullPointerException) {
            logger.error("Couldn't parse response")
            return ImageResolver.Status.ParsingFailed()
        } catch (e: Exception) {
            logger.error("Error while resolving Instagram URL", e)
            return ImageResolver.Status.Unknown()
        } finally {
            lastRequestTimeMillis = System.currentTimeMillis()
            mutex.unlock()
        }

        logger.debug("Resolved ${urls.size} images from Instagram post")

        // Download images
        return Result.success(ImageResolver.Resolved(normalizedUrl, persist(urls, normalizedUrl, shortcode)))
    }

    companion object {
        private const val COOLDOWN_MS = 3000L

        const val INSTAGRAM_HOST = "www.instagram.com"
        private val INSTAGRAM_POST_PATH_REGEX = Regex("""(?:/[a-z0-9_.]{1,30})?(/p/[^/]+)/?""")
        private const val INSTAGRAM_CONTENT_START_MARKER = """{"graphql":{"shortcode_media":{"""
        private const val INSTAGRAM_CONTENT_END_MARKER = ";</script>"
    }
}