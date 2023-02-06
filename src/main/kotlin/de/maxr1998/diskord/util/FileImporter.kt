package de.maxr1998.diskord.util

import de.maxr1998.diskord.Constants
import de.maxr1998.diskord.command.dynamic.CommandEntryEntity
import de.maxr1998.diskord.command.dynamic.DynamicCommandRepository
import de.maxr1998.diskord.config.Config
import de.maxr1998.diskord.config.ConfigHelpers
import de.maxr1998.diskord.integration.resolver.sources.InstagramImageSource
import de.maxr1998.diskord.util.extension.subdirectories
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.util.extension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

private val logger = KotlinLogging.logger { }
private val importMutex = Mutex()

class FileImporter(
    configHelpers: ConfigHelpers,
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val config: Config by configHelpers

    private val filesPath = Path.of(Constants.FILES_PATH)
    private val importPath = filesPath.resolve("import")

    fun startImport() {
        coroutineScope.launch {
            importMutex.withLock {
                importAll()
            }
        }
    }

    private suspend fun importAll() {
        val vendorDirectories = importPath.toFile().subdirectories()
        for (vendorDirectory in vendorDirectories) {
            val vendor = vendorDirectory.name

            val commandDirectories = vendorDirectory.subdirectories()
            for (commandDirectory in commandDirectories) {
                val commandPath = commandDirectory.toPath()
                val command = commandDirectory.name

                val commandEntity = DynamicCommandRepository.getCommandByGuild("", command.lowercase())
                if (commandEntity == null) {
                    logger.warn("Found invalid command directory '$command'")
                    continue // skip invalid commands
                }

                val entries = withContext(Dispatchers.IO) {
                    val filePaths = Files.walk(commandPath).use { stream ->
                        stream.filter { path ->
                            path.isRegularFile() && (path.extension == "jpg" || path.extension == "jpeg")
                        }.toList()
                    }

                    filePaths.mapNotNull { filePath ->
                        processPath(vendor, commandPath, filePath)
                    }
                }

                for ((sourcePath, destinationPath, commandEntry) in entries) {
                    DynamicCommandRepository.addCommandEntry(commandEntity, commandEntry)
                    sourcePath.toFile().renameTo(destinationPath.toFile())

                    logger.info("Importer added '${commandEntry.content}' to '$command'")
                }
            }
        }
    }

    private fun processPath(
        vendor: String,
        commandPath: Path,
        sourcePath: Path,
    ): Triple<Path, Path, CommandEntryEntity>? = when (vendor) {
        "instagram" -> {
            val filename = sourcePath.name.replace(".jpeg", ".jpg")
            val destinationPath = filesPath.resolve(commandPath.relativize(sourcePath)).parent.resolve(filename)

            // Build command entry
            val url = "${config.fileServerBaseUrl.orEmpty()}/$filename"
            val postId = filename.substringBeforeLast('-')
            val source = URLBuilder(
                protocol = URLProtocol.HTTPS,
                host = InstagramImageSource.INSTAGRAM_HOST,
                pathSegments = listOf("p", postId),
            ).build()

            val commandEntry = CommandEntryEntity.image(url, source)

            Triple(sourcePath, destinationPath, commandEntry)
        }
        else -> null
    }
}