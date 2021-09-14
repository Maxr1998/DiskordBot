package de.maxr1998.diskord.services.resolver

import de.maxr1998.diskord.model.database.CommandEntryEntity
import io.ktor.client.HttpClient

abstract class ImageSource(protected val httpClient: HttpClient) {

    abstract fun supports(content: String): Boolean

    abstract suspend fun resolve(content: String): Result<List<CommandEntryEntity>>
}