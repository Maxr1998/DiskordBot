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
    const val AUTO_RESPONDER_MODE_ADD = "add"
    const val AUTO_RESPONDER_MODE_LIST = "list"
    val AUTO_RESPONDER_MODE_REMOVE = arrayOf("remove", "rm")

    const val ADD = "add"
    const val HELP = "help"
}