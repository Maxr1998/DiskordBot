package de.maxr1998.diskord.utils

import de.maxr1998.diskord.Constants.PINTEREST_IMAGE_BASE_URL
import de.maxr1998.diskord.Constants.TWITTER_IMAGE_BASE_URL

object UrlNormalizer {
    private val replacements = listOf(
        Regex("""($TWITTER_IMAGE_BASE_URL[\w_-]+)(?:\?format=|\.)([a-z]+)(?:[&?]name=|:)(?:[a-z]+|\d+x\d+)""") to "$1?format=$2&name=orig",
        Regex("""($PINTEREST_IMAGE_BASE_URL)/\d+x/([a-f\d]{2}/[a-f\d]{2}/[a-f\d]{2}/[a-f\d]{32}\.[a-z]+)""") to "$1/originals/$2",
    )

    fun normalizeUrls(content: String): String = replacements.fold(content) { acc, (search, replace) ->
        acc.replace(search, replace)
    }
}