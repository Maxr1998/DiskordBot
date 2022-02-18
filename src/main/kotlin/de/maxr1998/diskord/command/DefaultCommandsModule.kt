package de.maxr1998.diskord.command

import com.jessecorbett.diskord.api.common.Message
import com.jessecorbett.diskord.api.exceptions.DiscordException
import com.jessecorbett.diskord.api.gateway.EventDispatcher
import com.jessecorbett.diskord.api.gateway.events.DiscordEvent
import com.jessecorbett.diskord.bot.BotBase
import com.jessecorbett.diskord.bot.BotContext
import com.jessecorbett.diskord.bot.ClassicCommandModule
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

@DslMarker
annotation class DefaultCommandsModule

@DefaultCommandsModule
fun BotBase.defaultCommands(commandPrefix: String = ".", commands: CommandBuilder.() -> Unit) {
    registerModule { dispatcher, context ->
        CommandBuilder(commandPrefix, dispatcher, context).commands()
    }
}

@DefaultCommandsModule
class CommandBuilder(
    private val prefix: String,
    private val dispatcher: EventDispatcher<Unit>,
    private val botContext: BotContext
) {
    /**
     * Creates a command listener on [DiscordEvent.MESSAGE_CREATE] events
     */
    @ClassicCommandModule
    fun command(key: String, block: suspend BotContext.(Message) -> Unit) {
        dispatcher.onMessageCreate { message ->
            if (
                message.content.startsWith("$prefix$key ") ||
                message.content.startsWith("$prefix$key\n") ||
                message.content == "$prefix$key"
            ) {
                try {
                    botContext.block(message)
                } catch (e: DiscordException) {
                    logger.error("Handling command $key caused exception $e", e)
                }
            }
        }
    }
}