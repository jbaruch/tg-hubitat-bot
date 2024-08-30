package jbaru.ch.telegram.hubitat.mappers

fun interface Mapper<I, R> {
    fun map(input: I): R
}
