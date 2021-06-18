package de.maxr1998.diskord

object UrlNormalizer {
    const val TWITTER_LINK_BASE = "https://pbs.twimg.com/media"
    const val PINTEREST_LINK_BASE = "https://i.pinimg.com"

    private val replacements = listOf(
        Regex("""($TWITTER_LINK_BASE/\w+)(?:\?format=|\.)([a-z]+)(?:[&?]name=|:)[a-z]+""") to "$1?format=$2&name=orig",
        Regex("""($PINTEREST_LINK_BASE)/\d+x/([a-f\d]{2}/[a-f\d]{2}/[a-f\d]{2}/[a-f\d]{32}\.[a-z]+)""") to "$1/originals/$2",
    )

    fun normalizeUrls(content: String): String = replacements.fold(content) { acc, (search, replace) ->
        acc.replace(search, replace)
    }
}