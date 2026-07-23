package jbaru.ch.telegram.hubitat

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

class ResilienceTest : FunSpec({

    test("passes the block result through on success") {
        onExpectedFailure(onFailure = { "failed" }) { "ok" } shouldBe "ok"
    }

    test("funnels each expected failure type into onFailure") {
        val expected = listOf(
            IOException("io"),
            IllegalStateException("state"),
            SerializationException("json"),
            IllegalArgumentException("arg")
        )
        for (e in expected) {
            onExpectedFailure(onFailure = { it.message }) { throw e } shouldBe e.message
        }
    }

    test("lets CancellationException propagate despite subclassing IllegalStateException") {
        shouldThrow<CancellationException> {
            onExpectedFailure(onFailure = { "swallowed" }) { throw CancellationException("cancelled") }
        }
        shouldThrow<CancellationException> {
            onExpectedFailureSuspend(onFailure = { "swallowed" }) { throw CancellationException("cancelled") }
        }
    }

    test("lets unexpected exception types propagate") {
        shouldThrow<UnsupportedOperationException> {
            onExpectedFailure(onFailure = { "swallowed" }) { throw UnsupportedOperationException("boom") }
        }
    }

    test("suspend variant passes results and funnels expected failures") {
        onExpectedFailureSuspend(onFailure = { "failed" }) { "ok" } shouldBe "ok"
        val expected = listOf(
            IOException("io"),
            IllegalStateException("state"),
            SerializationException("json"),
            IllegalArgumentException("arg")
        )
        for (e in expected) {
            onExpectedFailureSuspend(onFailure = { it.message }) { throw e } shouldBe e.message
        }
    }

    test("suspend variant lets unexpected exception types propagate") {
        shouldThrow<UnsupportedOperationException> {
            onExpectedFailureSuspend(onFailure = { "swallowed" }) { throw UnsupportedOperationException("boom") }
        }
    }
})
