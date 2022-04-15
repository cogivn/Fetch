package com.tonyodev.fetch2core

fun Any.asMap(): MutableMap<String, String> {
    return toJson().let {
        JsonGenerator.parser<MutableMap<String, String>>(it)
    } ?: mutableMapOf()
}

inline fun <T, R> T.catching(showLog: Boolean = true, block: T.() -> R): R? {
    return try {
        block()
    } catch (e: Throwable) {
        if (showLog) e.printStackTrace()
        null
    }
}

fun Any?.toJson(): String = JsonGenerator.convertJson(this ?: "{}")

fun <T> T.isNot(vararg values: T): Boolean = !values.contains(this)
fun <T> T.isOneOf(vararg values: T): Boolean = values.contains(this)
