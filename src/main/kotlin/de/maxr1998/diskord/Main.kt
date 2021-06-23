package de.maxr1998.diskord

import org.koin.core.context.startKoin
import java.io.File

suspend fun main() {
    startKoin {
        modules(appModule)
    }

    Bot(configFile = File(Constants.CONFIG_FILE_NAME)).run()
}