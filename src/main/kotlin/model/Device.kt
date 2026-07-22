@file:Suppress("unused")

package jbaru.ch.telegram.hubitat.model
import kotlinx.serialization.*

// Shared constants behind the getter-only overrides: the getters keep these
// type-level tables out of serialization, the constants keep every access from
// allocating a fresh map.
private val ACTUATOR_OPS = mapOf("on" to 0, "off" to 0)
private val DIMMER_OPS = mapOf("on" to 0, "off" to 0, "setLevel" to 1)
private val BUTTON_OPS = mapOf("doubleTap" to 1, "hold" to 1, "push" to 1, "release" to 1)
private val SHADE_OPS = mapOf("open" to 0, "close" to 0)
private val HUB_OPS = mapOf("reboot" to 0)
private val CONTACT_ATTRIBUTES = mapOf("contact" to listOf("open", "closed"))
private val NO_OPS = emptyMap<String, Int>()
private val NO_ATTRIBUTES = emptyMap<String, List<String>>()

@Serializable
sealed class Device {
    abstract val id: Int
    abstract val label: String
    abstract val supportedOps: Map<String, Int>
    abstract val attributes: Map<String, List<String>>

    @Serializable
    sealed class Actuator() : Device() {
        override val supportedOps: Map<String, Int> get() = ACTUATOR_OPS
        override val attributes: Map<String, List<String>> get() = NO_ATTRIBUTES

    }

    @Serializable
    sealed class Dimmer() : Actuator() {
        override val supportedOps: Map<String, Int> get() = DIMMER_OPS
    }

    @Serializable
    sealed class Button() : Device() {
        override val supportedOps: Map<String, Int> get() = BUTTON_OPS
        override val attributes: Map<String, List<String>> get() = NO_ATTRIBUTES
    }

    @Serializable
    sealed class Shade() : Device() {
        override val supportedOps: Map<String, Int> get() = SHADE_OPS
        override val attributes: Map<String, List<String>> get() = NO_ATTRIBUTES
    }

    @Serializable
    sealed class Sensor() : Device() {
        override val supportedOps: Map<String, Int> get() = NO_OPS
    }

    @Serializable
    sealed class ContactSensor() : Sensor() {
        override val attributes: Map<String, List<String>> get() = CONTACT_ATTRIBUTES
    }

    @Serializable
    @SerialName("Generic Zigbee Contact Sensor")
    data class GenericZigbeeContactSensor(override val id: Int, override val label: String) : ContactSensor()

    @Serializable
    @SerialName("SmartThings Multipurpose Sensor V5")
    data class SmartThingsMultipurposeSensorV5(override val id: Int, override val label: String) : ContactSensor()

    @Serializable
    @SerialName("Hub Information Driver v3")
    data class Hub(
        override val id: Int,
        override val label: String,
        var managementToken: String = "",
        var ip: String = ""
    ) : Device() {
        override val supportedOps: Map<String, Int> get() = HUB_OPS
        override val attributes: Map<String, List<String>> get() = NO_ATTRIBUTES
    }

    @Serializable
    @SerialName("Virtual Switch")
    data class VirtualSwitch(override val id: Int, override val label: String) : Actuator()

    @Serializable
    @SerialName("Virtual Button")
    data class VirtualButton(override val id: Int, override val label: String) : Button()

    @Serializable
    @SerialName("Room Lights Activator Switch")
    data class RoomLightsActivatorSwitch(override val id: Int, override val label: String) : Actuator()

    @Serializable
    @SerialName("Room Lights Activator Dimmer")
    data class RoomLightsActivatorDimmer(override val id: Int, override val label: String) : Dimmer()

    @Serializable
    @SerialName("Room Lights Activator Bulb")
    data class RoomLightsActivatorBulb(override val id: Int, override val label: String) : Dimmer()

    @Serializable
    @SerialName("Generic Zigbee Outlet")
    data class GenericZigbeeOutlet(override val id: Int, override val label: String) : Actuator()

    @Serializable
    @SerialName("Zooz Zen27 Central Scene Dimmer")
    data class ZoozDimmer(override val id: Int, override val label: String) : Dimmer()

    @Serializable
    @SerialName("Zooz ZEN Dimmer Advanced")
    data class ZoozZenDimmerAdvanced(override val id: Int, override val label: String) : Dimmer()

    @Serializable
    @SerialName("Zooz Zen76 S2 Switch")
    data class ZoozSwitch(override val id: Int, override val label: String) : Actuator()

    @Serializable
    @SerialName("Zooz ZEN Switch Advanced")
    data class ZoozZenSwitchAdvanced(override val id: Int, override val label: String) : Actuator()

    @Serializable
    @SerialName("Zooz ZEN Plugs Advanced")
    data class ZoozZenPlugsAdvanced(override val id: Int, override val label: String) : Actuator()

    @Serializable
    @SerialName("Room Lights Activator Shade")
    data class RoomLightsActivatorShade(override val id: Int, override val label: String) : Shade()
}
