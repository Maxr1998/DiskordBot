package de.maxr1998.diskord.integration.resolver.sources

import kotlinx.serialization.Serializable

object ImgurApi {
    @Serializable
    data class Response<T>(
        val data: List<T>,
        val success: Boolean,
        val status: Int,
    )

    @Serializable
    data class Image(
        val id: String,
        val link: String,
    )
}