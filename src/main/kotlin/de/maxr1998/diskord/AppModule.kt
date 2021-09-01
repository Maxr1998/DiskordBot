package de.maxr1998.diskord

import de.maxr1998.diskord.config.ConfigHelpers
import de.maxr1998.diskord.utils.ImageResolver
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.features.BrowserUserAgent
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import mu.KLoggable
import org.koin.dsl.module
import java.io.File
import io.ktor.client.features.logging.Logger as KtorLogger

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

    single<KtorLogger> {
        object : KtorLogger, KLoggable {
            override val logger = logger("de.maxr1998.diskord.HttpClient")

            override fun log(message: String) {
                logger.info(message)
            }
        }
    }

    single {
        HttpClient(Java) {
            BrowserUserAgent()
            install(Logging) {
                logger = get()
                level = LogLevel.HEADERS
            }
        }
    }

    single {
        ConfigHelpers(configFile = File(Constants.CONFIG_FILE_NAME), get())
    }

    single { ImageResolver(get(), get(), get()) }
}