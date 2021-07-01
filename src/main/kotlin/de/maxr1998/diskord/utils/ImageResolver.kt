package de.maxr1998.diskord.utils

import de.maxr1998.diskord.config.Config
import de.maxr1998.diskord.config.ConfigHelpers
import io.ktor.client.HttpClient
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import java.io.File

val logger = KotlinLogging.logger {}

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
    suspend fun resolve(content: String): List<String> = when {
        content.startsWith(INSTAGRAM_BASE_URL) -> handleInstagramUrl(content)
        else -> emptyList()
    }

    private suspend fun handleInstagramUrl(url: String): List<String> {
        val (postRegex, replacement) = INSTAGRAM_GRAPH_REPLACEMENT
        val postUrl = url.replace(postRegex, replacement)

        // Request and parse post metadata from Instagram
        val (shortcode, urls) = try {
            val response = httpClient.get<String>(postUrl) {
                header(HttpHeaders.Referrer, INSTAGRAM_BASE_URL)
                cookie("ig_pr", "1")
            }

            val startIndex = response.indexOf(CONTENT_START_MARKER)
            if (startIndex < 0) return emptyList()
            val endIndex = response.indexOf(CONTENT_END_MARKER, startIndex = startIndex)
            if (endIndex < 0) return emptyList()
            val sharedDataString = response.substring(startIndex + CONTENT_START_MARKER.length, endIndex)
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
        } catch (e: Exception) {
            logger.error("Error while resolving Instagram URL", e)
            return emptyList()
        }

        // Download images
        val downloadResults = coroutineScope {
            val baseUrl = config.fileServerBaseUrl.orEmpty()
            urls.mapIndexed { index, url ->
                val path = "$shortcode-$index.jpg"
                async {
                    if (httpClient.downloadFile(url, File(filePath, path))) "$baseUrl/$path" else null
                }
            }
        }

        return downloadResults.awaitAll().filterNotNull()
    }

    companion object {
        private const val INSTAGRAM_BASE_URL = "https://www.instagram.com"
        private val INSTAGRAM_GRAPH_REPLACEMENT = Regex("""($INSTAGRAM_BASE_URL/p/[^/]+/).*""") to "$1"
        private const val CONTENT_START_MARKER = "window._sharedData = "
        private const val CONTENT_END_MARKER = ";</script>"
    }
}