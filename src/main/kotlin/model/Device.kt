package jbaru.ch.telegram.hubitat.model
import kotlinx.serialization.*

@Serializable
data class Device(val id: Int, val label: String)