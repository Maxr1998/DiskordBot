package de.maxr1998.diskord.model.imgur

import kotlinx.serialization.Serializable

@Serializable
data class ImgurImage(
    val id: String,
    val link: String,
)