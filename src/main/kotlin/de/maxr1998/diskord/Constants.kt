package de.maxr1998.diskord

object Constants {
    const val CONFIG_FILE_NAME = "config.json"
    const val COMMAND_PREFIX = "%"
    const val DEFAULT_ACK_EMOJI = "\uD83D\uDC4D"
}

object Command {
    const val PROMOTE_ADMIN = "promoteadmin"
    const val PROMOTE_ADMIN_SHORT = "pa"
    const val PROMOTE = "promote"
    const val PROMOTE_SHORT = "p"

    const val AUTO_RESPONDER = "autoresponder"
    const val AUTO_RESPONDER_SHORT = "ar"
    val AUTO_RESPONDER_MODE_ADD = arrayOf("add", "create", "new")
    val AUTO_RESPONDER_MODE_LIST = arrayOf("list", "ls")
    val AUTO_RESPONDER_MODE_REMOVE = arrayOf("remove", "rm", "delete")
    const val ADD = "add"

    const val STATUS = "status"
    const val STATUS_MODE_CLEAR = "clear"

    const val HELP = "help"
}