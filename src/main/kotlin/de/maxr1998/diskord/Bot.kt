package de.maxr1998.diskord

import com.jessecorbett.diskord.AutoGateway
import com.jessecorbett.diskord.api.channel.ChannelClient
import com.jessecorbett.diskord.api.channel.MessageEdit
import com.jessecorbett.diskord.api.common.Attachment
import com.jessecorbett.diskord.api.common.Message
import com.jessecorbett.diskord.api.common.User
import com.jessecorbett.diskord.api.common.UserStatus
import com.jessecorbett.diskord.api.exceptions.DiscordNotFoundException
import com.jessecorbett.diskord.api.gateway.events.MessageReactionAdd
import com.jessecorbett.diskord.api.gateway.model.ActivityType
import com.jessecorbett.diskord.api.gateway.model.UserStatusActivity
import com.jessecorbett.diskord.bot.BotBase
import com.jessecorbett.diskord.bot.BotContext
import com.jessecorbett.diskord.bot.bot
import com.jessecorbett.diskord.util.sendEmbed
import com.jessecorbett.diskord.util.sendMessage
import de.maxr1998.diskord.Constants.COMMAND_PREFIX
import de.maxr1998.diskord.command.ADD
import de.maxr1998.diskord.command.AUTO_RESPONDER
import de.maxr1998.diskord.command.AUTO_RESPONDER_MODE_ADD
import de.maxr1998.diskord.command.AUTO_RESPONDER_MODE_HIDE
import de.maxr1998.diskord.command.AUTO_RESPONDER_MODE_LIST
import de.maxr1998.diskord.command.AUTO_RESPONDER_MODE_PUBLISH
import de.maxr1998.diskord.command.AUTO_RESPONDER_MODE_REMOVE
import de.maxr1998.diskord.command.AUTO_RESPONDER_MODE_RENAME
import de.maxr1998.diskord.command.AUTO_RESPONDER_SHORT
import de.maxr1998.diskord.command.AUTO_RESPONDER_TYPE_GLOBAL
import de.maxr1998.diskord.command.AUTO_RESPONDER_TYPE_HIDDEN
import de.maxr1998.diskord.command.BUILT_IN_COMMANDS
import de.maxr1998.diskord.command.CHECK_ALL
import de.maxr1998.diskord.command.HELP
import de.maxr1998.diskord.command.HELP_ADMIN
import de.maxr1998.diskord.command.HELP_TITLE
import de.maxr1998.diskord.command.PROMOTE
import de.maxr1998.diskord.command.PROMOTE_ADMIN
import de.maxr1998.diskord.command.PROMOTE_ADMIN_SHORT
import de.maxr1998.diskord.command.PROMOTE_SHORT
import de.maxr1998.diskord.command.REMOVE
import de.maxr1998.diskord.command.REMOVE_SHORT
import de.maxr1998.diskord.command.RESOLVE
import de.maxr1998.diskord.command.SET_SOURCE
import de.maxr1998.diskord.command.SOURCE
import de.maxr1998.diskord.command.STATUS
import de.maxr1998.diskord.command.buildEmbed
import de.maxr1998.diskord.command.dynamic.CommandEntryEntity
import de.maxr1998.diskord.command.dynamic.DynamicCommandRepository
import de.maxr1998.diskord.command.dynamic.DynamicCommandRepository.CommandType
import de.maxr1998.diskord.command.dynamic.DynamicCommandsModule
import de.maxr1998.diskord.command.staticCommands
import de.maxr1998.diskord.config.Config
import de.maxr1998.diskord.config.ConfigHelpers
import de.maxr1998.diskord.integration.UrlNormalizer
import de.maxr1998.diskord.integration.resolver.ImageResolver
import de.maxr1998.diskord.util.DatabaseHelpers
import de.maxr1998.diskord.util.EntriesProcessor
import de.maxr1998.diskord.util.diskord.ExtractionResult
import de.maxr1998.diskord.util.diskord.extractEntries
import de.maxr1998.diskord.util.diskord.isAdmin
import de.maxr1998.diskord.util.diskord.isManager
import de.maxr1998.diskord.util.diskord.isOwner
import de.maxr1998.diskord.util.extension.args
import de.maxr1998.diskord.util.extension.getAckEmoji
import de.maxr1998.diskord.util.extension.getRepliedMessage
import de.maxr1998.diskord.util.extension.getUrl
import de.maxr1998.diskord.util.extension.getUser
import de.maxr1998.diskord.util.extension.parsedContentType
import de.maxr1998.diskord.util.extension.toUrlOrNull
import de.maxr1998.diskord.util.logAdd
import de.maxr1998.diskord.util.logRemove
import de.maxr1998.diskord.util.validateCommand
import io.ktor.http.ContentType
import io.ktor.http.Url
import kotlinx.coroutines.delay
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import kotlin.math.min

private val logger = KotlinLogging.logger {}

@Suppress("DuplicatedCode", "TooManyFunctions")
class Bot : KoinComponent {
    private val configHelpers: ConfigHelpers = get()
    private val databaseHelpers: DatabaseHelpers = get()
    private val imageResolver: ImageResolver by inject()
    private val entriesProcessor: EntriesProcessor by inject()
    private val config: Config by configHelpers
    private lateinit var botUser: User

    suspend fun run() {
        logger.debug("Starting Diskord bot…")

        configHelpers.awaitConfig()
        logger.debug("Successfully loaded config.")

        databaseHelpers.setup()
        logger.debug("Successfully connected to database.")

        databaseHelpers.createSchemas()

        bot(config.botToken) {
            registerModule { dispatcher, context ->
                dispatcher.onReady {
                    context.onReady()
                }
            }

            staticCommands(commandPrefix = COMMAND_PREFIX) {
                // Bot management commands
                command(STATUS) { message -> setStatus(this@bot, message) }

                // User management commands
                command(PROMOTE_ADMIN) { message -> promoteAdmin(message) }
                command(PROMOTE_ADMIN_SHORT) { message -> promoteAdmin(message) }
                command(PROMOTE) { message -> promoteManager(message) }
                command(PROMOTE_SHORT) { message -> promoteManager(message) }

                // AR management commands
                command(AUTO_RESPONDER) { message -> autoResponder(message) }
                command(AUTO_RESPONDER_SHORT) { message -> autoResponder(message) }
                command(ADD) { message -> addEntry(message) }
                command(REMOVE) { message -> removeEntry(message) }
                command(REMOVE_SHORT) { message -> removeEntry(message) }
                command(SOURCE) { message -> getSource(message) }
                command(SET_SOURCE) { message -> setSource(message) }
                command(CHECK_ALL) { message -> checkAll(message) }

                // Helper commands
                command(RESOLVE) { message -> resolve(message) }

                // Help
                command(HELP) { message -> helpCommand(message) }
            }

            registerModule { dispatcher, context ->
                dispatcher.onMessageReactionAdd { reaction ->
                    context.onMessageReaction(reaction)
                }
            }

            // Dynamic commands
            registerModule(DynamicCommandsModule())
        }

        // Keep application alive
        while (true) delay(@Suppress("MagicNumber") 100)
    }

    private suspend fun BotContext.onReady() {
        val globalClient = global()
        botUser = globalClient.getUser()

        logger.debug("Bot has user id ${botUser.id} and is in the following server(s):")
        var numberOfGuilds = 0
        var last = "0"
        while (true) {
            val guilds = globalClient.getGuilds(limit = Constants.GUILDS_QUERY_LIMIT, after = last)
            if (guilds.isEmpty()) break

            guilds.forEach { guild ->
                logger.debug("\u21b3 ${guild.name} [id=${guild.id}, perms=${guild.permissions.value}]")
            }
            numberOfGuilds += guilds.size
            if (guilds.size < Constants.GUILDS_QUERY_LIMIT) break
            last = guilds.last().id
        }
        logger.debug("\u21d2 $numberOfGuilds server(s) total.")
    }

    private suspend fun BotContext.setStatus(botBase: BotBase, message: Message) {
        // Status can only be set by the owner
        if (!isOwner(config, message)) {
            message.reply("Insufficient permissions")
            return
        }

        val args = message.args(limit = 2)

        val type = when (args.firstOrNull()) {
            "game" -> ActivityType.GAME
            "stream" -> ActivityType.STREAMING
            "listen" -> ActivityType.LISTENING
            else -> null
        }

        val status = when {
            type == null -> args.joinToString(" ")
            args.size < 2 -> return
            else -> args[1]
        }

        val gateway = BotBase::class.java.getField("gateway").get(botBase) as AutoGateway
        gateway.setStatus(
            status = UserStatus.ONLINE,
            isAfk = false,
            idleTime = null,
            activity = UserStatusActivity(
                name = status,
                type = type ?: ActivityType.GAME,
            ),
        )
    }

    private suspend fun BotContext.promoteAdmin(message: Message) {
        // Admins can only be promoted by the owner
        if (!isOwner(config, message)) {
            message.reply("Insufficient permissions")
            return
        }

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
        if (!isAdmin(config, message)) {
            message.reply("Insufficient permissions")
            return
        }

        // Add mentioned users as managers
        val users = message.usersMentioned.map(User::id)
        if (config.managerIds.addAll(users)) {
            configHelpers.postPersistConfig()
            message.react(config.getAckEmoji())

            logger.debug("${users.joinToString(prefix = "[", postfix = "]")} promoted to manager")
        }
    }

    @Suppress("LongMethod", "ComplexMethod", "MagicNumber")
    private suspend fun BotContext.autoResponder(message: Message) {
        val guild = message.guildId ?: run {
            message.channel.sendNoDmWarning(AUTO_RESPONDER)
            return
        }

        val args = message.args(limit = Int.MAX_VALUE)
        if (args.isEmpty()) { // Require mode
            message.channel.showHelp(HELP_ADMIN)
            return
        }

        val mode = args.first()
        val command = args.getOrNull(1)?.lowercase()

        when (mode) {
            in AUTO_RESPONDER_MODE_ADD -> {
                if (!isAdmin(config, message)) {
                    message.reply("Only admins can add auto-responders")
                    return
                }

                if (args.size != 2 /* <mode> <command> */ || command == null) {
                    message.channel.showHelp(HELP_ADMIN)
                    return
                }

                if (command in BUILT_IN_COMMANDS || !validateCommand(command)) {
                    message.respond("Cannot create auto-responder, '$command' is invalid or a reserved keyword")
                    return
                }

                val existingEntity = DynamicCommandRepository.getCommandByGuild(guild, command)
                if (existingEntity == null && DynamicCommandRepository.addCommandByGuild(guild, command)) {
                    message.respond("Successfully added auto-responder for '$command'")
                    logger.debug("${message.author.username} added auto-responder $command")
                } else {
                    message.respond("Auto-responder for '$command' already exists")
                }
            }
            in AUTO_RESPONDER_MODE_RENAME -> {
                if (!isAdmin(config, message)) {
                    message.reply("Only admins can rename auto-responders")
                    return
                }

                if (args.size != 3 /* <mode> <command> <new_name> */ || command == null) {
                    message.channel.showHelp(HELP_ADMIN)
                    return
                }

                val target = args[2].lowercase()
                val existingTarget = DynamicCommandRepository.getCommandByGuild(guild, target)
                if (existingTarget != null) {
                    message.respond("Auto-responder '$target' already exists, choose another name")
                } else if (DynamicCommandRepository.renameCommandByGuild(guild, command, target)) {
                    message.respond("Successfully renamed auto-responder '$command' to '$target'")
                    logger.debug("${message.author.username} renamed auto-responder $command to $target")
                } else {
                    message.respond("Unknown auto-responder '$command'")
                }
            }
            in AUTO_RESPONDER_MODE_LIST -> {
                val type = when (command) {
                    AUTO_RESPONDER_TYPE_GLOBAL -> CommandType.GLOBAL_ONLY
                    AUTO_RESPONDER_TYPE_HIDDEN -> CommandType.HIDDEN
                    else -> CommandType.ALL_VISIBLE
                }

                if (type == CommandType.HIDDEN && !isOwner(config, message)) {
                    message.reply("Deep down in the darkness they lie, unable for you to see…")
                    return
                }

                if (args.size != 1 /* <mode> */ && (args.size != 2 /* <mode> global/hidden */ || type == CommandType.ALL_VISIBLE)) {
                    message.channel.showHelp(HELP_ADMIN)
                    return
                }

                val commands = DynamicCommandRepository.getCommandsByGuild(guild, type)

                message.respond {
                    title = "Available auto-responders"
                    description = commands.joinToString("\n") { (cmd, global, count) ->
                        "\u2022 ` $cmd `${if (global) " **[$AUTO_RESPONDER_TYPE_GLOBAL]**" else ""} - $count entries"
                    }
                }
            }
            AUTO_RESPONDER_MODE_PUBLISH -> {
                if (!isOwner(config, message)) {
                    message.reply("Only owners can publish auto-responders globally")
                    return
                }

                if (args.size != 2 /* <mode> <command> */ || command == null) {
                    return
                }

                if (DynamicCommandRepository.publishCommandByGuild(guild, command)) {
                    message.respond("Successfully published auto-responder '$command'")
                    logger.debug("${message.author.username} published auto-responder $command globally")
                } else {
                    message.respond("Unknown auto-responder '$command'")
                }
            }
            AUTO_RESPONDER_MODE_HIDE -> {
                if (!isAdmin(config, message)) {
                    message.reply("Only admins can hide auto-responders")
                    return
                }

                if (args.size != 2 /* <mode> <command> */ || command == null) {
                    message.channel.showHelp(HELP_ADMIN)
                    return
                }

                if (DynamicCommandRepository.hideCommandByGuild(guild, command)) {
                    message.respond("Successfully hid auto-responder '$command' from list")
                    logger.debug("${message.author.username} hid auto-responder $command")
                } else {
                    message.respond("Unknown auto-responder '$command'")
                }
            }
            in AUTO_RESPONDER_MODE_REMOVE -> {
                if (!isAdmin(config, message)) {
                    message.reply("Only admins can remove auto-responders")
                    return
                }

                if (args.size != 2 /* <mode> <command> */ || command == null) {
                    message.channel.showHelp(HELP_ADMIN)
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

    @Suppress("LongMethod", "ComplexMethod")
    private suspend fun BotContext.addEntry(message: Message) {
        // Only owner, admins and managers can add new entries
        if (!isManager(config, message)) {
            message.reply("Only managers can add entries")
            return
        }

        val maySaveImages = isOwner(config, message)

        val guild = message.guildId ?: run {
            message.channel.sendNoDmWarning(ADD)
            return
        }

        val args = message.args(limit = 2)

        val commandString = args.getOrNull(0)?.lowercase() ?: run {
            message.channel.showHelp(ADD)
            return
        }

        val commands = commandString.split(';').map { commandSet -> commandSet.split(',') }

        val commandEntities = commands.map { commandSet ->
            commandSet.map { command ->
                DynamicCommandRepository.getCommandByGuild(guild, command) ?: run {
                    message.respond("Unknown auto-responder '$command', aborting…")
                    return
                }
            }
        }

        val entries = when (val extractionResult = extractEntries(args, message)) {
            null -> {
                message.channel.showHelp(ADD)
                return
            }
            is ExtractionResult.Raw -> {
                listOf(CommandEntryEntity.text(extractionResult.content, null))
            }
            is ExtractionResult.Lines -> {
                val urls = mutableListOf<Url>()
                for (line in extractionResult.content) {
                    when (val url = line.removeSurrounding("<", ">").toUrlOrNull()) {
                        null -> {
                            urls.clear()
                            break
                        }
                        else -> urls.add(url)
                    }
                }
                val resolverResults = urls.mapNotNull { url ->
                    imageResolver.resolve(url, maySaveImages).takeUnless { result ->
                        result.isFailure && result.exceptionOrNull() == ImageResolver.Status.Unsupported
                    }?.let { result ->
                        url to result
                    }
                }
                if (resolverResults.isNotEmpty()) {
                    if (commandEntities.size != 1 && resolverResults.size > 1) {
                        message.respond("Command sets aren't supported when resolving more than one link")
                        return
                    }
                    for ((url, result) in resolverResults) {
                        result.onSuccess { resolved ->
                            // Resolved images, add to database
                            val imageUrls = resolved.imageUrls
                            if (commandEntities.size != 1 && commandEntities.size != imageUrls.size) {
                                message.respond("Number of command sets doesn't match entry count")
                                return
                            }
                            if (DynamicCommandRepository.addCommandEntries(commandEntities, imageUrls)) {
                                val response = when (commands.size) {
                                    1 -> {
                                        val commandsString = commands.first().joinToString { command -> "`$command`" }
                                        val imagesString = imageUrls.joinToString(prefix = "\n", separator = "\n", limit = Constants.MAX_PREVIEW_IMAGES, transform = CommandEntryEntity::content)
                                        "Resolved ${imageUrls.size} image(s) from <${resolved.url}> and added them to $commandsString\n$imagesString"
                                    }
                                    imageUrls.size -> {
                                        val imagesString = imageUrls.indices.joinToString(prefix = "\n", separator = "\n", limit = Constants.MAX_PREVIEW_IMAGES) { index ->
                                            val imageUrl = imageUrls[index].content
                                            val commandsString = commands[index].joinToString { command -> "`$command`" }
                                            "$imageUrl → $commandsString"
                                        }
                                        "Resolved ${imageUrls.size} image(s) from <${resolved.url}> and added them to the respective commands:\n$imagesString"
                                    }
                                    else -> error("Mismatch in number of command sets and entries")
                                }
                                message.respond(response.take(Constants.MAX_MESSAGE_LENGTH))
                                logger.logAdd(message.author, commands, imageUrls)
                            } else {
                                message.respond("All content from `${resolved.url}` has already been added previously!")
                            }
                        }.onFailure { exception ->
                            require(exception is ImageResolver.Status.Failure)
                            val errorText = when (exception) {
                                ImageResolver.Status.Forbidden -> "Insufficient permissions to use this feature."
                                ImageResolver.Status.RateLimited -> "Rate-limit exceeded for <$url>, please try again later."
                                ImageResolver.Status.ParsingFailed -> "Parsing of <$url> failed, please contact the developer."
                                ImageResolver.Status.Unknown -> "Couldn't process <$url>.\nPlease ensure your query is correct."
                            }
                            message.respond(errorText)
                        }
                    }
                    return
                }

                // Normalize URLs
                extractionResult.content.map { content ->
                    val normalizedUrl = UrlNormalizer.normalizeUrls(content)
                    CommandEntryEntity.tryUrl(normalizedUrl, null)
                }
            }
            is ExtractionResult.Attachments -> {
                val attachments = extractionResult.content

                val entries = attachments.mapNotNull { attachment ->
                    val url = UrlNormalizer.normalizeUrls(attachment.url)
                    val contentType = attachment.parsedContentType
                    val width = attachment.imageWidth
                    val height = attachment.imageHeight

                    if (width == null || height == null || contentType == null) {
                        return@mapNotNull CommandEntryEntity.tryUrl(url, message.getUrl())
                    }

                    val minSize = min(width, height)

                    when {
                        contentType.match(ContentType.Image.GIF) -> when {
                            minSize >= Constants.MIN_VIDEO_SIZE ->
                                CommandEntryEntity.gif(url, message.getUrl(), width, height)
                            else -> null
                        }
                        contentType.match(ContentType.Image.Any) -> when {
                            minSize >= Constants.MIN_IMAGE_SIZE ->
                                CommandEntryEntity.image(url, message.getUrl(), width, height)
                            else -> null
                        }
                        contentType.match(ContentType.Video.Any) -> when {
                            minSize >= Constants.MIN_VIDEO_SIZE ->
                                CommandEntryEntity.video(url, message.getUrl(), width, height)
                            else -> null
                        }
                        else -> null
                    }
                }

                val diff = attachments.size - entries.size
                if (diff > 0) {
                    message.respond(
                        "$diff of ${attachments.size} attachments weren't added " +
                            "as they didn't fulfill the minimum resolution requirements:\n" +
                            "\u2022 Images: ${Constants.MIN_IMAGE_SIZE}x${Constants.MIN_IMAGE_SIZE} pixels\n" +
                            "\u2022 Videos: ${Constants.MIN_VIDEO_SIZE}x${Constants.MIN_VIDEO_SIZE} pixels",
                    )

                    // Abort if empty
                    if (entries.isEmpty()) return
                }

                entries
            }
        }

        if (commandEntities.size != 1 && commandEntities.size != entries.size) {
            message.respond("Number of command sets doesn't match entry count")
            return
        }

        // Add content to commands map
        if (DynamicCommandRepository.addCommandEntries(commandEntities, entries)) {
            message.react(config.getAckEmoji())
            logger.logAdd(message.author, commands, entries)
        } else {
            message.respond("Content was already added previously - updating with new data if necessary")
        }
    }

    private suspend fun BotContext.removeEntry(message: Message) {
        // Only owner, admins and managers can remove entries
        if (!isManager(config, message)) {
            message.reply("Only managers can remove entries")
            return
        }

        val guild = message.guildId ?: run {
            message.channel.sendNoDmWarning(REMOVE)
            return
        }

        val args = message.args(limit = 2)

        val command = args.getOrNull(0)?.lowercase() ?: run {
            message.channel.showHelp(REMOVE)
            return
        }

        val commandEntity = DynamicCommandRepository.getCommandByGuild(guild, command) ?: run {
            message.respond("Unknown auto-responder '$command'")
            return
        }

        val entries = when (val extractionResult = extractEntries(args, message)) {
            null -> {
                message.channel.showHelp(REMOVE)
                return
            }
            is ExtractionResult.Raw -> listOf(extractionResult.content)
            is ExtractionResult.Lines -> extractionResult.content
            is ExtractionResult.Attachments -> extractionResult.content.map(Attachment::url)
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

    private suspend fun BotContext.getSource(message: Message) {
        val args = message.args(limit = 1)
        val content = args.singleOrNull() ?: message.getRepliedMessage(this)?.let { repliedMessage ->
            if (repliedMessage.author.id == botUser.id) {
                repliedMessage.content
            } else {
                message.respond("The `$COMMAND_PREFIX$SOURCE` command only works for replies to messages by the bot")
                return
            }
        } ?: run {
            message.channel.showHelp(SOURCE)
            return
        }

        val normalizedContent = content.trim().removeSurrounding("<", ">")

        val entry = DynamicCommandRepository.getCommandEntry(normalizedContent) ?: run {
            message.respond("Content not found")
            return
        }

        val source = entry.contentSource
        if (source != null) {
            message.respond("Found source at $source")
        } else {
            message.respond("Missing source for content")
        }
    }

    private suspend fun BotContext.setSource(message: Message) {
        // Only owner, admins and managers can update the source
        if (!isManager(config, message)) {
            message.reply("Only managers can update the source")
            return
        }

        val args = message.args(limit = 2)

        val source = args.getOrNull(0) ?: run {
            message.channel.showHelp(SET_SOURCE)
            return
        }

        val entries = when (val extractionResult = extractEntries(args, message)) {
            null -> {
                message.channel.showHelp(SET_SOURCE)
                return
            }
            is ExtractionResult.Raw -> listOf(extractionResult.content)
            is ExtractionResult.Lines -> extractionResult.content
            is ExtractionResult.Attachments -> {
                message.channel.showHelp(SET_SOURCE)
                return
            }
        }

        if (DynamicCommandRepository.setSourceForEntries(source, entries)) {
            message.react(config.getAckEmoji())
            val entriesString = entries.joinToString(
                separator = ",",
                prefix = "[",
                postfix = "]",
            )
            logger.debug("${message.author.username} set source for $entriesString to $source")
        } else {
            message.respond("Unable to find any entries to update.")
        }
    }

    private suspend fun BotContext.checkAll(message: Message) {
        if (!isOwner(config, message)) {
            message.reply("Only the owner can request to check all entries")
            return
        }

        val startOffset = message.args(1).firstOrNull()?.toLongOrNull() ?: 0L
        entriesProcessor.checkAndFlagRemovedLinks(startOffset)
    }

    private suspend fun BotContext.resolve(message: Message) {
        val maySaveImages = isOwner(config, message)

        val content = message.content.removePrefix("$COMMAND_PREFIX$RESOLVE")
        val url = content.trim().removeSurrounding("<", ">").toUrlOrNull() ?: run {
            message.respond("Not a link. Try something else.")
            return
        }

        imageResolver.resolve(url, maySaveImages).onSuccess { resolved ->
            val images = resolved.imageUrls
            DynamicCommandRepository.updateCommandEntries(images)
            for (from in images.indices step Constants.MAX_PREVIEW_IMAGES) {
                val to = (from + Constants.MAX_PREVIEW_IMAGES).coerceAtMost(images.size)
                val chunk = images.subList(from, to)
                message.respond(chunk.joinToString(separator = "\n", transform = CommandEntryEntity::content))
            }
        }.onFailure { exception ->
            require(exception is ImageResolver.Status)
            val errorText = when (exception) {
                ImageResolver.Status.Unsupported -> "Unsupported content. Try a different link."
                ImageResolver.Status.Forbidden -> "Insufficient permissions to use this feature."
                ImageResolver.Status.RateLimited -> "Rate-limit exceeded, please try again later."
                ImageResolver.Status.ParsingFailed -> "Parsing failed, please contact the developer."
                ImageResolver.Status.Unknown -> "Couldn't process content, please ensure your query is correct."
            }
            message.respond(errorText)
        }
    }

    private suspend fun BotContext.helpCommand(message: Message) {
        val args = message.args(limit = 1)

        message.channel.showHelp(command = args.getOrNull(0))
    }

    private suspend fun ChannelClient.showHelp(command: String? = null) = sendEmbed {
        title = HELP_TITLE
        color = Constants.HELP_COLOR

        buildEmbed(command)
    }

    private suspend fun ChannelClient.sendNoDmWarning(command: String) =
        sendMessage("Command `$command` cannot be used in DMs.")

    private suspend fun BotContext.onMessageReaction(messageReactionAdd: MessageReactionAdd) {
        val channelClient = channel(messageReactionAdd.channelId)
        val message = try {
            channelClient.getMessage(messageReactionAdd.messageId)
        } catch (e: DiscordNotFoundException) {
            logger.error("Message ${messageReactionAdd.messageId} not found in ${messageReactionAdd.channelId}")
            return
        } catch (e: Exception) {
            logger.error("Encountered $e while retrieving message ${messageReactionAdd.messageId} in ${messageReactionAdd.channelId}")
            return
        }
        val emoji = messageReactionAdd.emoji

        // Ignore reactions to messages not from the bot
        if (message.author.id != botUser.id) return

        when (emoji.name) {
            "\u2753" -> { // ❓
                val entry = DynamicCommandRepository.getCommandEntry(message.content) ?: return
                val source = entry.contentSource ?: return

                channelClient.editMessage(message.id, MessageEdit(content = "Source: <$source>\n\n${entry.content}"))
            }
            "\u274C", "\u2716\uFE0F" -> { // ❌ ✖
                val user = messageReactionAdd.getUser(this) ?: return
                if (!isAdmin(config, user)) return

                val guild = messageReactionAdd.guildId ?: return
                val entry = DynamicCommandRepository.getCommandEntry(message.content) ?: return

                if (DynamicCommandRepository.removeEntryForGuild(entry.content, guild)) {
                    channelClient.editMessage(message.id, MessageEdit(content = "Removed <${entry.content}>"))
                }
            }
        }
    }
}