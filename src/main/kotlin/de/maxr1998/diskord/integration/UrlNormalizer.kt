package de.maxr1998.diskord.integration

import de.maxr1998.diskord.integration.resolver.sources.TwitterApi

object UrlNormalizer {
    const val PINTEREST_IMAGE_BASE_URL = "https://i.pinimg.com"

    private val replacements = listOf(
        Regex("""(${TwitterApi.TWITTER_IMAGE_BASE_URL}[\w_-]+)(?:\?format=|\.)([a-z]+)(?:(?:[&?]name=|:)(?:[a-z]+|\d+x\d+))?""") to "$1?format=$2&name=orig",
        Regex("""($PINTEREST_IMAGE_BASE_URL)/\d+x/([a-f\d]{2}/[a-f\d]{2}/[a-f\d]{2}/[a-f\d]{32}\.[a-z]+)""") to "$1/originals/$2",
        Regex("""https://media.discordapp.net/(attachments/\d+/\d+/\S+\.\w+)""") to "https://cdn.discordapp.com/$1",
        Regex("""https:://wx\d\.sinaimg\.cn/[a-z\d]+/([a-zA-Z\d]+\.jpg)""") to "https://wx4.sinaimg.cn/original/$1",
    )

    fun normalizeUrls(content: String): String = replacements.fold(content) { acc, (search, replace) ->
        acc.replace(search, replace)
    }
}