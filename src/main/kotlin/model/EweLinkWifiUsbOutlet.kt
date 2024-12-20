package jbaru.ch.telegram.hubitat.jbaru.ch.telegram.hubitat.model

import com.github.realzimboguy.ewelink.api.model.home.OutletSwitch
import com.github.realzimboguy.ewelink.api.model.home.Thing
import jbaru.ch.telegram.hubitat.EweLinkManager

class EweLinkWifiUsbOutlet(
    val name: String,
    private val eweLinkManager: EweLinkManager,
    private val outletNumber: Int = 0
) : PowerControl {

    override fun powerOff() {
        val outletSwitch = OutletSwitch().apply {
            outlet = outletNumber
            _switch = "off"
        }
        eweLinkManager.apply(name, outletSwitch)
    }

    override fun powerOn() {
        val outletSwitch = OutletSwitch().apply {
            outlet = outletNumber
            _switch = "on"
        }
        eweLinkManager.apply(name, outletSwitch)
    }

    fun getCurrentStatus(): String {
        eweLinkManager.refreshDevices()
        return eweLinkManager.findByName(name)
            ?.itemData
            ?.params
            ?.switches
            ?.firstOrNull { it.outlet == outletNumber }
            ?._switch
            ?: throw IllegalStateException("Could not get outlet status")
    }

    companion object {
        fun fromThing(thing: Thing, eweLinkManager: EweLinkManager, outletNumber: Int = 0): EweLinkWifiUsbOutlet {
            return EweLinkWifiUsbOutlet(
                name = thing.itemData.name,
                eweLinkManager = eweLinkManager,
                outletNumber = outletNumber
            )
        }
    }
}