package jbaru.ch.telegram.hubitat.model
import kotlinx.serialization.*

@Serializable
sealed class Device {
    abstract val id: Int
    abstract val label: String
    abstract val SUPPORTED_OPS:List<String>


    @Serializable
    sealed class Actuator() : Device() {
        override val SUPPORTED_OPS: List<String> = listOf("on", "off")
    }

    @Serializable
    @SerialName("Virtual Switch")
    data class VirtualSwitch (override val id: Int, override val label: String) : Actuator() {}

    @Serializable
    @SerialName("Room Lights Activator Switch")
    data class RoomLightsActivatorSwitch (override val id: Int, override val label: String) : Actuator() {}

    @Serializable
    @SerialName("Room Lights Activator Dimmer")
    data class RoomLightsActivatorDimmer (override val id: Int, override val label: String) : Actuator() {}

    @Serializable
    @SerialName("Room Lights Activator Bulb")
    data class RoomLightsActivatorBulb (override val id: Int, override val label: String) : Actuator() {}

    @Serializable
    @SerialName("Generic Zigbee Outlet")
    data class GenericZigbeeOutlet (override val id: Int, override val label: String) : Actuator() {}

    @Serializable
    @SerialName("Zooz Zen27 Central Scene Dimmer")
    data class ZoozDimmer (override val id: Int, override val label: String) : Actuator() {}

    @Serializable
    @SerialName("Zooz Zen76 S2 Switch")
    data class ZoozSwitch (override val id: Int, override val label: String) : Actuator() {}

    @Serializable
    sealed class Shade() : Device() {
        override val SUPPORTED_OPS: List<String> = listOf("open", "close")
    }

    @Serializable
    @SerialName("Room Lights Activator Shade")
    data class RoomLightsActivatorShade(override val id: Int, override val label: String) : Shade() {}
}
