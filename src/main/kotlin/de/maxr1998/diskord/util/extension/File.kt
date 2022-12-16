package de.maxr1998.diskord.util.extension

import java.io.File

fun File.subdirectories(): Array<out File> = listFiles { file -> file.isDirectory }.orEmpty()