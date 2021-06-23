package de.maxr1998.diskord.utils

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import java.io.File

val logger = KotlinLogging.logger {}

class ImageResolver(
    private val httpClient: HttpClient,
) {
    private val filePath = File("files").apply(File::mkdir)

    /**
     * Tries to resolve image urls or images from online services
     */
    suspend fun resolve(content: String): List<String> = when {
        content.startsWith(INSTAGRAM_BASE_PATH) -> handleInstagramUrl(content)
        else -> emptyList()
    }

    private suspend fun handleInstagramUrl(url: String): List<String> {
        val (graphRegex, replacement) = INSTAGRAM_GRAPH_REPLACEMENT
        val graphUrl = url.replace(graphRegex, replacement)

        // Request and parse post metadata from Instagram
        val (shortcode, urls) = try {
            val response = httpClient.get<JsonObject>(graphUrl)

            // Ugly, blame complex response JSON structure
            val shortcodeMedia = response["graphql"]!!.jsonObject["shortcode_media"]!!.jsonObject
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
            urls.mapIndexed { index, url ->
                async {
                    val path = "$shortcode-$index.jpg"
                    if (httpClient.downloadFile(url, File(filePath, path))) path else null
                }
            }
        }

        return downloadResults.awaitAll().filterNotNull()
    }

    companion object {
        private const val INSTAGRAM_BASE_PATH = "https://www.instagram.com/p/"
        private val INSTAGRAM_GRAPH_REPLACEMENT = Regex("""($INSTAGRAM_BASE_PATH[^/]+/).*""") to "$1?__a=1"
    }
}