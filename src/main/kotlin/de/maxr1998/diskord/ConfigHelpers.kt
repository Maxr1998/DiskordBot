package de.maxr1998.diskord

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.File

@OptIn(ExperimentalSerializationApi::class)
object ConfigHelpers {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    suspend fun readConfig(file: File): Config = withContext(Dispatchers.IO) {
        val configString = file.readText()
        json.decodeFromString(Config.serializer(), configString)
    }

    suspend fun persistConfig(file: File, config: Config) {
        withContext(Dispatchers.IO) {
            val configString = json.encodeToString(Config.serializer(), config)
            file.writeText(configString)
        }
    }
}