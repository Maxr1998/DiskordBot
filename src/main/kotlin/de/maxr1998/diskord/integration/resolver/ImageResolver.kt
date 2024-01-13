package de.maxr1998.diskord.integration.resolver

import de.maxr1998.diskord.command.dynamic.CommandEntryEntity
import io.ktor.http.Url

class ImageResolver(
    private val sources: List<ImageSource>,
) {
    /**
     * Tries to resolve image urls or images from online services
     */
    suspend fun resolve(url: Url, maySave: Boolean): Result<Resolved> {
        val supportedResolver = sources.find { source -> source.supports(url) }
        return when {
            supportedResolver == null -> Status.Unsupported()
            supportedResolver is PersistingImageSource && !maySave -> Status.Forbidden()
            else -> supportedResolver.resolve(url)
        }
    }

    data class Resolved(
        val url: Url,
        val imageUrls: List<CommandEntryEntity>,
    )

    sealed class Status : Exception() {
        data object Unsupported : Status()
        sealed class Failure : Status()
        data object Forbidden : Failure()
        data object RateLimited : Failure()
        data object ParsingFailed : Failure()
        data object AgeRestricted : Failure()
        data object Unknown : Failure()

        operator fun <T> invoke() = Result.failure<T>(this)
    }
}