package jbaru.ch.telegram.hubitat

import jbaru.ch.telegram.hubitat.model.CatalogEntry
import jbaru.ch.telegram.hubitat.model.FirmwareCatalog
import jbaru.ch.telegram.hubitat.model.FirmwareLine
import jbaru.ch.telegram.hubitat.model.parseFirmwareVersion

/**
 * Decides what /firmware says about a device: matches it against the catalog,
 * picks the one firmware line allowed to claim its installed major (the
 * brick-avoidance gate), and compares versions - refusing to guess whenever
 * the answer is ambiguous.
 */
internal object FirmwareClassifier {

    fun classify(device: ZwaveDeviceInfo, catalog: FirmwareCatalog): FirmwareFinding {
        val (entry, matchProblem) = matchEntry(device, catalog)
        if (matchProblem != null) return matchProblem
        checkNotNull(entry)
        if (entry.recommendation == "skip") {
            return FirmwareFinding(device, FirmwareStatus.SKIP, entry)
        }
        return classifyVersion(device, entry)
    }

    private fun classifyVersion(device: ZwaveDeviceInfo, entry: CatalogEntry): FirmwareFinding {
        val versionString = device.firmwareVersion
            ?: return FirmwareFinding(
                device, FirmwareStatus.UNREADABLE, entry,
                detail = device.readError ?: "no firmware version reported"
            )
        val installed = parseFirmwareVersion(versionString)
            ?: return FirmwareFinding(
                device, FirmwareStatus.UNREADABLE, entry,
                detail = "unparseable firmware version '$versionString'"
            )

        val (line, lineProblem) = resolveLine(device, entry, versionString, installed.toInt())
        if (lineProblem != null) return lineProblem
        checkNotNull(line)

        return compareVersions(device, entry, line, versionString, installed)
    }

    /** Either the single matching catalog entry, or a terminal finding - never both. */
    private fun matchEntry(
        device: ZwaveDeviceInfo,
        catalog: FirmwareCatalog
    ): Pair<CatalogEntry?, FirmwareFinding?> {
        val matches = catalog.entries.filter { entry ->
            device.deviceModel != null && device.deviceModel in entry.match.deviceModels ||
                device.driverType.isNotEmpty() && device.driverType in entry.match.driverTypes ||
                entry.match.ids.any {
                    it.manufacturer == device.manufacturer &&
                        it.deviceType == device.deviceType &&
                        it.deviceId == device.productId
                }
        }
        return when (matches.size) {
            1 -> matches.single() to null
            0 -> null to FirmwareFinding(device, FirmwareStatus.NOT_IN_CATALOG)
            else -> null to FirmwareFinding(
                device, FirmwareStatus.CHECK_MANUALLY,
                detail = "matches multiple catalog entries: ${matches.joinToString { it.model }}"
            )
        }
    }

    /**
     * Line selection is the brick-avoidance gate: exactly one line may claim
     * the installed major, otherwise we refuse to recommend a file.
     */
    private fun resolveLine(
        device: ZwaveDeviceInfo,
        entry: CatalogEntry,
        versionString: String,
        major: Int
    ): Pair<FirmwareLine?, FirmwareFinding?> {
        val lines = entry.lines.filter { major in it.installedMajor }
        return when (lines.size) {
            1 -> lines.single() to null
            0 -> null to FirmwareFinding(
                device, FirmwareStatus.CHECK_MANUALLY, entry,
                detail = "no known firmware line for installed version $versionString"
            )
            else -> null to FirmwareFinding(
                device, FirmwareStatus.CHECK_MANUALLY, entry,
                detail = "installed version $versionString matches multiple lines: ${lines.joinToString { it.line }}"
            )
        }
    }

    private fun compareVersions(
        device: ZwaveDeviceInfo,
        entry: CatalogEntry,
        line: FirmwareLine,
        versionString: String,
        installed: java.math.BigDecimal
    ): FirmwareFinding {
        val latest = parseFirmwareVersion(line.latest)
            ?: return FirmwareFinding(
                device, FirmwareStatus.CHECK_MANUALLY, entry, line,
                detail = "catalog latest '${line.latest}' is unparseable"
            )

        // A line can span majors with a version-scheme reset (ZEN76 10.x
        // updates to 2.30), where numeric comparison is meaningless. Decimal
        // compare only holds within a major; an installed version numerically
        // ahead of a different-major latest is a scheme mismatch, not currency.
        if (installed >= latest && installed.toInt() != latest.toInt()) {
            return FirmwareFinding(
                device, FirmwareStatus.CHECK_MANUALLY, entry, line,
                detail = "installed $versionString vs line latest ${line.latest} — version scheme mismatch"
            )
        }

        return when {
            installed >= latest -> FirmwareFinding(device, FirmwareStatus.UP_TO_DATE, entry, line)
            entry.recommendation == "warn" -> FirmwareFinding(
                device, FirmwareStatus.WARN, entry, line, detail = entry.warning
            )
            else -> FirmwareFinding(device, FirmwareStatus.UPDATE_AVAILABLE, entry, line)
        }
    }
}
