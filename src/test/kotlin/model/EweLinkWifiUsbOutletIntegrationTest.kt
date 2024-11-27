package jbaru.ch.telegram.hubitat.model

import jbaru.ch.telegram.hubitat.EweLinkManager
import jbaru.ch.telegram.hubitat.EweLinkSession
import org.junit.jupiter.api.*
import java.lang.System.getenv
import kotlin.test.Test
import kotlin.test.assertEquals

class OutletStatusMethodOrderer : MethodOrderer {
    companion object {
        var isLastTest = false
        private var executedTests = 0
        private const val TOTAL_TESTS = 2

        fun markTestExecuted() {
            executedTests++
            isLastTest = executedTests >= TOTAL_TESTS
        }
    }

    override fun orderMethods(context: MethodOrdererContext) {
        val outlet = EweLinkWifiUsbOutletIntegrationTest.getTestOutlet()
        val currentStatus = outlet.getCurrentStatus()

        context.methodDescriptors.sortWith { m1, m2 ->
            when {
                currentStatus == "on" && m1.method.name.contains("off") -> -1
                currentStatus == "off" && m1.method.name.contains("on") -> -1
                else -> 1
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
}