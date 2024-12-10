package jbaru.ch.telegram.hubitat.model

import jbaru.ch.telegram.hubitat.EweLinkManager
import jbaru.ch.telegram.hubitat.EweLinkSession
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import java.lang.System.getenv
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.assertThrows

class OutletStatusMethodOrderer : MethodOrderer {
    companion object {
        var isLastTest = false
        private var executedTests = 0
        private const val TOTAL_TESTS = 3

        fun markTestExecuted() {
            executedTests++
            isLastTest = executedTests >= TOTAL_TESTS
        }
    }

    override fun orderMethods(context: MethodOrdererContext) {
        // Error tests should run last to not interfere with power state sequence
        context.methodDescriptors.sortWith { m1, m2 ->
            when {
                m1.method.name.contains("fails") -> 1  // Error tests run last
                m2.method.name.contains("fails") -> -1 // Error tests run last
                else -> {
                    // Original ordering for power state tests
                    val outlet = EweLinkWifiUsbOutletIntegrationTest.getTestOutlet()
                    val currentStatus = outlet.getCurrentStatus()
                    when {
                        currentStatus == "on" && m1.method.name.contains("off") -> -1
                        currentStatus == "off" && m1.method.name.contains("on") -> -1
                        else -> 1
                    }
                }
            }
        }
    }
}

@TestMethodOrder(OutletStatusMethodOrderer::class)
class EweLinkWifiUsbOutletIntegrationTest {
    private lateinit var outlet: EweLinkWifiUsbOutlet

    companion object {
        private fun getSession() = EweLinkSession(
            email = getenv("EWELINK_EMAIL") ?: throw IllegalStateException("EWELINK_EMAIL not set"),
            password = getenv("EWELINK_PASSWORD") ?: throw IllegalStateException("EWELINK_PASSWORD not set")
        )

        private val manager = EweLinkManager(getSession())

        fun getTestOutlet(): EweLinkWifiUsbOutlet {
            val testDevice = manager.findByName("Test")
                ?: throw IllegalStateException("Test device not found")
            return EweLinkWifiUsbOutlet.fromThing(testDevice, manager)
        }
    }

    @BeforeEach
    fun setup() {
        outlet = getTestOutlet()
    }

    @AfterEach
    fun cleanup() {
        OutletStatusMethodOrderer.markTestExecuted()
        if (!OutletStatusMethodOrderer.isLastTest) {
            Thread.sleep(2000)
        }
    }

    @Test
    fun `test turning outlet off`() {
        outlet.powerOff()
        Thread.sleep(3000)
        assertEquals("off", outlet.getCurrentStatus())
    }

    @Test
    fun `test turning outlet on`() {
        outlet.powerOn()
        Thread.sleep(3000)
        assertEquals("on", outlet.getCurrentStatus())
    }

    @Test
    fun `test power off fails when websocket is not ready after login`() = runBlocking {
        val email = System.getenv("EWELINK_EMAIL") ?: throw IllegalStateException("EWELINK_EMAIL not set")
        val password = System.getenv("EWELINK_PASSWORD") ?: throw IllegalStateException("EWELINK_PASSWORD not set")
        
        // Create a session with minimal timeout to ensure WebSocket won't connect in time
        val session = EweLinkSession(email = email, password = password, timeoutSeconds = 1)
        val manager = EweLinkManager(session)
        val outlet = EweLinkWifiUsbOutlet(name = "Devices Hub", eweLinkManager = manager)

        // Force login but don't wait for WebSocket
        session.getEweLink().login()
        
        // Immediately try to power off - this should fail because WebSocket isn't ready
        assertThrows<RuntimeException> {
            outlet.powerOff()
        }
    }
}