package de.maxr1998.diskord.permission

object Permission {

    // COMMAND GROUP

    /**
     * Add a new command
     */
    const val ADD_COMMAND: Long = 1 shl 0

    /**
     * Rename, or hide commands
     */
    const val MODIFY_COMMAND: Long = 1 shl 1

    /**
     * Make commands available globally
     */
    const val PUBLISH_COMMAND: Long = 1 shl 2

    /**
     * Delete commands
     */
    const val DELETE_COMMAND: Long = 1 shl 3

    // COMMAND ENTRY GROUP

    /**
     * Add command entries
     */
    const val ADD_COMMAND_ENTRY: Long = 1 shl 8

    /**
     * Remove command entries
     */
    const val REMOVE_COMMAND_ENTRY: Long = 1 shl 9

    /**
     * Remove command entry from all guilds
     */
    const val PRUNE_COMMAND_ENTRY: Long = 1 shl 10

    // MODERATION GROUP

    const val MANAGE_PERMISSIONS: Long = 1 shl 32

    // COMBINED PERMISSIONS

    const val MANAGE_COMMANDS: Long = ADD_COMMAND or MODIFY_COMMAND or PUBLISH_COMMAND or DELETE_COMMAND

    const val MANAGE_ENTRIES: Long = ADD_COMMAND_ENTRY or REMOVE_COMMAND_ENTRY

    const val ALL: Long = MANAGE_COMMANDS or MANAGE_ENTRIES or PRUNE_COMMAND_ENTRY or MANAGE_PERMISSIONS
}