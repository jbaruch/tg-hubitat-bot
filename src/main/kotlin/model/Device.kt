@file:Suppress("unused")

package jbaru.ch.telegram.hubitat.model
import kotlinx.serialization.*

@Serializable
sealed class Device {
    abstract val id: Int
    abstract val label: String
    abstract val supportedOps:List<String>


    @Serializable
    sealed class Actuator() : Device() {
        override val supportedOps: List<String> = listOf("on", "off")
    }

    @Serializable
    sealed class Button() : Device() {
        override val supportedOps: List<String> = listOf("doubleTap", "hold", "push", "release")
    }

    @Serializable
    sealed class Shade() : Device() {
        override val supportedOps: List<String> = listOf("open", "close")
    }

    @Serializable
    @SerialName("Hub Information Driver v3")
    data class Hub(override val id:Int, override val label: String, var managementToken:String = "", var ip:String = "") : Device() {
        override val supportedOps: List<String> = listOf("reboot")
    }

    @Serializable
    @SerialName("Virtual Switch")
    data class VirtualSwitch (override val id: Int, override val label: String) : Actuator()

    @Serializable
    @SerialName("Virtual Button")
    data class VirtualButton (override val id: Int, override val label: String) : Button()

    @Serializable
    @SerialName("Room Lights Activator Switch")
    data class RoomLightsActivatorSwitch (override val id: Int, override val label: String) : Actuator()

    @Serializable
    @SerialName("Room Lights Activator Dimmer")
    data class RoomLightsActivatorDimmer (override val id: Int, override val label: String) : Actuator()

    @Serializable
    @SerialName("Room Lights Activator Bulb")
    data class RoomLightsActivatorBulb (override val id: Int, override val label: String) : Actuator()

    @Serializable
    @SerialName("Generic Zigbee Outlet")
    data class GenericZigbeeOutlet (override val id: Int, override val label: String) : Actuator()

    @Serializable
    @SerialName("Zooz Zen27 Central Scene Dimmer")
    data class ZoozDimmer (override val id: Int, override val label: String) : Actuator()

    @Serializable
    @SerialName("Zooz Zen76 S2 Switch")
    data class ZoozSwitch (override val id: Int, override val label: String) : Actuator()

    @Serializable
    @SerialName("Room Lights Activator Shade")
    data class RoomLightsActivatorShade(override val id: Int, override val label: String) : Shade()
}
