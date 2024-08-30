package jbaru.ch.telegram.hubitat.factories

interface Factory<I, O> {
    fun create(param: I): O
}
