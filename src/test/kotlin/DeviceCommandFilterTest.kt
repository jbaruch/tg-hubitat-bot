package jbaru.ch.telegram.hubitat

import com.github.kotlintelegrambot.entities.Message
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class DeviceCommandFilterTest : FunSpec({

    lateinit var deviceManager: DeviceManager
    lateinit var filter: DeviceCommandFilter

    beforeEach {
        deviceManager = mock {
            on { isDeviceCommand("on") } doReturn true
            on { isDeviceCommand("off") } doReturn true
            on { isDeviceCommand("setLevel") } doReturn true
            on { isDeviceCommand("cancelAlerts") } doReturn false
            on { isDeviceCommand("unknown") } doReturn false
        }
        filter = DeviceCommandFilter { deviceManager }
    }

    fun messageWithText(value: String?): Message = mock {
        on { text } doReturn value
    }

    fun matches(text: String?): Boolean = with(filter) {
        messageWithText(text).predicate()
    }

    test("matches a device command with a leading slash") {
        matches("/on kitchen") shouldBe true
    }

    test("converts snake_case commands before lookup") {
        matches("/set_level kitchen 50") shouldBe true
    }

    test("does not match without a leading slash") {
        matches("on kitchen") shouldBe false
    }

    test("does not match system commands") {
        matches("/cancel_alerts") shouldBe false
    }

    test("does not match unknown commands") {
        matches("/unknown kitchen") shouldBe false
    }

    test("does not match null text") {
        matches(null) shouldBe false
    }

    test("does not match empty text") {
        matches("") shouldBe false
    }

    test("does not match a bare slash") {
        matches("/") shouldBe false
    }

    test("matches the group-chat mention form") {
        matches("/on@MyHomeBot kitchen") shouldBe true
    }

    test("does not match a mention of an unknown command") {
        matches("/unknown@MyHomeBot kitchen") shouldBe false
    }
})
