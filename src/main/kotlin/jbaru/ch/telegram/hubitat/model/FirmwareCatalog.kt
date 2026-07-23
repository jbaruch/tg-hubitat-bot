package jbaru.ch.telegram.hubitat.model

import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * Curated Z-Wave firmware catalog, bundled as a resource
 * (zwave-firmware-catalog.json) and refreshed via PRs, never scraped at
 * runtime. See issue #15 for the grounding of every version and URL.
 */
@Serializable
data class FirmwareCatalog(
    val catalogVersion: String,
    val entries: List<CatalogEntry>
)

@Serializable
data class CatalogEntry(
    val model: String,
    val vendor: String,
    val description: String = "",
    val match: MatchSpec,
    val battery: Boolean = false,
    val flirs: Boolean = false,
    val recommendation: String = "update",
    val warning: String? = null,
    val note: String? = null,
    val lines: List<FirmwareLine> = emptyList()
)

/**
 * How a hub device is recognized as this catalog model. A device matches when
 * ANY populated key matches: the driver-reported deviceModel, the
 * (manufacturer, deviceType, deviceId) triple, or the driver type name (only
 * for vendor-specific driver names — generic ones like "Generic Z-Wave Lock"
 * must never be used as match keys).
 */
@Serializable
data class MatchSpec(
    val deviceModels: List<String> = emptyList(),
    val driverTypes: List<String> = emptyList(),
    val ids: List<ZwaveIdTriple> = emptyList()
)

@Serializable
data class ZwaveIdTriple(
    val manufacturer: Int,
    val deviceType: Int,
    val deviceId: Int
)

/**
 * One hardware line of a model. 700-series and 800LR share a model name but
 * take DIFFERENT images (flashing the wrong one can brick), so a line is
 * selected only when the installed firmware's major version matches
 * installedMajor exactly once across the entry's lines — never guessed.
 */
@Serializable
data class FirmwareLine(
    val line: String,
    val installedMajor: List<Int>,
    val latest: String,
    val url: String,
    val note: String? = null
)

/**
 * Firmware versions compare as decimals, matching both Zooz's file notation
 * (V01R30 = 1.30 > V01R24 = 1.24) and the zwaveJS endpoint's rendering of the
 * same firmware ("3.6" == the driver's "3.60"). Returns null for anything that
 * doesn't parse as a plain decimal.
 */
fun parseFirmwareVersion(version: String): BigDecimal? =
    version.trim().toBigDecimalOrNull()

fun firmwareMajor(version: String): Int? =
    parseFirmwareVersion(version)?.toInt()

private fun String.toBigDecimalOrNull(): BigDecimal? =
    try {
        BigDecimal(this)
    } catch (_: NumberFormatException) {
        null
    }
