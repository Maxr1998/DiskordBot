package de.maxr1998.diskord.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.reflect.KProperty

@Suppress("MemberVisibilityCanBePrivate")
class ConfigHelpers(
    private val configFile: File,
    private val json: Json,
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var config: Config
    private var persistJob: Job? = null

    suspend fun awaitConfig() {
        config = readConfig()
    }

    suspend fun readConfig(): Config = withContext(Dispatchers.IO) {
        val configString = configFile.readText()
        json.decodeFromString(Config.serializer(), configString)
    }

    suspend fun persistConfig(config: Config) {
        withContext(Dispatchers.IO) {
            val configString = json.encodeToString(Config.serializer(), config)
            configFile.writeText(configString)
        }
    }

    fun postPersistConfig() {
        // Cancel current
        persistJob?.cancel()

        // Start new persistence job
        persistJob = coroutineScope.launch {
            // Delay writing config
            delay(CONFIG_PERSISTENCE_DELAY_MS)

            persistConfig(config)
        }
    }

    operator fun getValue(any: Any, property: KProperty<*>): Config = config

    companion object {
        private const val CONFIG_PERSISTENCE_DELAY_MS = 30_000L
    }
}