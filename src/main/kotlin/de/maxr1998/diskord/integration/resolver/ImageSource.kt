package de.maxr1998.diskord.integration.resolver

import io.ktor.client.HttpClient
import io.ktor.http.Url

abstract class ImageSource(protected val httpClient: HttpClient) {

    abstract fun supports(url: Url): Boolean

    abstract suspend fun resolve(url: Url): Result<ImageResolver.Resolved>
}