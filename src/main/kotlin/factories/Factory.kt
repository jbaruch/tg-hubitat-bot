package jbaru.ch.telegram.hubitat.commands

interface Factory<I, O> {
    fun create(param: I): O
}
