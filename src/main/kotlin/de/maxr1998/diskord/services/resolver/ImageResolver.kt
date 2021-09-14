package de.maxr1998.diskord.services.resolver

import de.maxr1998.diskord.model.database.CommandEntryEntity

class ImageResolver(
    private val sources: List<ImageSource>,
) {
    /**
     * Tries to resolve image urls or images from online services
     */
    suspend fun resolve(content: String, maySave: Boolean): Result<List<CommandEntryEntity>> {
        val supportedResolver = sources.find { source -> source.supports(content) }
        return when {
            supportedResolver == null -> Status.Unsupported()
            supportedResolver is PersistingImageSource && !maySave -> Status.Forbidden()
            else -> supportedResolver.resolve(content)
        }
    }

    sealed class Status : Exception() {
        object Unsupported : Status()
        sealed class Failure : Status()
        object Forbidden : Failure()
        object RateLimited : Failure()
        object ParsingFailed : Failure()
        object Unknown : Failure()

        operator fun <T> invoke() = Result.failure<T>(this)
    }
}