package de.maxr1998.diskord.integration.resolver.sources

import de.maxr1998.diskord.command.dynamic.CommandEntryEntity
import de.maxr1998.diskord.config.Config
import de.maxr1998.diskord.config.ConfigHelpers
import de.maxr1998.diskord.integration.resolver.ImageResolver
import de.maxr1998.diskord.integration.resolver.ImageSource
import de.maxr1998.diskord.util.extension.cleanedCopy
import io.ktor.client.HttpClient
import io.ktor.client.features.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlinx.serialization.SerializationException
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class ImgurAlbumSource(
    httpClient: HttpClient,
    configHelpers: ConfigHelpers,
) : ImageSource(httpClient) {

    private val config: Config by configHelpers

    override fun supports(url: Url): Boolean =
        url.host.matches(IMGUR_HOST_REGEX) && url.encodedPath.matches(IMGUR_ALBUM_PATH_REGEX)

    override suspend fun resolve(url: Url): Result<ImageResolver.Resolved> {
        val normalizedUrl = url.cleanedCopy(host = IMGUR_HOST)

        val albumId = normalizedUrl.encodedPath.removePrefix("/a/")
        val response = try {
            httpClient.get<ImgurApi.Response<ImgurApi.Image>>(IMGUR_API_ALBUM_IMAGES_PATH.format(albumId)) {
                header(HttpHeaders.Authorization, "Client-ID ${config.imgurClientId}")
            }
        } catch (e: ClientRequestException) {
            return ImageResolver.Status.Unsupported()
        } catch (e: SerializationException) {
            return ImageResolver.Status.ParsingFailed()
        } catch (e: Exception) {
            logger.error("Error while resolving Imgur album URL", e)
            return ImageResolver.Status.Unknown()
        }

        val imageUrls = response.data.map(ImgurApi.Image::link).map { imageUrl ->
            CommandEntryEntity.image(imageUrl, normalizedUrl)
        }

        return if (imageUrls.isNotEmpty()) {
            logger.debug("Resolved ${imageUrls.size} images from Imgur album")

            Result.success(ImageResolver.Resolved(normalizedUrl, imageUrls))
        } else {
            ImageResolver.Status.Unknown()
        }
    }

    companion object {
        private const val IMGUR_HOST = "imgur.com"
        private val IMGUR_HOST_REGEX = Regex("""(?:m\.)?imgur\.com""")
        private val IMGUR_ALBUM_PATH_REGEX = Regex("""/a/\w+""")
        private const val IMGUR_API_ALBUM_IMAGES_PATH = "https://api.imgur.com/3/album/%s/images"
    }
}