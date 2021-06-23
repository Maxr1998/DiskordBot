package de.maxr1998.diskord.utils

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import java.io.File

suspend fun HttpClient.downloadFile(url: String, out: File, overwrite: Boolean = false): Boolean {
    if (out.exists() && !overwrite) {
        // Assume same name = same content
        return true
    }

    val response = try {
        get<HttpResponse>(url)
    } catch (e: Exception) {
        return false
    }

    if (!response.status.isSuccess()) {
        return false
    }

    response.content.copyAndClose(out.writeChannel())

    return true
}