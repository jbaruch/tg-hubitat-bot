package jbaru.ch.telegram.hubitat

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

/**
 * Runs [block], funneling the expected hub-I/O failure types into [onFailure]:
 * network I/O (ktor timeouts are IOExceptions), non-2xx responses and other
 * state guards (IllegalStateException), and malformed JSON
 * (SerializationException / IllegalArgumentException). Anything unexpected -
 * including CancellationException - propagates untouched, so per-item
 * resilience never hides a real bug or breaks structured concurrency.
 *
 * Deliberately NOT inline: inlining would copy the four catch branches into
 * every call site's bytecode, where the branches a site cannot hit count as
 * uncovered instructions against that class's jacoco gate.
 */
internal fun <T> onExpectedFailure(onFailure: (Exception) -> T, block: () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        // CancellationException SUBCLASSES IllegalStateException - without
        // this clause the next catch would swallow coroutine cancellation.
        throw e
    } catch (e: IOException) {
        onFailure(e)
    } catch (e: IllegalStateException) {
        onFailure(e)
    } catch (e: SerializationException) {
        onFailure(e)
    } catch (e: IllegalArgumentException) {
        onFailure(e)
    }

/** Suspend variant of [onExpectedFailure] with the same contract. */
internal suspend fun <T> onExpectedFailureSuspend(
    onFailure: suspend (Exception) -> T,
    block: suspend () -> T
): T =
    try {
        block()
    } catch (e: CancellationException) {
        // See the non-suspend variant: CancellationException subclasses
        // IllegalStateException and must never be funneled into onFailure.
        throw e
    } catch (e: IOException) {
        onFailure(e)
    } catch (e: IllegalStateException) {
        onFailure(e)
    } catch (e: SerializationException) {
        onFailure(e)
    } catch (e: IllegalArgumentException) {
        onFailure(e)
    }
