package jbaru.ch.telegram.hubitat.mappers

fun interface LightSuffixRemovalMapper : Mapper<String, String>

class LightSuffixRemovalMapperImpl : LightSuffixRemovalMapper {

    override fun map(input: String): String =
        input.replace(Regex(" lights?$", RegexOption.IGNORE_CASE), "")
            .trim()
}

