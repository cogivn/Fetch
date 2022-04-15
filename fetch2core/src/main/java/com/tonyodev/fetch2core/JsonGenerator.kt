package com.tonyodev.fetch2core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.lang.reflect.Modifier
import java.lang.reflect.Type
import kotlin.coroutines.CoroutineContext

object JsonGenerator {
    private val mGSon: Gson = GsonBuilder()
        .setLenient()
        .addSerializationExclusionStrategy(ExclusionStrategy())
        .excludeFieldsWithModifiers(Modifier.TRANSIENT, Modifier.STATIC, Modifier.VOLATILE)
        .create()

    fun convertJson(data: Any): String = catching { mGSon.toJson(data) ?: "{}" } ?: "{}"

    suspend fun convertJsonElement(data: Any?): JsonElement? {
        val nonNullData = data ?: return null
        val json = convertJson(nonNullData)
        return parse(json, JsonElement::class.java)
    }

    private fun mayBeJSON(json: String?): Boolean = json?.let {
        ("null" == json || json.startsWith("[")
                && json.endsWith("]")
                || json.startsWith("{")
                && json.endsWith("}"))
    } ?: false

    @Deprecated(
        "This function run on Main thread.\n" +
                "Please using parse(String, Type) instead.", ReplaceWith(
            "JsonGenerator.parse(json, type)",
            "com.legato.common.extensions.parse"
        )
    )
    fun <T> parserJson(json: String?, type: Type): T? =
        if (mayBeJSON(json)) catching { json?.let { mGSon.fromJson<T>(json, type) } }
        else null

    @Deprecated(
        "This function run on Main thread.\n" +
                "Please using parse(String, Type) instead.", ReplaceWith(
            "JsonGenerator.parse(json, type)",
            "com.legato.common.extensions.parse"
        )
    )
    fun <T> parserJson(json: JsonElement?, type: Type?): T? =
        catching { json?.let { mGSon.fromJson<T>(json, type) } }


    @Deprecated(
        "This function run on Main thread.\n" +
                "Please using parse(String) instead.", ReplaceWith(
            "JsonGenerator.parse<T>(json)",
            "com.legato.common.extensions.parse"
        )
    )
    inline fun <reified T> parser(json: String?): T? = parserJson<T>(json, T::class.java)

    /**
     * Parsing JSON with CPU-intensive work outside of the main thread.
     **/
    suspend fun <T> parse(
        json: JsonElement?, type: Type?
    ): T? = withContext(Dispatchers.Default) {
        catching { json?.let { mGSon.fromJson<T>(it, type) } }
    }

    suspend fun <T> parse(
        json: String?, type: Type
    ): T? = withContext(Dispatchers.Default) {
        if (mayBeJSON(json)) catching {
            json?.let { mGSon.fromJson<T>(json, type) }
        }
        else null
    }

    suspend inline fun <reified T> parse(json: String?): T? {
        val type = object : TypeToken<T>() {}.type
        return parse(json, type)
    }

    suspend inline fun <reified T> parse(json: JsonElement?): T? {
        val type = object : TypeToken<T>() {}.type
        return parse(json, type)
    }

    inline fun <reified T> parse(
        json: String?,
        context: CoroutineContext
    ): T? = runBlocking(context) { parse(json) }
}