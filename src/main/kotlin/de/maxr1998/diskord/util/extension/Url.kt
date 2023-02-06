package de.maxr1998.diskord.util.extension

import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.encodedPath

fun Url.cleanedCopy(
    host: String = this.host,
    path: String = encodedPath,
    parameters: Parameters = Parameters.Empty,
): Url = URLBuilder(
    protocol = URLProtocol.HTTPS,
    host = host,
    port = port,
    user = user,
    password = password,
    parameters = parameters,
    fragment = "",
    trailingQuery = false,
).apply {
    encodedPath = path
}.build()

fun Url.removeParameters(): Url = URLBuilder(this).apply {
    parameters.clear()
    fragment = ""
    trailingQuery = false
}.build()