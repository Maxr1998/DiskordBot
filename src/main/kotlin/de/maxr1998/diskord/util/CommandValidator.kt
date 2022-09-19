package de.maxr1998.diskord.util

/**
 * Some special characters which are allowed in commands.
 * Notably excludes "%" and ",", which are prefix and separator characters.
 */
private const val COMMAND_ALLOWED_SPECIAL_CHARS = "!#&*+-./:<>=?_"

/**
 * Ensure only allowed chars are in the command string
 */
fun validateCommand(command: String): Boolean = command.all { char ->
    char.isLetterOrDigit() || char in COMMAND_ALLOWED_SPECIAL_CHARS
}