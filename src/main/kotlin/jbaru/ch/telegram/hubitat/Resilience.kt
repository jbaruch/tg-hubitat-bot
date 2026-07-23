package jbaru.ch.telegram.hubitat

import java.io.IOException
import kotlinx.serialization.SerializationException

/**
 * Runs [block], funneling the expected hub-I/O failure types into [onFailure]:
 * network I/O (ktor timeouts are IOExceptions), non-2xx responses and other
 * state guards (IllegalStateException), and malformed JSON
 * (SerializationException / IllegalArgumentException). Anything unexpected -
 * including CancellationException - propagates untouched, so per-item
 * resilience never hides a real bug or breaks structured concurrency.
 */
internal inline fun <T> onExpectedFailure(onFailure: (Exception) -> T, block: () -> T): T =
    try {
        block()
    } catch (e: IOException) {
        onFailure(e)
    } catch (e: IllegalStateException) {
        onFailure(e)
    } catch (e: SerializationException) {
        onFailure(e)
    } catch (e: IllegalArgumentException) {
        onFailure(e)
    }
