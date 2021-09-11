package de.maxr1998.diskord

import com.jessecorbett.diskord.api.channel.ChannelClient
import com.jessecorbett.diskord.api.channel.EmbedField
import com.jessecorbett.diskord.api.common.Message
import com.jessecorbett.diskord.api.common.User
import com.jessecorbett.diskord.bot.BotContext
import com.jessecorbett.diskord.bot.bot
import com.jessecorbett.diskord.bot.classicCommands
import com.jessecorbett.diskord.util.sendEmbed
import com.jessecorbett.diskord.util.sendMessage
import com.jessecorbett.diskord.util.words
import de.maxr1998.diskord.Command.ADD
import de.maxr1998.diskord.Command.AUTO_RESPONDER
import de.maxr1998.diskord.Command.AUTO_RESPONDER_MODE_ADD
import de.maxr1998.diskord.Command.AUTO_RESPONDER_MODE_LIST
import de.maxr1998.diskord.Command.AUTO_RESPONDER_MODE_REMOVE
import de.maxr1998.diskord.Command.AUTO_RESPONDER_SHORT
import de.maxr1998.diskord.Command.REMOVE
import de.maxr1998.diskord.Command.REMOVE_SHORT
import de.maxr1998.diskord.Command.RESOLVE
import de.maxr1998.diskord.Constants.COMMAND_PREFIX
import de.maxr1998.diskord.config.Config
import de.maxr1998.diskord.config.ConfigHelpers
import de.maxr1998.diskord.model.repository.DynamicCommandRepository
import de.maxr1998.diskord.utils.DatabaseHelpers
import de.maxr1998.diskord.utils.ImageResolver
import de.maxr1998.diskord.utils.UrlNormalizer
import de.maxr1998.diskord.utils.diskord.args
import de.maxr1998.diskord.utils.diskord.checkAdmin
import de.maxr1998.diskord.utils.diskord.checkManager
import de.maxr1998.diskord.utils.diskord.checkOwner
import de.maxr1998.diskord.utils.diskord.extractEntries
import de.maxr1998.diskord.utils.getAckEmoji
import de.maxr1998.diskord.utils.logAdd
import de.maxr1998.diskord.utils.logRemove
import kotlinx.coroutines.delay
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

@Suppress("DuplicatedCode")
class Bot(
    private val configHelpers: ConfigHelpers,
    private val databaseHelpers: DatabaseHelpers,
    private val imageResolver: ImageResolver,
) {
    private val config: Config by configHelpers

    suspend fun run() {
        logger.debug("Starting Diskord bot…")

        configHelpers.awaitConfig()
        logger.debug("Successfully loaded config.")

        databaseHelpers.setup()
        logger.debug("Successfully connected to database.")

        databaseHelpers.createSchemas()

        bot(config.botToken) {
            classicCommands(commandPrefix = COMMAND_PREFIX) {
                // User management commands
                command(Command.PROMOTE_ADMIN) { message -> promoteAdmin(message) }
                command(Command.PROMOTE_ADMIN_SHORT) { message -> promoteAdmin(message) }
                command(Command.PROMOTE) { message -> promoteManager(message) }
                command(Command.PROMOTE_SHORT) { message -> promoteManager(message) }

                // AR management commands
                command(AUTO_RESPONDER) { message -> autoResponder(message) }
                command(AUTO_RESPONDER_SHORT) { message -> autoResponder(message) }
                command(ADD) { message -> addEntry(message) }
                command(REMOVE) { message -> removeEntry(message) }
                command(REMOVE_SHORT) { message -> removeEntry(message) }

                // Helper commands
                command(RESOLVE) { message -> resolve(message) }

                // Help
                command(Command.HELP) { message -> message.channel.showHelp() }
            }

            // Dynamic commands
            DynamicCommandsModule().install(this)
        }

        // Keep application alive
        while (true) delay(100)
    }

    private suspend fun BotContext.promoteAdmin(message: Message) {
        // Admins can only be promoted by the owner
        if (!checkOwner(config, message)) return

        // Add mentioned users as managers
        val users = message.usersMentioned.map(User::id)
        if (config.adminIds.addAll(users)) {
            configHelpers.postPersistConfig()
            message.react(config.getAckEmoji())

            logger.debug("${users.joinToString(prefix = "[", postfix = "]")} promoted to admin")
        }
    }

    private suspend fun BotContext.promoteManager(message: Message) {
        // Managers can only be promoted by the owner and admins
        if (!checkAdmin(config, message)) return

        // Add mentioned users as managers
        val users = message.usersMentioned.map(User::id)
        if (config.managerIds.addAll(users)) {
            configHelpers.postPersistConfig()
            message.react(config.getAckEmoji())

            logger.debug("${users.joinToString(prefix = "[", postfix = "]")} promoted to manager")
        }
    }

    private suspend fun BotContext.autoResponder(message: Message) {
        // Only owner and admins can add new auto-responders
        if (!checkAdmin(config, message)) return

        val args = message.words.drop(1)
        if (args.size !in 1..2) {
            message.channel.showHelp()
            return
        }

        val mode = args[0]
        val command = args.getOrNull(1)

        val guild = message.guildId ?: run {
            message.channel.sendNoDmWarning("$AUTO_RESPONDER $mode")
            return
        }

        when (mode) {
            in AUTO_RESPONDER_MODE_ADD -> {
                if (command == null) {
                    message.channel.showHelp()
                    return
                }

                if (DynamicCommandRepository.addCommandByGuild(guild, command)) {
                    message.respond("Successfully added auto-responder for '$command'")
                    logger.debug("${message.author.username} added auto-responder $command")
                } else {
                    message.respond("Auto-responder for '$command' already exists")
                }
            }
            in AUTO_RESPONDER_MODE_LIST -> {
                if (args.size != 1) {
                    message.channel.showHelp()
                    return
                }

                val commands = DynamicCommandRepository.getCommandsByGuild(guild)

                message.respond {
                    title = "Available auto-responders"
                    description = commands.joinToString("\n") { (cmd, count) ->
                        "\u2022 ` $cmd ` - $count entries"
                    }
                }
            }
            in AUTO_RESPONDER_MODE_REMOVE -> {
                if (command == null) {
                    message.channel.showHelp()
                    return
                }

                if (DynamicCommandRepository.removeCommandByGuild(guild, command)) {
                    message.respond("Successfully removed auto-responder for '$command'")
                    logger.debug("${message.author.username} removed auto-responder $command")
                } else {
                    message.respond("Unknown auto-responder '$command'")
                }
            }
            else -> {
                message.channel.showHelp()
                return
            }
        }
    }

    private suspend fun BotContext.addEntry(message: Message) {
        // Only owner, admins and managers can add new entries
        if (!checkManager(config, message)) return

        val guild = message.guildId ?: run {
            message.channel.sendNoDmWarning(ADD)
            return
        }

        val args = message.args(limit = 3)

        val command = args.getOrNull(0)?.trim() ?: run {
            message.channel.showHelp()
            return
        }

        val commandEntity = DynamicCommandRepository.getCommandByGuild(guild, command) ?: run {
            message.respond("Unknown auto-responder '$command'")
            return
        }

        val entries = extractEntries(args, message) ?: run {
            message.channel.showHelp()
            return
        }

        // Try to resolve images from a single link
        val singleEntry = entries.singleOrNull()
        if (singleEntry != null && !singleEntry.contains(Regex("""\s"""))) {
            imageResolver.resolve(singleEntry).onSuccess { images ->
                // Resolved images, add to database
                if (DynamicCommandRepository.addCommandEntries(commandEntity, images)) {
                    val imagesString = images.joinToString(prefix = "\n", separator = "\n")
                    message.respond("Resolved ${images.size} image(s) and added them to `$command`\n$imagesString")
                    logger.logAdd(message.author, command, images)
                } else {
                    message.respond("This content already exists, try a different one!")
                }
                return
            }.onFailure { exception ->
                require(exception is ImageResolver.Status)
                when (exception) {
                    is ImageResolver.Status.Failure -> {
                        val errorText = when (exception) {
                            ImageResolver.Status.RateLimited -> "Rate-limit exceeded, please try again later."
                            ImageResolver.Status.ParsingFailed -> "Parsing failed, please contact the developer."
                            ImageResolver.Status.Unknown -> "Couldn't process content, please ensure your query is correct."
                        }
                        message.respond(errorText)
                        return
                    }
                    ImageResolver.Status.Unsupported -> Unit // Continue normally
                }
            }
        }

        // Normalize URLs
        val normalizedEntries = entries.map(UrlNormalizer::normalizeUrls)

        // Add content to commands map
        if (DynamicCommandRepository.addCommandEntries(commandEntity, normalizedEntries)) {
            message.react(config.getAckEmoji())
            logger.logAdd(message.author, command, normalizedEntries)
        } else {
            message.respond("This content already exists, try a different one!")
        }
    }

    private suspend fun BotContext.removeEntry(message: Message) {
        // Only owner, admins and managers can remove entries
        if (!checkManager(config, message)) return

        val guild = message.guildId ?: run {
            message.channel.sendNoDmWarning(REMOVE)
            return
        }

        val args = message.args(limit = 3)

        val command = args.getOrNull(0)?.trim() ?: run {
            message.channel.showHelp()
            return
        }

        val commandEntity = DynamicCommandRepository.getCommandByGuild(guild, command) ?: run {
            message.respond("Unknown auto-responder '$command'")
            return
        }

        val entries = extractEntries(args, message) ?: run {
            message.channel.showHelp()
            return
        }

        // Normalize URLs
        val normalizedEntries = entries.map(UrlNormalizer::normalizeUrls)

        // Remove content from commands map
        if (DynamicCommandRepository.removeCommandEntries(commandEntity, normalizedEntries)) {
            message.react(config.getAckEmoji())
            logger.logRemove(message.author, command, normalizedEntries)
        } else {
            message.respond("Content not found, nothing was removed.")
        }
    }

    private suspend fun BotContext.resolve(message: Message) {
        // Only managers may use the bot to resolve links
        if (!checkManager(config, message)) return

        val content = message.content
            .removePrefix("$COMMAND_PREFIX$RESOLVE ")
            .replace("""\S+""", " ")

        imageResolver.resolve(content).onSuccess { images ->
            message.respond(images.joinToString(prefix = Constants.LINE_SEPARATED_CONTENT_TAG, separator = "\n"))
        }.onFailure { exception ->
            require(exception is ImageResolver.Status)
            val errorText = when (exception) {
                ImageResolver.Status.Unsupported -> "Unsupported content. Try a different link."
                ImageResolver.Status.RateLimited -> "Rate-limit exceeded, please try again later."
                ImageResolver.Status.ParsingFailed -> "Parsing failed, please contact the developer."
                ImageResolver.Status.Unknown -> "Couldn't process content, please ensure your query is correct."
            }
            message.respond(errorText)
        }
    }

    private suspend fun ChannelClient.showHelp() = sendEmbed {
        title = "Usage"
        description = "All commands that the bot provides."
        color = 0xA2E4B8

        fields = mutableListOf(
            EmbedField(
                name = "Add auto-responder - *admin only*",
                value = """`$COMMAND_PREFIX$AUTO_RESPONDER ${AUTO_RESPONDER_MODE_ADD[0]} <command>`""",
                inline = false,
            ),
            EmbedField(
                name = "Show auto-responders - *admin only*",
                value = """`$COMMAND_PREFIX$AUTO_RESPONDER ${AUTO_RESPONDER_MODE_LIST[0]} <command>`""",
                inline = false,
            ),
            EmbedField(
                name = "Remove auto-responder - *admin only*",
                value = """`$COMMAND_PREFIX$AUTO_RESPONDER ${AUTO_RESPONDER_MODE_REMOVE[0]} <command>`""",
                inline = false,
            ),
            EmbedField(
                name = "Add entry to existing responder",
                value = """`$COMMAND_PREFIX$ADD <command> <content>`
                          |*or*
                          |Reply to a message with:
                          |`$COMMAND_PREFIX$ADD <command>`
                          |
                          |If `<content>` is a link to Twitter, Instagram or an Imgur album, contained images will automatically be resolved!""".trimMargin(),
                inline = false,
            ),
            EmbedField(
                name = "Resolve images from supported URLs",
                value = """`$COMMAND_PREFIX$RESOLVE <link>`""",
                inline = false,
            )
        )
    }

    private suspend fun ChannelClient.sendNoDmWarning(command: String) =
        sendMessage("Command `$command` cannot be used in DMs.")
}