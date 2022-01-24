package de.maxr1998.diskord

object Command {
    // Bot management
    const val STATUS = "status"

    // User management
    const val PROMOTE_ADMIN = "promoteadmin"
    const val PROMOTE_ADMIN_SHORT = "pa"
    const val PROMOTE = "promote"
    const val PROMOTE_SHORT = "p"

    // AR management
    const val AUTO_RESPONDER = "autoresponder"
    const val AUTO_RESPONDER_SHORT = "ar"
    val AUTO_RESPONDER_MODE_ADD = arrayOf("add", "create", "new")
    val AUTO_RESPONDER_MODE_RENAME = arrayOf("rename", "mv")
    val AUTO_RESPONDER_MODE_LIST = arrayOf("list", "ls")
    const val AUTO_RESPONDER_TYPE_GLOBAL = "global"
    const val AUTO_RESPONDER_TYPE_HIDDEN = "hidden"
    const val AUTO_RESPONDER_MODE_PUBLISH = "publish"
    const val AUTO_RESPONDER_MODE_HIDE = "hide"
    val AUTO_RESPONDER_MODE_REMOVE = arrayOf("remove", "rm", "delete")

    // AR content management
    const val ADD = "add"
    const val REMOVE = "remove"
    const val REMOVE_SHORT = "rm"
    const val SOURCE = "source"

    // Helper commands
    const val RESOLVE = "resolve"

    // Help
    const val HELP = "help"
    const val HELP_ADMIN = "admin"
}