package de.maxr1998.diskord.util.extension

import com.jessecorbett.diskord.api.common.Attachment
import io.ktor.http.ContentType

val Attachment.parsedContentType: ContentType?
    get() = contentType?.let(ContentType.Companion::parse)