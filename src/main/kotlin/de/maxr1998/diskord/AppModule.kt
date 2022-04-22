package de.maxr1998.diskord

import de.maxr1998.diskord.config.Config
import de.maxr1998.diskord.config.ConfigHelpers
import de.maxr1998.diskord.integration.resolver.ImageResolver
import de.maxr1998.diskord.integration.resolver.ImageSource
import de.maxr1998.diskord.integration.resolver.sources.ImgurAlbumSource
import de.maxr1998.diskord.integration.resolver.sources.InstagramImageSource
import de.maxr1998.diskord.integration.resolver.sources.NaverEntertainImageSource
import de.maxr1998.diskord.integration.resolver.sources.NaverPostImageSource
import de.maxr1998.diskord.integration.resolver.sources.TwitterImageSource
import de.maxr1998.diskord.integration.resolver.sources.WeiboImageSource
import de.maxr1998.diskord.permission.PermissionManager
import de.maxr1998.diskord.util.DatabaseHelpers
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.features.BrowserUserAgent
import io.ktor.client.features.cookies.HttpCookies
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logging
import io.ktor.http.Cookie
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import mu.KLoggable
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File
import io.ktor.client.features.json.Json as KtorJson
import io.ktor.client.features.logging.Logger as KtorLogger

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
            install(HttpCookies) {
                default {
                    val config: Config by get<ConfigHelpers>()
                    if (config.instagramSession.isNotEmpty()) {
                        val url = URLBuilder(protocol = URLProtocol.HTTPS, host = InstagramImageSource.INSTAGRAM_HOST).build()
                        storage.addCookie(url, Cookie("sessionid", config.instagramSession))
                    }
                }
            }
            install(Logging) {
                logger = get()
                level = LogLevel.HEADERS
            }
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

    single { PermissionManager() }

    single { ImageResolver(getAll()) }
    single { ImgurAlbumSource(get(), get()) } bind ImageSource::class
    single { InstagramImageSource(get(), get(), get()) } bind ImageSource::class
    single { NaverEntertainImageSource(get()) } bind ImageSource::class
    single { NaverPostImageSource(get()) } bind ImageSource::class
    single { TwitterImageSource(get(), get()) } bind ImageSource::class
    single { WeiboImageSource(get(), get()) } bind ImageSource::class
}