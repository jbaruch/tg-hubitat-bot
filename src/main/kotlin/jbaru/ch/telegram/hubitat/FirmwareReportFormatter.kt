package jbaru.ch.telegram.hubitat

import jbaru.ch.telegram.hubitat.model.CatalogEntry
import jbaru.ch.telegram.hubitat.model.FirmwareCatalog

/**
 * Renders /firmware findings into Telegram-sized messages: one section per
 * status, chunked under the message-length cap on section (or, for an
 * oversized section, line) boundaries.
 */
internal object FirmwareReportFormatter {

    private const val MAX_MESSAGE_LENGTH = 3900

    fun format(
        findings: List<FirmwareFinding>,
        catalog: FirmwareCatalog,
        hubErrors: List<String> = emptyList(),
        catalogNote: String? = null
    ): List<String> {
        val byStatus = findings.groupBy { it.status }
        val sections = mutableListOf<String>()

        sections.add(header(findings, catalog, catalogNote))
        byStatus[FirmwareStatus.UPDATE_AVAILABLE]?.let { sections.add(updatesSection(it)) }
        byStatus[FirmwareStatus.WARN]?.let { sections.add(warnSection(it)) }
        byStatus[FirmwareStatus.CHECK_MANUALLY]?.let { sections.add(manualSection(it)) }
        byStatus[FirmwareStatus.UNREADABLE]?.let { sections.add(unreadableSection(it)) }
        byStatus[FirmwareStatus.SKIP]?.let { sections.add(skippedSection(it)) }
        byStatus[FirmwareStatus.NOT_IN_CATALOG]?.let { sections.add(notInCatalogSection(it)) }
        byStatus[FirmwareStatus.UP_TO_DATE]?.let { sections.add("✅ Up to date: ${it.size}") }
        if (hubErrors.isNotEmpty()) {
            sections.add("💥 Hubs that could not be checked:\n" + hubErrors.joinToString("\n") { "• $it" })
        }

        return chunk(sections)
    }

    private fun header(
        findings: List<FirmwareFinding>,
        catalog: FirmwareCatalog,
        catalogNote: String?
    ): String {
        val hubCount = findings.map { it.device.hubLabel }.distinct().size
        val catalogSuffix = if (catalogNote != null) " ($catalogNote)" else ""
        return "Z-Wave firmware report: ${findings.size} devices on $hubCount hub(s), " +
            "catalog ${catalog.catalogVersion}$catalogSuffix"
    }

    private fun updatesSection(updates: List<FirmwareFinding>): String {
        val section = StringBuilder("⬆️ Updates available (${updates.size}):\n")
        updates.groupBy { it.entry!! to it.line!! }.forEach { (key, group) ->
            val (entry, line) = key
            section.append("\n${entry.model} → ${line.latest} (${line.line})")
            if (entry.battery || entry.flirs) section.append(" 🔋 wake device for OTA")
            section.append("\n${line.url}\n")
            line.note?.let { section.append("note: $it\n") }
            group.forEach {
                section.append("• ${it.device.name} [${it.device.hubLabel}] ${it.device.firmwareVersion}\n")
            }
        }
        return section.toString().trimEnd()
    }

    private fun warnSection(warns: List<FirmwareFinding>): String {
        val section = StringBuilder("⚠️ Behind, but update NOT recommended (${warns.size}):\n")
        warns.groupBy { it.entry!! }.forEach { (entry, group) ->
            val line = group.first().line
            section.append("\n${entry.model}: ${line?.latest} available\n")
            group.first().detail?.let { section.append("$it\n") }
            group.forEach {
                section.append("• ${it.device.name} [${it.device.hubLabel}] ${it.device.firmwareVersion}\n")
            }
        }
        return section.toString().trimEnd()
    }

    private fun manualSection(manual: List<FirmwareFinding>): String {
        val section = StringBuilder("❓ Check manually (${manual.size}):\n")
        manual.forEach {
            section.append(
                "• ${it.device.name} [${it.device.hubLabel}] " +
                    "${it.entry?.model ?: it.device.driverType}: ${it.detail}\n"
            )
        }
        return section.toString().trimEnd()
    }

    private fun unreadableSection(unreadable: List<FirmwareFinding>): String {
        val section = StringBuilder("🚫 Could not read firmware (${unreadable.size}):\n")
        unreadable.forEach {
            section.append("• ${it.device.name} [${it.device.hubLabel}]: ${it.detail}\n")
        }
        return section.toString().trimEnd()
    }

    private fun skippedSection(skipped: List<FirmwareFinding>): String {
        val section = StringBuilder("⏭️ Skipped by policy (${skipped.size}):\n")
        skipped.groupBy { it.entry!! }.forEach { (entry: CatalogEntry, group) ->
            section.append("• ${entry.model} (${entry.vendor}) ×${group.size}: ${entry.note}\n")
        }
        return section.toString().trimEnd()
    }

    private fun notInCatalogSection(unknown: List<FirmwareFinding>): String {
        val section = StringBuilder("📋 Not in catalog (${unknown.size}):\n")
        unknown.groupBy { it.device.deviceModel ?: it.device.driverType }.forEach { (key, group) ->
            val versions = group.mapNotNull { it.device.firmwareVersion }.distinct().sorted()
            val versionInfo = if (versions.isEmpty()) "" else " @ ${versions.joinToString("/")}"
            section.append("• $key ×${group.size}$versionInfo\n")
        }
        return section.toString().trimEnd()
    }

    // Telegram caps messages at 4096 chars; split on section boundaries, and
    // within a section on line boundaries if a single section is oversized.
    private fun chunk(sections: List<String>): List<String> {
        val messages = mutableListOf<String>()
        val current = StringBuilder()
        for (section in sections) {
            val pieces = if (section.length > MAX_MESSAGE_LENGTH) {
                splitOversized(section)
            } else {
                listOf(section)
            }
            for (piece in pieces) {
                if (current.isNotEmpty() && current.length + piece.length + 2 > MAX_MESSAGE_LENGTH) {
                    messages.add(current.toString().trimEnd())
                    current.clear()
                }
                if (current.isNotEmpty()) current.append("\n\n")
                current.append(piece)
            }
        }
        if (current.isNotEmpty()) messages.add(current.toString().trimEnd())
        return messages
    }

    private fun splitOversized(section: String): List<String> {
        val pieces = mutableListOf<String>()
        val current = StringBuilder()
        for (lineText in section.lineSequence()) {
            // A single line beyond the cap (a pathological device name) gets
            // hard-chunked - it must never pass through oversized.
            val chunks = lineText.chunked(MAX_MESSAGE_LENGTH)
            for (chunk in chunks) {
                if (current.isNotEmpty() && current.length + chunk.length + 1 > MAX_MESSAGE_LENGTH) {
                    pieces.add(current.toString().trimEnd())
                    current.clear()
                }
                if (current.isNotEmpty()) current.append("\n")
                current.append(chunk)
            }
        }
        if (current.isNotEmpty()) pieces.add(current.toString().trimEnd())
        return pieces
    }
}
