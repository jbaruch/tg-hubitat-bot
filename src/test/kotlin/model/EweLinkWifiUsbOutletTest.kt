package jbaru.ch.telegram.hubitat.model

import jbaru.ch.telegram.hubitat.EweLinkManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class EweLinkWifiUsbOutletTest {
 private lateinit var eweLinkManager: EweLinkManager
 private lateinit var outlet: EweLinkWifiUsbOutlet

 @BeforeEach
 fun setup() {
  eweLinkManager = mock()
  outlet = EweLinkWifiUsbOutlet(
   name = "Test Device",
   eweLinkManager = eweLinkManager)
 }

 @Test
 fun `test power off`() {
  outlet.powerOff()

  verify(eweLinkManager).apply(
   eq("Test Device"),
   argThat { outletSwitch ->
    outletSwitch.outlet == 0 &&
            outletSwitch._switch == "off"
   }
  )
 }

 @Test
 fun `test power on`() {
  outlet.powerOn()

  verify(eweLinkManager).apply(
   eq("Test Device"),
   argThat { outletSwitch ->
    outletSwitch.outlet == 0 &&
            outletSwitch._switch == "on"
   }
  )
 }
}