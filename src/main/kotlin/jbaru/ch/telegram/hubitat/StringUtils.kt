package jbaru.ch.telegram.hubitat

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun String.snakeToCamelCase(): String {
    return split("_").mapIndexed { index, s ->
        if (index == 0) s else s.replaceFirstChar(Char::titlecase)
    }.joinToString("")
}

fun String.camelToSnakeCase(): String {
    return replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()
}

/** Path-segment encoding: form-style "+" is wrong for URL paths. */
fun encodePathSegment(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
