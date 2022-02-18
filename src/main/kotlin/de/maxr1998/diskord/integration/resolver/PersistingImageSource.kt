package de.maxr1998.diskord.integration.resolver

import de.maxr1998.diskord.config.Config
import de.maxr1998.diskord.config.ConfigHelpers
import de.maxr1998.diskord.model.database.CommandEntryEntity
import de.maxr1998.diskord.util.http.downloadFile
import io.ktor.client.HttpClient
import io.ktor.http.Url
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

    suspend fun persist(urls: List<String>, source: Url, prefix: String): List<CommandEntryEntity> = coroutineScope {
        val baseUrl: String = config.fileServerBaseUrl.orEmpty()
        urls.mapIndexed { index, url ->
            async {
                val path = "$prefix-$index.jpg"
                if (httpClient.downloadFile(url, File(downloadPath, path))) {
                    CommandEntryEntity.image("$baseUrl/$path", source)
                } else null
            }
        }
    }.awaitAll().filterNotNull()
}