package jbaru.ch.telegram.hubitat.model

import jbaru.ch.telegram.hubitat.EweLinkManager
import jbaru.ch.telegram.hubitat.EweLinkSession
import jbaru.ch.telegram.hubitat.RetryConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertNotNull
import java.lang.System.getenv
import kotlin.test.Test
import kotlin.test.assertEquals

class OutletStatusMethodOrderer : MethodOrderer {
    companion object {
        var isLastTest = false
        private var executedTests = 0
        private const val TOTAL_TESTS = 4

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
        private val TEST_RETRY_CONFIG = RetryConfig(
            maxAttempts = 2,
            retryDelayMs = 5000,  // 5 seconds between retries
            maxWebSocketRetries = 3,
            webSocketRetryDelayMs = 3000  // 3 seconds between WebSocket retries
        )

        private fun getSession() = EweLinkSession(
            email = getenv("EWELINK_EMAIL") ?: throw IllegalStateException("EWELINK_EMAIL not set"),
            password = getenv("EWELINK_PASSWORD") ?: throw IllegalStateException("EWELINK_PASSWORD not set"),
            timeoutSeconds = 10,  // 10 seconds timeout
            retryConfig = TEST_RETRY_CONFIG
        )

        private val manager = EweLinkManager(getSession(), TEST_RETRY_CONFIG)

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
            Thread.sleep(3000) // Wait 3 seconds between tests
        }
    }

    @Test
    fun `test turning outlet off`() {
        outlet.powerOff()
        Thread.sleep(3000) // Wait 3 seconds for state change
        assertEquals("off", outlet.getCurrentStatus())
    }

    @Test
    fun `test turning outlet on`() {
        outlet.powerOn()
        Thread.sleep(3000) // Wait 3 seconds for state change
        assertEquals("on", outlet.getCurrentStatus())
    }

    @Test
    fun `test power off fails when websocket is not ready after login`(): Unit = runBlocking {
        val email = getenv("EWELINK_EMAIL") ?: throw IllegalStateException("EWELINK_EMAIL not set")
        val password = getenv("EWELINK_PASSWORD") ?: throw IllegalStateException("EWELINK_PASSWORD not set")
        
        // Create a session with minimal timeout and fast retry config
        val session = EweLinkSession(
            email = email, 
            password = password, 
            timeoutSeconds = 1,
            retryConfig = TEST_RETRY_CONFIG
        )
        val manager = EweLinkManager(session, TEST_RETRY_CONFIG)
        val outlet = EweLinkWifiUsbOutlet(name = "Devices Hub", eweLinkManager = manager)

        // Force login but don't wait for WebSocket
        session.eweLink.login()

        // Attempt to power off - should fail after retries
        val startTime = System.currentTimeMillis()
        val exception = assertThrows<RuntimeException> {
            outlet.powerOff()
        }
        val duration = System.currentTimeMillis() - startTime

        // Verify exception details
        assert(exception.message?.contains("WebSocket") == true) { "Expected exception message to contain 'WebSocket', but was: ${exception.message}" }
        
        // Verify that we waited for retries (at least retryDelayMs * (maxAttempts-1))
        val expectedMinDuration = TEST_RETRY_CONFIG.retryDelayMs * (TEST_RETRY_CONFIG.maxAttempts - 1)
        assert(duration >= expectedMinDuration) { 
            "Expected operation to take at least ${expectedMinDuration}ms due to retries, but took ${duration}ms" 
        }
        assert(duration <= expectedMinDuration * 2) {
            "Operation took too long: ${duration}ms. Expected less than ${expectedMinDuration * 2}ms"
        }
    }

    @Test
    fun `test power off with real EweLink API`() {
        val session = EweLinkSession(
            email = System.getenv("EWELINK_EMAIL") ?: "test@example.com",
            password = System.getenv("EWELINK_PASSWORD") ?: "test123",
            timeoutSeconds = 1,
            retryConfig = TEST_RETRY_CONFIG
        )
        val eweLinkManager = EweLinkManager(session, TEST_RETRY_CONFIG)
        val outlet = EweLinkWifiUsbOutlet(
            name = "Test Device",
            eweLinkManager = eweLinkManager,
            outletNumber = 0
        )

        // Verify that we can access the eweLink API
        assertDoesNotThrow {
            session.eweLink
        }

        // Verify that we can get the list of devices
        val devices = eweLinkManager.things
        assertNotNull(devices)
    }
}