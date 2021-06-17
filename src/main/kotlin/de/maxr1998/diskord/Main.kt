package de.maxr1998.diskord

import java.io.File

suspend fun main() {
    val configFile = File(Constants.CONFIG_FILE_NAME)

    Bot(configFile).run()
}