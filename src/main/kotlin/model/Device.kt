@file:Suppress("unused")

package jbaru.ch.telegram.hubitat.model
import jbaru.ch.telegram.hubitat.model.Constants.Companion.CLOSE
import jbaru.ch.telegram.hubitat.model.Constants.Companion.OFF
import jbaru.ch.telegram.hubitat.model.Constants.Companion.ON
import jbaru.ch.telegram.hubitat.model.Constants.Companion.OPEN
import jbaru.ch.telegram.hubitat.model.Constants.Companion.REBOOT
import kotlinx.serialization.*

@Serializable
sealed class Device {
    abstract val id: Int
    abstract val label: String
    abstract val supportedOps:List<String>

    @Serializable
    sealed class Actuator() : Device() {
        override val supportedOps: List<String> = listOf(ON, OFF)
    }

    @Serializable
    sealed class Shade() : Device() {
        override val supportedOps: List<String> = listOf(OPEN, CLOSE)
    }

    @Serializable
    @SerialName("Hub Information Driver v3")
    data class Hub(override val id:Int, override val label: String, var managementToken:String = "", var ip:String = "") : Device() {
        override val supportedOps: List<String> = listOf(REBOOT)
    }

    @Serializable
    @SerialName("Virtual Switch")
    data class VirtualSwitch(override val id: Int, override val label: String) : Actuator()

    @Serializable
    @SerialName("Room Lights Activator Switch")
    data class RoomLightsActivatorSwitch(override val id: Int, override val label: String) : Actuator()

    @Serializable
    @SerialName("Room Lights Activator Dimmer")
    data class RoomLightsActivatorDimmer(override val id: Int, override val label: String) : Actuator()

    @Serializable
    @SerialName("Room Lights Activator Bulb")
    data class RoomLightsActivatorBulb(override val id: Int, override val label: String) : Actuator()

    @Serializable
    @SerialName("Generic Zigbee Outlet")
    data class GenericZigbeeOutlet(override val id: Int, override val label: String) : Actuator()

    @Serializable
    @SerialName("Zooz Zen27 Central Scene Dimmer")
    data class ZoozDimmer(override val id: Int, override val label: String) : Actuator()

    @Serializable
    @SerialName("Zooz Zen76 S2 Switch")
    data class ZoozSwitch(override val id: Int, override val label: String) : Actuator()

    @Serializable
    @SerialName("Room Lights Activator Shade")
    data class RoomLightsActivatorShade(override val id: Int, override val label: String) : Shade()
}
