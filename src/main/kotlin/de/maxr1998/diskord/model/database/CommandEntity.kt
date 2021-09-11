package de.maxr1998.diskord.model.database

import org.jetbrains.exposed.dao.id.EntityID

data class CommandEntity(
    val id: EntityID<Int>,
    val server: String,
    val command: String,
)