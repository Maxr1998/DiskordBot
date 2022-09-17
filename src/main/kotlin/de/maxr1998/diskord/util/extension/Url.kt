package de.maxr1998.diskord.util.extension

import io.ktor.http.Parameters
import io.ktor.http.URLProtocol
import io.ktor.http.Url

fun Url.cleanedCopy(
    host: String = this.host,
    encodedPath: String = this.encodedPath,
    parameters: Parameters = Parameters.Empty,
) = copy(
    protocol = URLProtocol.HTTPS,
    host = host,
    encodedPath = encodedPath,
    parameters = parameters,
    fragment = "",
    trailingQuery = false,
)