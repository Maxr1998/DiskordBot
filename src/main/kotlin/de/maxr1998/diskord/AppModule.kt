package de.maxr1998.diskord

import de.maxr1998.diskord.config.ConfigHelpers
import de.maxr1998.diskord.utils.ImageResolver
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.features.BrowserUserAgent
import io.ktor.client.features.logging.DEFAULT
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import java.io.File

val appModule = module {
    single { Bot(get(), get()) }

    single {
        @OptIn(ExperimentalSerializationApi::class)
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            allowSpecialFloatingPointValues = true
            prettyPrint = true
            prettyPrintIndent = "  "
        }
    }

    single {
        HttpClient(Java) {
            BrowserUserAgent()
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.HEADERS
            }
        }
    }

    single {
        ConfigHelpers(configFile = File(Constants.CONFIG_FILE_NAME), get())
    }

    single { ImageResolver(get(), get(), get()) }
}