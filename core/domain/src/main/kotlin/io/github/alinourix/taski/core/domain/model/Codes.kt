package io.github.alinourix.taski.core.domain.model

/** An enum stored by a stable text code rather than its ordinal, so reordering the enum never corrupts data. */
interface Coded {
    val code: String
}

inline fun <reified E> codeOf(code: String?): E? where E : Enum<E>, E : Coded =
    enumValues<E>().firstOrNull { it.code == code }
