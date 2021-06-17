package de.maxr1998.diskord

import com.jessecorbett.diskord.api.common.Message
import com.jessecorbett.diskord.bot.BotBase
import com.jessecorbett.diskord.bot.BotContext
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

class DynamicCommandsModule(private val config: Config) {
    private val random: Random = SecureRandom().asKotlinRandom()

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
        if (!message.content.startsWith(Constants.COMMAND_PREFIX)) {
            return
        }

        val command = message.content.substring(Constants.COMMAND_PREFIX.length)
        val commandEntries = config.commands[command] ?: return
        val content = commandEntries.randomOrNull(random) ?: return

        message.respond(content)
    }
}