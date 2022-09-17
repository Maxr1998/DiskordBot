package de.maxr1998.diskord.command

import com.jessecorbett.diskord.api.channel.Embed
import com.jessecorbett.diskord.api.channel.EmbedField
import de.maxr1998.diskord.Constants.COMMAND_PREFIX

const val HELP_TITLE = "Help"
const val LIST_HELP_DESC = """
A list of commands provided by the bot.
"""

const val ADD_DESC = """```
$COMMAND_PREFIX$ADD command entry
```
Adds entries (text, links, images) to a responder.
Automatically splits on lines, allowing to add multiple entries at once.

If the entry is a link to Twitter, a Naver post or an Imgur album, images from it will automatically be resolved! Videos are not supported.

Replying with this command to a message, omitting the entry, will treat the replied message as content.

Attachments can also be uploaded, but should be rarely used since Discord will delete the media if the original message is removed.
Also, they generally break the de-duplication of the bot, causing duplicate entries to be potentially added.
Attachments work for both the message of the command itself as well as replies. When using them, the content of the message itself is ignored.
"""

const val REMOVE_DESC = """```
$COMMAND_PREFIX$REMOVE command entry
```
Remove entries from a responder.
Works similar to `$ADD`, but instead removes entries.
"""

const val SOURCE_DESC = """```
$COMMAND_PREFIX$SOURCE content
```
Shows the source of the content if available.

Replying with this command to a message by the bot, omitting the entry, will treat the replied message as content.

Additionally, it's possible to react to a message by the bot with ❓ to retrieve the source.
"""

const val SET_SOURCE_DESC = """```
$COMMAND_PREFIX$SET_SOURCE source entry
```
Updates the source for the given entries.
Automatically splits on lines, allowing to update multiple entries at once.

Replying with this command to a message, omitting the entry, will treat the replied message as content.
"""

const val RESOLVE_DESC = """```
$COMMAND_PREFIX$RESOLVE link
```
Resolves images from a link.
Currently supported are Twitter, Imgur albums and Naver (Instagram is not publicly available).
Videos are generally not supported.
"""

const val HELP_DESC = """```
$COMMAND_PREFIX$HELP
$COMMAND_PREFIX$HELP command
```
Shows this help. Add `command` for details on a single command.

For a list of admin-only commands, see
```
$COMMAND_PREFIX$HELP $HELP_ADMIN
```
"""

@Suppress("LongMethod")
fun Embed.buildEmbed(command: String?) {
    when (command) {
        null -> {
            description = LIST_HELP_DESC
            fields = mutableListOf(
                EmbedField(
                    name = ADD.uppercase(),
                    value = ADD_DESC,
                    inline = false,
                ),
                EmbedField(
                    name = REMOVE.uppercase(),
                    value = REMOVE_DESC,
                    inline = false,
                ),
                EmbedField(
                    name = SOURCE.uppercase(),
                    value = SOURCE_DESC,
                    inline = false,
                ),
                EmbedField(
                    name = SET_SOURCE.uppercase(),
                    value = SET_SOURCE_DESC,
                    inline = false,
                ),
                EmbedField(
                    name = RESOLVE.uppercase(),
                    value = RESOLVE_DESC,
                    inline = false,
                ),
                EmbedField(
                    name = HELP.uppercase(),
                    value = HELP_DESC,
                    inline = false,
                ),
            )
        }
        HELP_ADMIN -> {
            description = "Moderation commands. **Only for admins.**"
            fields = mutableListOf(
                EmbedField(
                    name = "Add a new auto-responder",
                    value = """`$COMMAND_PREFIX$AUTO_RESPONDER ${AUTO_RESPONDER_MODE_ADD[0]} command`""",
                    inline = false,
                ),
                EmbedField(
                    name = "Show all auto-responders",
                    value = """`$COMMAND_PREFIX$AUTO_RESPONDER ${AUTO_RESPONDER_MODE_LIST[0]} (global)`""",
                    inline = false,
                ),
                EmbedField(
                    name = "Publish auto-responder globally",
                    value = """`$COMMAND_PREFIX$AUTO_RESPONDER $AUTO_RESPONDER_MODE_HIDE command`""",
                    inline = false,
                ),
                EmbedField(
                    name = "Hide auto-responder (from list)",
                    value = """`$COMMAND_PREFIX$AUTO_RESPONDER $AUTO_RESPONDER_MODE_HIDE command`""",
                    inline = false,
                ),
                EmbedField(
                    name = "Remove auto-responder - also deletes all its entries",
                    value = """`$COMMAND_PREFIX$AUTO_RESPONDER ${AUTO_RESPONDER_MODE_REMOVE[0]} command`""",
                    inline = false,
                ),
            )
        }
        ADD -> {
            title = ADD.uppercase()
            description = ADD_DESC
        }
        REMOVE -> {
            title = REMOVE.uppercase()
            description = REMOVE_DESC
        }
        SOURCE -> {
            title = SOURCE.uppercase()
            description = SOURCE_DESC
        }
        SET_SOURCE -> {
            title = SET_SOURCE.uppercase()
            description = SET_SOURCE_DESC
        }
        RESOLVE -> {
            title = RESOLVE.uppercase()
            description = RESOLVE_DESC
        }
    }
}