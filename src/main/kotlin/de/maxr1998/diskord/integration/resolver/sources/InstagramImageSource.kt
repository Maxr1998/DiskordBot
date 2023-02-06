package de.maxr1998.diskord.integration.resolver.sources

import de.maxr1998.diskord.config.ConfigHelpers
import de.maxr1998.diskord.integration.resolver.ImageResolver
import de.maxr1998.diskord.integration.resolver.PersistingImageSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class InstagramImageSource(
    httpClient: HttpClient,
    configHelpers: ConfigHelpers,
) : PersistingImageSource(httpClient, configHelpers) {

    private val mutex = Mutex()
    private var lastRequestTimeMillis = 0L

    override fun supports(url: Url): Boolean =
        url.host == INSTAGRAM_HOST && url.encodedPath.matches(INSTAGRAM_POST_PATH_REGEX)

    override suspend fun resolve(url: Url): Result<ImageResolver.Resolved> {
        val normalizedUrl = URLBuilder(url).apply {
            encodedPath = encodedPath.replace(INSTAGRAM_POST_PATH_REGEX, "$1/")
        }.build()

        // Request and parse post metadata from Instagram
        val (shortcode, urls) = try {
            mutex.lock()

            // Apply cooldown, negative delays are ignored
            delay(lastRequestTimeMillis + COOLDOWN_MS - System.currentTimeMillis())

            // Get media id from URL
            val mediaId: Long = httpClient.get {
                url {
                    host = config.instagrapiUrl
                    port = INSTAGRAPI_PORT
                    pathSegments = listOf("media", "pk_from_url")
                    parameter("url", normalizedUrl.toString())
                }
            }.body()

            // Resolve media info and image URLs via instagrapi
            val mediaInfo: Instagrapi.MediaInfo = httpClient.submitForm(
                formParameters = Parameters.build {
                    append("sessionid", config.instagramSession)
                    append("pk", mediaId.toString())
                },
            ) {
                url {
                    host = config.instagrapiUrl
                    port = INSTAGRAPI_PORT
                    pathSegments = listOf("media", "info")
                }
                header(HttpHeaders.Accept, ContentType.Application.Json)
            }.body()

            val urls = mediaInfo.thumbnailUrl?.let(::listOf)
                ?: mediaInfo.resources.map(Instagrapi.MediaInfo.Resource::thumbnailUrl)

            mediaInfo.code to urls
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
                logger.debug("Resolved ${imageUrls.size} images or videos from $normalizedUrl")
                Result.success(ImageResolver.Resolved(normalizedUrl, imageUrls))
            }
            else -> {
                logger.warn("Instagram returned no results for $normalizedUrl")
                ImageResolver.Status.Unknown()
            }
        }
    }

    companion object {
        private const val COOLDOWN_MS = 3000L

        const val INSTAGRAM_HOST = "www.instagram.com"
        private val INSTAGRAM_POST_PATH_REGEX = Regex("""(?:/[a-z\d_.]{1,30})?(/p/[^/]+)/?""")
        private const val INSTAGRAPI_PORT = 8000
    }
}