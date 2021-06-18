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
import de.maxr1998.diskord.Constants.COMMAND_PREFIX
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.io.File

val logger = KotlinLogging.logger {}

class Bot(private val configFile: File) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var config: Config
    private var persistJob: Job? = null

    suspend fun run() {
        logger.debug("Starting Diskord bot…")

        config = ConfigHelpers.readConfig(configFile)

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
        if (message.author.id != config.ownerId) {
            message.reply("Insufficient permissions")
            return
        }

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
        val sender = message.author.id
        if (sender != config.ownerId && sender !in config.adminIds) {
            message.reply("Insufficient permissions")
            return
        }

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
        val sender = message.author
        if (sender.id != config.ownerId && sender.id !in config.adminIds) {
            message.reply("Insufficient permissions")
            return
        }

        val args = message.words.drop(1)
        if (args.size !in 1..2) {
            message.channel.showHelp()
            return
        }

        val mode = args[0]
        val command = args.getOrNull(1)

        when (mode) {
            AUTO_RESPONDER_MODE_ADD -> {
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
                logger.debug("${sender.username} added auto-responder $command")
            }
            AUTO_RESPONDER_MODE_LIST -> {
                if (args.size != 1) {
                    message.channel.showHelp()
                    return
                }

                val commands = config.commands.keys.sorted()

                message.replyEmbed {
                    title = "Available auto-responders"
                    description = commands.joinToString("\n") { cmd -> "- ` $cmd `" }
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
                logger.debug("${sender.username} removed auto-responder $command")
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
        val sender = message.author
        if (sender.id != config.ownerId && sender.id !in config.adminIds && sender.id !in config.managerIds) {
            message.reply("Insufficient permissions")
            return
        }

        val args = message.content
            .replace("""\S+""", " ")
            .split(" ", limit = 3)
            .drop(1)
        val command = args.getOrNull(0)?.trim()
        val repliedMessage = message.reference?.messageId

        val content = when {
            repliedMessage != null && args.size == 1 -> {
                message.channel.getMessage(repliedMessage).content.trim()
            }
            args.size == 2 -> args[1].trim()
            else -> null
        }

        if (command == null || content == null || content.isEmpty()) {
            message.channel.showHelp()
            return
        }

        val commandEntries = config.commands[command]

        if (commandEntries == null) {
            message.respond("Unknown auto-responder '$command'")
            return
        }

        // Normalize URLs
        val normalizedContent = UrlNormalizer.normalizeUrls(content)

        // Add content to commands map
        if (commandEntries.add(normalizedContent)) {
            message.react(config.getAckEmoji())

            logger.debug("${sender.username} added $normalizedContent to $command")
        } else {
            message.respond("This content already exists, try a different one!")
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
                value = """`$COMMAND_PREFIX$AUTO_RESPONDER $AUTO_RESPONDER_MODE_ADD <command>`
                          |`$COMMAND_PREFIX$AUTO_RESPONDER_SHORT $AUTO_RESPONDER_MODE_ADD <command>`""".trimMargin(),
                inline = false,
            ),
            EmbedField(
                name = "Remove auto-responder - *admin only*",
                value = """`$COMMAND_PREFIX$AUTO_RESPONDER ${AUTO_RESPONDER_MODE_REMOVE[0]} <command>`
                          |`$COMMAND_PREFIX$AUTO_RESPONDER_SHORT ${AUTO_RESPONDER_MODE_REMOVE[1]} <command>`""".trimMargin(),
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

    private fun postPersistConfig() {
        // Cancel current
        persistJob?.cancel()

        // Start new persistence job
        persistJob = coroutineScope.launch {
            // Delay writing config by 30 seconds
            delay(10 * 1000)

            ConfigHelpers.persistConfig(configFile, config)
        }
    }
}