package de.maxr1998.diskord.command

import com.jessecorbett.diskord.api.common.Message
import com.jessecorbett.diskord.api.exceptions.DiscordException
import com.jessecorbett.diskord.api.gateway.EventDispatcher
import com.jessecorbett.diskord.bot.BotBase
import com.jessecorbett.diskord.bot.BotContext
import io.ktor.client.network.sockets.ConnectTimeoutException
import kotlinx.coroutines.TimeoutCancellationException
import mu.KotlinLogging
import java.nio.channels.UnresolvedAddressException

private val logger = KotlinLogging.logger {}

typealias CommandHandler = suspend BotContext.(Message) -> Unit

@DslMarker
annotation class StaticCommands

fun interface GuildPrefix {
    suspend fun getPrefix(guildId: String?): String
}

@StaticCommands
fun BotBase.staticCommands(
    commandPrefix: GuildPrefix = GuildPrefix { "." },
    commands: CommandBuilder.() -> Unit,
) {
    val handlers = CommandBuilder().apply { commands() }.build()
    registerModule(StaticCommandsModule(commandPrefix, handlers))
}

@StaticCommands
fun BotBase.staticCommands(
    commandPrefix: String = ".",
    commands: CommandBuilder.() -> Unit,
) {
    staticCommands(commandPrefix = { commandPrefix }, commands)
}

@StaticCommands
class CommandBuilder {
    private val commands: MutableMap<String, CommandHandler> = HashMap()

    /**
     * Registers a classic command on the bot
     */
    @StaticCommands
    fun command(key: String, block: CommandHandler) {
        require(!commands.containsKey(key)) { "Handler was already registered for command '$key'" }
        commands[key] = block
    }

    internal fun build(): Map<String, CommandHandler> = commands.toMap()
}

internal class StaticCommandsModule(
    private val guildPrefix: GuildPrefix,
    private val handlers: Map<String, CommandHandler>,
) : BotBase.BotModule {
    override suspend fun register(dispatcher: EventDispatcher, context: BotContext, configuring: Boolean) {
        dispatcher.onMessageCreate { message ->
            val prefix = guildPrefix.getPrefix(message.guildId)

            if (message.content.startsWith(prefix)) {
                val command = with(message.content) {
                    val delimiterIndex = indexOfAny(charArrayOf(' ', '\n'))
                    if (delimiterIndex == -1) substring(prefix.length) else substring(prefix.length, delimiterIndex)
                }

                handlers[command]?.let { handler ->
                    try {
                        context.handler(message)
                    } catch (e: TimeoutCancellationException) {
                        logger.error("Timed out while handling $command", e)
                    } catch (e: DiscordException) {
                        logger.error("Handling command $command caused exception $e", e)
                    } catch (e: ConnectTimeoutException) {
                        logger.error("Connection timed out while handling $command", e)
                    } catch (e: UnresolvedAddressException) {
                        logger.error("Failed to resolve address", e)
                    }
                }
            }
        }
    }
}