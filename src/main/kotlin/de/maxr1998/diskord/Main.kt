package de.maxr1998.diskord

import org.koin.core.context.startKoin

suspend fun main() {
    val koinApp = startKoin {
        modules(appModule)
    }

    with(koinApp.koin) {
        get<Bot>().run()
    }
}