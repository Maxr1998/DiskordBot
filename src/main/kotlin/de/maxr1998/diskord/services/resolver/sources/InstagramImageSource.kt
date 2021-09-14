package de.maxr1998.diskord.services.resolver.sources

import de.maxr1998.diskord.Constants.INSTAGRAM_BASE_URL
import de.maxr1998.diskord.config.ConfigHelpers
import de.maxr1998.diskord.model.database.CommandEntryEntity
import de.maxr1998.diskord.services.resolver.ImageResolver
import de.maxr1998.diskord.services.resolver.PersistingImageSource
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
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

    override fun supports(content: String): Boolean =
        content.startsWith(INSTAGRAM_BASE_URL)

    override suspend fun resolve(content: String): Result<List<CommandEntryEntity>> {
        val (postRegex, replacement) = INSTAGRAM_GRAPH_REPLACEMENT
        val postUrl = content.replace(postRegex, replacement)

        // Request and parse post metadata from Instagram
        val (shortcode, urls) = try {
            val response = httpClient.get<HttpResponse>(postUrl) {}
            when {
                !response.status.isSuccess() -> return ImageResolver.Status.Unknown()
                response.call.request.url.encodedPath.startsWith("/accounts/login") -> return ImageResolver.Status.RateLimited()
            }

            val body = response.receive<String>()
            val startIndex = body.indexOf(INSTAGRAM_CONTENT_START_MARKER)
            if (startIndex < 0) return ImageResolver.Status.ParsingFailed()
            val endIndex = body.indexOf(INSTAGRAM_CONTENT_END_MARKER, startIndex = startIndex)
            if (endIndex < 0) return ImageResolver.Status.ParsingFailed()
            val sharedDataString = body.substring(startIndex + INSTAGRAM_CONTENT_START_MARKER.length, endIndex)
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
            return ImageResolver.Status.ParsingFailed()
        } catch (e: Exception) {
            logger.error("Error while resolving Instagram URL", e)
            return ImageResolver.Status.Unknown()
        }

        logger.debug("Resolved ${urls.size} images from Instagram post")

        // Download images
        return Result.success(persist(urls, shortcode))
    }

    companion object {
        private val INSTAGRAM_GRAPH_REPLACEMENT = Regex("""($INSTAGRAM_BASE_URL/p/[^/]+/).*""") to "$1"
        private const val INSTAGRAM_CONTENT_START_MARKER = "window._sharedData = "
        private const val INSTAGRAM_CONTENT_END_MARKER = ";</script>"
    }
}