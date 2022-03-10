package de.maxr1998.diskord.integration.resolver

import de.maxr1998.diskord.command.dynamic.CommandEntryEntity
import de.maxr1998.diskord.config.Config
import de.maxr1998.diskord.config.ConfigHelpers
import de.maxr1998.diskord.util.http.downloadFile
import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File

abstract class PersistingImageSource(
    httpClient: HttpClient,
    configHelpers: ConfigHelpers,
) : ImageSource(httpClient) {

    protected val config: Config by configHelpers

    private val downloadPath = File("files").apply(File::mkdir)

    /**
     * Download images from [urls] and return a list of [image entities][CommandEntryEntity] on success,
     * or an empty list if any of the images failed to download.
     */
    suspend fun persist(urls: List<String?>, source: Url, prefix: String): List<CommandEntryEntity> {
        return persist(urls, prefix).map { imageUrl ->
            CommandEntryEntity.image(imageUrl, source)
        }
    }

    /**
     * Download images from [urls] and return a list of URLs for the downloaded images on success,
     * or an empty list if any of the images failed to download.
     */
    private suspend fun persist(urls: List<String?>, prefix: String): List<String> = coroutineScope {
        val baseUrl: String = config.fileServerBaseUrl.orEmpty()

        val downloadJobs = ArrayList<Deferred<String?>>()
        urls.forEachIndexed { index, url ->
            if (url != null) downloadJobs += async {
                val path = "$prefix-$index.jpg"
                when {
                    httpClient.downloadFile(url, File(downloadPath, path)) -> "$baseUrl/$path"
                    else -> null
                }
            }
        }

        downloadJobs.awaitAll().map { result ->
            result ?: return@coroutineScope emptyList()
        }
    }
}