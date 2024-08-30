package jbaru.ch.telegram.hubitat.mappers

fun interface CamelCaseAbbreviationMapper : Mapper<String, String>

class CamelCaseAbbreviationMapperImpl : CamelCaseAbbreviationMapper {
    override fun map(input: String): String =
        input.split(" ")
            .filter { it.isNotEmpty() }
            .joinToString("") {
                it.first().lowercaseChar().toString()
            }
}
