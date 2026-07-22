package jbaru.ch.telegram.hubitat

fun String.snakeToCamelCase(): String {
    return split("_").mapIndexed { index, s ->
        if (index == 0) s else s.replaceFirstChar(Char::titlecase)
    }.joinToString("")
}

fun String.camelToSnakeCase(): String {
    return replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()
}
