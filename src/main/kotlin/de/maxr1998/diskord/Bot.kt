package de.maxr1998.diskord

import com.jessecorbett.diskord.api.channel.ChannelClient
import com.jessecorbett.diskord.api.channel.EmbedField
import com.jessecorbett.diskord.api.common.Message
import com.jessecorbett.diskord.api.common.User
import com.jessecorbett.diskord.bot.BotContext
import com.jessecorbett.diskord.bot.bot
import com.jessecorbett.diskord.bot.classicCommands
import com.jessecorbett.diskord.util.sendEmbed
import com.jessecorbett.diskord.util.words
import de.maxr1998.diskord.Command.ADD
import de.maxr1998.diskord.Command.AUTO_RESPONDER
import de.maxr1998.diskord.Command.AUTO_RESPONDER_MODE_ADD
import de.maxr1998.diskord.Command.AUTO_RESPONDER_MODE_LIST
import de.maxr1998.diskord.Command.AUTO_RESPONDER_MODE_REMOVE
import de.maxr1998.diskord.Command.AUTO_RESPONDER_SHORT
import de.maxr1998.diskord.Command.REMOVE
import de.maxr1998.diskord.Command.REMOVE_SHORT
import de.maxr1998.diskord.Constants.COMMAND_PREFIX
import de.maxr1998.diskord.config.Config
import de.maxr1998.diskord.config.ConfigHelpers
import de.maxr1998.diskord.utils.UrlNormalizer
import de.maxr1998.diskord.utils.attachmentUrlsOrNull
import de.maxr1998.diskord.utils.getAckEmoji
import de.maxr1998.diskord.utils.wrapListIfNotEmpty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

val logger = KotlinLogging.logger {}

class Bot(private val configFile: File) : KoinComponent {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val configHelpers: ConfigHelpers by inject()

    private lateinit var config: Config
    private var persistJob: Job? = null

    suspend fun run() {
        logger.debug("Starting Diskord bot…")

        config = configHelpers.readConfig(configFile)

        logger.debug("Successfully loaded config.")

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

                // Help
                command(Command.HELP) { message -> message.channel.showHelp() }
            }

            // Dynamic commands
            DynamicCommandsModule(config).install(this)
        }

        // Keep application alive
        while (true) delay(100)
    }

    private suspend fun BotContext.promoteAdmin(message: Message) {
        // Admins can only be promoted by the owner
        if (!checkOwner(message)) return

        // Add mentioned users as managers
        val users = message.usersMentioned.map(User::id)
        if (config.adminIds.addAll(users)) {
            message.react(config.getAckEmoji())

            logger.debug("${users.joinToString(prefix = "[", postfix = "]")} promoted to admin")
        }

        postPersistConfig()
    }

    private suspend fun BotContext.promoteManager(message: Message) {
        // Managers can only be promoted by the owner and admins
        if (!checkAdmin(message)) return

        // Add mentioned users as managers
        val users = message.usersMentioned.map(User::id)
        if (config.managerIds.addAll(users)) {
            message.react(config.getAckEmoji())

            logger.debug("${users.joinToString(prefix = "[", postfix = "]")} promoted to manager")
        }

        postPersistConfig()
    }

    private suspend fun BotContext.autoResponder(message: Message) {
        // Only owner and admins can add new auto-responders
        if (!checkAdmin(message)) return

        val args = message.words.drop(1)
        if (args.size !in 1..2) {
            message.channel.showHelp()
            return
        }

        val mode = args[0]
        val command = args.getOrNull(1)

        when (mode) {
            in AUTO_RESPONDER_MODE_ADD -> {
                if (command == null) {
                    message.channel.showHelp()
                    return
                }

                if (config.commands.containsKey(command)) {
                    message.respond("Auto-responder for '$command' already exists")
                    return
                }

                config.commands[command] = HashSet()

                message.respond("Successfully added auto-responder for '$command'")
                logger.debug("${message.author.username} added auto-responder $command")
            }
            in AUTO_RESPONDER_MODE_LIST -> {
                if (args.size != 1) {
                    message.channel.showHelp()
                    return
                }

                val commands = config.commands.keys.sorted()

                message.respondEmbed {
                    title = "Available auto-responders"
                    description = commands.joinToString("\n") { cmd ->
                        "\u2022 ` $cmd ` - ${config.commands[cmd]?.size ?: 0} entries"
                    }
                }
            }
            in AUTO_RESPONDER_MODE_REMOVE -> {
                if (command == null) {
                    message.channel.showHelp()
                    return
                }

                if (config.commands.remove(command) == null) {
                    return
                }

                message.respond("Successfully removed auto-responder for '$command'")
                logger.debug("${message.author.username} removed auto-responder $command")
            }
            else -> {
                message.channel.showHelp()
                return
            }
        }

        postPersistConfig()
    }

    private suspend fun BotContext.addEntry(message: Message) {
        // Only owner, admins and managers can add new entries
        if (!checkManager(message)) return

        val args = message.content
            .replace("""\S+""", " ")
            .split(" ", limit = 3)
            .drop(1)
        val command = args.getOrNull(0)?.trim()

        val entries = when (args.size) {
            1 -> {
                // Handle message attachments or replied message attachments or content
                val repliedMessage = message.reference?.messageId?.let { id -> message.channel.getMessage(id) }
                message.attachmentUrlsOrNull
                    ?: repliedMessage?.attachmentUrlsOrNull
                    ?: repliedMessage?.let { msg -> wrapListIfNotEmpty(msg.content) }
                    ?: emptyList()
            }
            2 -> wrapListIfNotEmpty(args[1])
            else -> emptyList()
        }

        if (command == null || entries.isEmpty()) {
            message.channel.showHelp()
            return
        }

        val commandEntries = config.commands[command]

        if (commandEntries == null) {
            message.respond("Unknown auto-responder '$command'")
            return
        }

        // Normalize URLs
        val normalizedEntries = entries.map(UrlNormalizer::normalizeUrls)

        // Add content to commands map
        if (commandEntries.addAll(normalizedEntries)) {
            message.react(config.getAckEmoji())

            val entriesString = normalizedEntries.joinToString(separator = ",", prefix = "[", postfix = "]")
            logger.debug("${message.author.username} added $entriesString to $command")
        } else {
            message.respond("This content already exists, try a different one!")
        }

        postPersistConfig()
    }

    private suspend fun BotContext.removeEntry(message: Message) {
        // Only owner, admins and managers can remove entries
        if (!checkManager(message)) return

        val args = message.content
            .replace("""\S+""", " ")
            .split(" ", limit = 3)
            .drop(1)
        val command = args.getOrNull(0)?.trim()

        val content = args.getOrNull(1)?.trim()?.takeUnless(String::isEmpty)

        if (command == null || content == null) {
            message.channel.showHelp()
            return
        }

        val commandEntries = config.commands[command]

        if (commandEntries == null) {
            message.respond("Unknown auto-responder '$command'")
            return
        }

        // Remove content from commands map
        if (commandEntries.remove(content)) {
            message.react(config.getAckEmoji())

            logger.debug("${message.author.username} removed $content from $command")
        } else {
            message.respond("Content not found, nothing was removed.")
        }

        postPersistConfig()
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
                          |`$COMMAND_PREFIX$ADD <command>`""".trimMargin(),
                inline = false,
            ),
        )
    }

    private suspend fun BotContext.checkOwner(message: Message): Boolean {
        if (message.author.id != config.ownerId) {
            message.reply("Insufficient permissions")
            return false
        }
        return true
    }

    private suspend fun BotContext.checkAdmin(message: Message): Boolean {
        val authorId = message.author.id
        if (authorId != config.ownerId && authorId !in config.adminIds) {
            message.reply("Insufficient permissions")
            return false
        }
        return true
    }

    private suspend fun BotContext.checkManager(message: Message): Boolean {
        val authorId = message.author.id
        if (authorId != config.ownerId && authorId !in config.adminIds && authorId !in config.managerIds) {
            message.reply("Insufficient permissions")
            return false
        }
        return true
    }

    private fun postPersistConfig() {
        // Cancel current
        persistJob?.cancel()

        // Start new persistence job
        persistJob = coroutineScope.launch {
            // Delay writing config by 30 seconds
            delay(30 * 1000)

            configHelpers.persistConfig(configFile, config)
        }
    }
}