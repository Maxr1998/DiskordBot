package de.maxr1998.diskord.util.extension

import org.jetbrains.exposed.sql.AndBitOp
import org.jetbrains.exposed.sql.EqOp
import org.jetbrains.exposed.sql.ExpressionWithColumnType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.wrap

infix fun <T> ExpressionWithColumnType<T>.hasNotFlag(t: T): EqOp =
    EqOp(AndBitOp(this, wrap(t), columnType), wrap(0))