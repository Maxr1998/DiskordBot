package de.maxr1998.diskord

import de.maxr1998.diskord.config.ConfigHelpers
import de.maxr1998.diskord.integration.resolver.ImageResolver
import de.maxr1998.diskord.integration.resolver.ImageSource
import de.maxr1998.diskord.integration.resolver.sources.ImgurAlbumSource
import de.maxr1998.diskord.integration.resolver.sources.InstagramImageSource
import de.maxr1998.diskord.integration.resolver.sources.NaverEntertainImageSource
import de.maxr1998.diskord.integration.resolver.sources.NaverPostImageSource
import de.maxr1998.diskord.integration.resolver.sources.TwitterImageSource
import de.maxr1998.diskord.integration.resolver.sources.WeiboImageSource
import de.maxr1998.diskord.util.DatabaseHelpers
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.features.BrowserUserAgent
import io.ktor.client.features.json.serializer.KotlinxSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File
import io.ktor.client.features.json.Json as KtorJson

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
        HttpClient(Java) {
            BrowserUserAgent()
            KtorJson {
                serializer = KotlinxSerializer(get())
            }
        }
    }

    single {
        ConfigHelpers(configFile = File(Constants.CONFIG_FILE_NAME), get())
    }

    single {
        DatabaseHelpers(databaseFile = File(Constants.DATABASE_FILE_NAME))
    }

    single { ImageResolver(getAll()) }
    single { ImgurAlbumSource(get(), get()) } bind ImageSource::class
    single { InstagramImageSource(get(), get()) } bind ImageSource::class
    single { NaverEntertainImageSource(get()) } bind ImageSource::class
    single { NaverPostImageSource(get()) } bind ImageSource::class
    single { TwitterImageSource(get(), get()) } bind ImageSource::class
    single { WeiboImageSource(get(), get()) } bind ImageSource::class
}