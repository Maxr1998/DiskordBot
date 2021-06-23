package de.maxr1998.diskord

import de.maxr1998.diskord.config.ConfigHelpers
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.serializer.KotlinxSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import java.io.File
import io.ktor.client.features.json.Json as KtorJsonFeature


val appModule = module {
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
        HttpClient(CIO) {
            KtorJsonFeature {
                serializer = KotlinxSerializer(get())
            }
        }
    }

    single {
        ConfigHelpers(configFile = File(Constants.CONFIG_FILE_NAME), get())
    }
}