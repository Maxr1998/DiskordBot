package de.maxr1998.diskord.services

import de.maxr1998.diskord.Constants.PINTEREST_IMAGE_BASE_URL
import de.maxr1998.diskord.Constants.TWITTER_IMAGE_BASE_URL
import io.ktor.http.Parameters
import io.ktor.http.URLProtocol
import io.ktor.http.Url

object UrlNormalizer {
    private val replacements = listOf(
        Regex("""($TWITTER_IMAGE_BASE_URL[\w_-]+)(?:\?format=|\.)([a-z]+)(?:(?:[&?]name=|:)(?:[a-z]+|\d+x\d+))?""") to "$1?format=$2&name=orig",
        Regex("""($PINTEREST_IMAGE_BASE_URL)/\d+x/([a-f\d]{2}/[a-f\d]{2}/[a-f\d]{2}/[a-f\d]{32}\.[a-z]+)""") to "$1/originals/$2",
        Regex("""https://media.discordapp.net/(attachments/\d+/\d+/\S+\.\w+)""") to "https://cdn.discordapp.com/$1",
        Regex("""https:://wx\d\.sinaimg\.cn/[a-z\d]+/([a-zA-Z\d]+\.jpg)""") to "https://wx4.sinaimg.cn/original/$1",
    )

    fun normalizeUrls(content: String): String = replacements.fold(content) { acc, (search, replace) ->
        acc.replace(search, replace)
    }

    fun Url.cleanedCopy(
        host: String = this.host,
        encodedPath: String = this.encodedPath,
        parameters: Parameters = Parameters.Empty
    ) = copy(
        protocol = URLProtocol.HTTPS,
        host = host,
        encodedPath = encodedPath,
        parameters = parameters,
        fragment = "",
        trailingQuery = false,
    )
}