package de.maxr1998.diskord.model.dto.imgur

import kotlinx.serialization.Serializable

@Serializable
data class ImgurResponse<T>(
    val data: List<T>,
    val success: Boolean,
    val status: Int,
)