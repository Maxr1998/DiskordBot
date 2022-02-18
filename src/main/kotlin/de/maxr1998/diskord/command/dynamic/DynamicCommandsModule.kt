package de.maxr1998.diskord.command.dynamic

import com.jessecorbett.diskord.api.common.Message
import com.jessecorbett.diskord.api.exceptions.DiscordException
import com.jessecorbett.diskord.bot.BotBase
import com.jessecorbett.diskord.bot.BotContext
import com.jessecorbett.diskord.util.isFromBot
import de.maxr1998.diskord.Constants
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class DynamicCommandsModule {

    /**
     * Installs the feature that provides support for dynamic commands
     */
    fun install(botBase: BotBase) {
        botBase.registerModule { dispatcher, context ->
            dispatcher.onMessageCreate { message ->
                context.handleMessage(message)
            }
        }
    }

    private suspend fun BotContext.handleMessage(message: Message) {
        when {
            !message.content.startsWith(Constants.COMMAND_PREFIX) -> return
            message.content.any(Char::isWhitespace) -> return
            message.isFromBot -> return
        }

        val guild = message.guildId ?: return
        val command = message.content.substring(Constants.COMMAND_PREFIX.length).lowercase()
        val commandEntity = DynamicCommandRepository.getCommandByGuild(guild, command) ?: return
        val content = DynamicCommandRepository.getRandomEntry(commandEntity) ?: return

        try {
            message.respond(content)
        } catch (e: DiscordException) {
            logger.error("Replying to %$command caused exception $e", e)
        }
    }
}