package de.maxr1998.diskord.model.dto.imgur

import kotlinx.serialization.Serializable

@Serializable
data class ImgurImage(
    val id: String,
    val link: String,
)