package de.maxr1998.diskord

import com.jessecorbett.diskord.api.gateway.events.MessageReactionAdd
import com.jessecorbett.diskord.bot.BotBase
import com.jessecorbett.diskord.bot.BotContext
import com.jessecorbett.diskord.bot.bot
import de.maxr1998.diskord.command.CommandBuilder
import de.maxr1998.diskord.command.dynamic.DynamicCommandsModule
import de.maxr1998.diskord.command.staticCommands
import de.maxr1998.diskord.config.Config
import de.maxr1998.diskord.config.ConfigHelpers
import de.maxr1998.diskord.util.DatabaseHelpers
import kotlinx.coroutines.delay
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val logger = KotlinLogging.logger { }

abstract class BaseBot : KoinComponent {
    protected val configHelpers: ConfigHelpers = get()
    protected val config: Config by configHelpers
    private val databaseHelpers: DatabaseHelpers = get()

    suspend fun run() {
        logger.debug("Starting Diskord bot…")

        configHelpers.awaitConfig()
        logger.debug("Successfully loaded config.")

        databaseHelpers.setup()
        logger.debug("Successfully connected to database.")

        databaseHelpers.createSchemas()

        bot(config.botToken) {
            registerModule { dispatcher, context, configuring ->
                dispatcher.onReady {
                    context.onReady()
                }
            }

            staticCommands(commandPrefix = Constants.COMMAND_PREFIX) {
                setupStaticCommands(this@bot)
            }

            registerModule { dispatcher, context, configuring ->
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

    protected abstract fun CommandBuilder.setupStaticCommands(botBase: BotBase)

    protected abstract suspend fun BotContext.onReady()

    protected abstract suspend fun BotContext.onMessageReaction(reaction: MessageReactionAdd)
}