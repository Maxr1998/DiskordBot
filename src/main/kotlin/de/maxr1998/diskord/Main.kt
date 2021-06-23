package de.maxr1998.diskord

import org.koin.core.context.startKoin

suspend fun main() {
    startKoin {
        modules(appModule)
    }

    Bot().run()
}