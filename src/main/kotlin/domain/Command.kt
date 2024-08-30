package jbaru.ch.telegram.hubitat.domain

fun interface Command<I, O> {
    suspend operator fun invoke(param: I): O
}
