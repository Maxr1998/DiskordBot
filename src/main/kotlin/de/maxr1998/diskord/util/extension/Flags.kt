package de.maxr1998.diskord.util.extension

fun Long.hasFlag(flag: Long): Boolean = this and flag == flag