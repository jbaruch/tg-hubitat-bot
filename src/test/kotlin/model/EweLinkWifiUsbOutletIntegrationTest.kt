package jbaru.ch.telegram.hubitat.model

import jbaru.ch.telegram.hubitat.EweLinkManager
import jbaru.ch.telegram.hubitat.EweLinkSession
import org.junit.jupiter.api.BeforeEach
import java.lang.System.getenv
import kotlin.test.Test
import kotlin.test.assertEquals

class EweLinkWifiUsbOutletIntegrationTest {

    private lateinit var session: EweLinkSession
    private lateinit var manager: EweLinkManager

    @BeforeEach
    fun setup() {
        session = EweLinkSession(
            email = getenv("EWELINK_EMAIL") ?: throw IllegalStateException("EWELINK_EMAIL not set"),
            password = getenv("EWELINK_PASSWORD") ?: throw IllegalStateException("EWELINK_PASSWORD not set"),
        )
        manager = EweLinkManager(session)
    }

    @Test
    fun `test real device outlet toggle`() {
//        val testDevice = manager.findByName("Test")
//            ?: throw IllegalStateException("Test device not found")
//
//        val outlet = EweLinkWifiUsbOutlet(
//            name = testDevice.itemData.name,
//            id = testDevice.itemData.deviceid,
//            eweLinkManager = manager
//        )
//
//        // Get initial state
//        val initialState = outlet.status(0) // refresh = true by default
//        println("Initial state: $initialState")
//
//        // Toggle to opposite state
//        when (initialState) {
//            SwitchState.ON -> {
//                outlet.off(0)
//                Thread.sleep(3000)
//                assertEquals(SwitchState.OFF, outlet.status(0))
//            }
//            SwitchState.OFF -> {
//                outlet.on(0)
//                Thread.sleep(3000)
//                assertEquals(SwitchState.ON, outlet.status(0))
//            }
//            SwitchState.UNKNOWN -> {
//                throw IllegalStateException("Device returned unknown state")
//            }
//        }
//
//        // Return to initial state
//        when (initialState) {
//            SwitchState.ON -> {
//                outlet.on(0)
//                Thread.sleep(3000)
//                assertEquals(SwitchState.ON, outlet.status(0))
//            }
//            SwitchState.OFF -> {
//                outlet.off(0)
//                Thread.sleep(3000)
//                assertEquals(SwitchState.OFF, outlet.status(0))
//            }
//            SwitchState.UNKNOWN -> {}
//        }
    }
}