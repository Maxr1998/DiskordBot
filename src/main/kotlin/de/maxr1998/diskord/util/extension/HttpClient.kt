package de.maxr1998.diskord.util.extension

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File

suspend fun HttpClient.getHtml(url: Url): HttpStatement = prepareGet(url) {
    accept(ContentType.Text.Html)
}

suspend fun HttpClient.loadJsoupDocument(url: Url): Document? = getHtml(url).execute { response ->
    if (response.status.isSuccess() && response.contentType()?.withoutParameters() == ContentType.Text.Html) {
        response.bodyAsChannel().toInputStream().use { stream ->
            withContext(Dispatchers.IO) {
                @Suppress("BlockingMethodInNonBlockingContext")
                Jsoup.parse(stream, null, url.toString())
            }
        }
    } else {
        null
    }
}

suspend fun HttpClient.downloadFile(url: String, out: File): Boolean {
    if (out.isDirectory) return false

    val response = try {
        get(url)
    } catch (e: Exception) {
        return false
    }

    if (!response.status.isSuccess()) {
        return false
    }

    val tmp = File(out.parent, "." + out.name)

    try {
        response.bodyAsChannel().copyAndClose(tmp.writeChannel())
    } catch (e: Exception) {
        return false
    }

    // Delete if target exists
    if (out.exists()) {
        out.delete()
    }

    return tmp.renameTo(out)
}