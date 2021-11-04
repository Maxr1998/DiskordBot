package de.maxr1998.diskord

import com.jessecorbett.diskord.api.channel.Embed
import com.jessecorbett.diskord.api.channel.EmbedField
import de.maxr1998.diskord.Command.ADD
import de.maxr1998.diskord.Command.AUTO_RESPONDER
import de.maxr1998.diskord.Command.AUTO_RESPONDER_MODE_ADD
import de.maxr1998.diskord.Command.AUTO_RESPONDER_MODE_HIDE
import de.maxr1998.diskord.Command.AUTO_RESPONDER_MODE_LIST
import de.maxr1998.diskord.Command.AUTO_RESPONDER_MODE_REMOVE
import de.maxr1998.diskord.Command.HELP
import de.maxr1998.diskord.Command.HELP_ADMIN
import de.maxr1998.diskord.Command.REMOVE
import de.maxr1998.diskord.Command.RESOLVE
import de.maxr1998.diskord.Command.SOURCE
import de.maxr1998.diskord.Constants.COMMAND_PREFIX

const val HELP_TITLE = "Help"
const val LIST_HELP_DESC = """A list of commands provided by the bot.
For more details on a specific command, enter:
```
$COMMAND_PREFIX$HELP <command>
```"""

const val ADD_DESC = """Adds entries (text, links, images) to a responder.
Automatically splits on lines, allowing to add multiple entries at once.

```
$COMMAND_PREFIX$ADD command entry
```

If the entry is a link to Twitter, a Naver post or an Imgur album, images from it will automatically be resolved! Videos are not supported.

Replying with this command to a message, omitting the entry, will treat the replied message as content.

Attachments can also be uploaded, but should be rarely used since Discord will delete the media if the original message is removed.
Also, they generally break the de-duplication of the bot, causing duplicate entries to be potentially added.
Attachments work for both the message of the command itself as well as replies. When using them, the content of the message itself is ignored.
"""

const val REMOVE_DESC = """Remove entries from a responder.
Works similar to `$ADD`, but instead removes entries.

```
$COMMAND_PREFIX$REMOVE command entry
```"""

const val SOURCE_DESC = """Shows the source of the content if available.

Replying with this command to a message by the bot, omitting the entry, will treat the replied message as content.

Additionally, it's possible to react to a message by the bot with ❓ to retrieve the source.

```
$COMMAND_PREFIX$SOURCE content
```
"""

const val RESOLVE_DESC = """Resolves images from a link.
Currently supported are Twitter, Imgur albums and Naver (Instagram is not publicly available).
Videos are generally not supported.

```
$COMMAND_PREFIX$RESOLVE link
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
                    name = RESOLVE.uppercase(),
                    value = RESOLVE_DESC,
                    inline = false,
                ),
            )
        }
        HELP_ADMIN -> {
            description = "Moderation commands. **Only for admins.**"
            fields = mutableListOf(
                EmbedField(
                    name = "Add auto-responder",
                    value = """`$COMMAND_PREFIX$AUTO_RESPONDER ${AUTO_RESPONDER_MODE_ADD[0]} <command>`""",
                    inline = false,
                ),
                EmbedField(
                    name = "Show auto-responders",
                    value = """`$COMMAND_PREFIX$AUTO_RESPONDER ${AUTO_RESPONDER_MODE_LIST[0]} <command>`""",
                    inline = false,
                ),
                EmbedField(
                    name = "Hide auto-responder",
                    value = """`$COMMAND_PREFIX$AUTO_RESPONDER $AUTO_RESPONDER_MODE_HIDE <command>`""",
                    inline = false,
                ),
                EmbedField(
                    name = "Remove auto-responder",
                    value = """`$COMMAND_PREFIX$AUTO_RESPONDER ${AUTO_RESPONDER_MODE_REMOVE[0]} <command>`""",
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
        RESOLVE -> {
            title = RESOLVE.uppercase()
            description = RESOLVE_DESC
        }
    }
}