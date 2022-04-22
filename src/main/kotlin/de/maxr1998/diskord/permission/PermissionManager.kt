package de.maxr1998.diskord.permission

import de.maxr1998.diskord.model.Permissions
import de.maxr1998.diskord.util.LruMap
import de.maxr1998.diskord.util.extension.hasFlag
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

class PermissionManager {

    private val permissionCache: MutableMap<GuildUser, Long> = LruMap(MAX_CACHE_SIZE)

    /**
     * Must only be used in IO thread
     */
    fun hasPermission(user: GuildUser, permission: Long): Boolean {
        val permissions = permissionCache.computeIfAbsent(user) {
            Permissions.slice(Permissions.permissions).select {
                (Permissions.guild eq user.guildId) and (Permissions.user eq user.userId)
            }.singleOrNull()?.get(Permissions.permissions) ?: 0L
        }

        return permissions.hasFlag(permission)
    }


    companion object {
        private const val MAX_CACHE_SIZE = 100
    }
}