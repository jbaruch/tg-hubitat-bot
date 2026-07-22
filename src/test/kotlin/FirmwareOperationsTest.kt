package jbaru.ch.telegram.hubitat

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import jbaru.ch.telegram.hubitat.model.CatalogEntry
import jbaru.ch.telegram.hubitat.model.Device
import jbaru.ch.telegram.hubitat.model.FirmwareCatalog
import jbaru.ch.telegram.hubitat.model.FirmwareLine
import jbaru.ch.telegram.hubitat.model.MatchSpec
import jbaru.ch.telegram.hubitat.model.ZwaveIdTriple
import jbaru.ch.telegram.hubitat.model.parseFirmwareVersion
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

private fun device(
    name: String = "Test Device",
    driverType: String = "Some Driver",
    deviceModel: String? = null,
    firmwareVersion: String? = null,
    manufacturer: Int? = null,
    deviceType: Int? = null,
    productId: Int? = null,
    readError: String? = null
) = ZwaveDeviceInfo(
    hubLabel = "Hub", deviceId = 1, dni = "0A", name = name, driverType = driverType,
    deviceModel = deviceModel, firmwareVersion = firmwareVersion,
    manufacturer = manufacturer, deviceType = deviceType, productId = productId,
    readError = readError
)

private val testCatalog = FirmwareCatalog(
    catalogVersion = "test",
    entries = listOf(
        CatalogEntry(
            model = "ZEN76", vendor = "Zooz",
            match = MatchSpec(deviceModels = listOf("ZEN76")),
            lines = listOf(
                FirmwareLine("800LR", listOf(3), "3.60", "https://example.com/ZEN76_V03R60.gbl"),
                FirmwareLine("700", listOf(2, 10), "2.30", "https://example.com/ZEN76_V02R30.gbl")
            )
        ),
        CatalogEntry(
            model = "LOCK-1", vendor = "Ultraloq",
            match = MatchSpec(ids = listOf(ZwaveIdTriple(1106, 4, 1))),
            battery = true, flirs = true,
            recommendation = "warn", warning = "known to fail on early hardware",
            lines = listOf(FirmwareLine("all", listOf(1), "1.5", "https://example.com/lock.gbl"))
        ),
        CatalogEntry(
            model = "RSZ1", vendor = "Springs",
            match = MatchSpec(driverTypes = listOf("Springs Window Fashions Roller Shade")),
            recommendation = "skip", note = "no usable vendor firmware"
        )
    )
)

class FirmwareOperationsTest : FunSpec({

    context("version parsing and comparison") {
        test("driver and zwaveJS notations of the same firmware compare equal") {
            parseFirmwareVersion("3.60")!!.compareTo(parseFirmwareVersion("3.6")!!) shouldBe 0
        }

        test("versions compare as decimals, not as integer minor parts") {
            (parseFirmwareVersion("2.30")!! > parseFirmwareVersion("2.24")!!).shouldBeTrue()
            (parseFirmwareVersion("13.02")!! < parseFirmwareVersion("13.3")!!).shouldBeTrue()
            (parseFirmwareVersion("1.01")!! < parseFirmwareVersion("1.5")!!).shouldBeTrue()
        }

        test("garbage does not parse") {
            parseFirmwareVersion("N/A") shouldBe null
            parseFirmwareVersion("") shouldBe null
        }
    }

    context("classification") {
        test("device behind its line gets an update") {
            val finding = FirmwareOperations.classify(
                device(deviceModel = "ZEN76", firmwareVersion = "3.40"), testCatalog
            )
            finding.status shouldBe FirmwareStatus.UPDATE_AVAILABLE
            finding.line?.line shouldBe "800LR"
            finding.line?.latest shouldBe "3.60"
        }

        test("device on latest is up to date, across notations") {
            FirmwareOperations.classify(
                device(deviceModel = "ZEN76", firmwareVersion = "3.6"), testCatalog
            ).status shouldBe FirmwareStatus.UP_TO_DATE
        }

        test("line selection follows installed major") {
            val finding = FirmwareOperations.classify(
                device(deviceModel = "ZEN76", firmwareVersion = "2.20"), testCatalog
            )
            finding.status shouldBe FirmwareStatus.UPDATE_AVAILABLE
            finding.line?.line shouldBe "700"
            finding.line?.latest shouldBe "2.30"
        }

        test("cross-major version scheme reset is never compared numerically") {
            // ZEN76 10.x belongs to the 700 line whose latest is 2.30; 10.10 is
            // numerically 'ahead' but the scheme differs, so we refuse to judge.
            val finding = FirmwareOperations.classify(
                device(deviceModel = "ZEN76", firmwareVersion = "10.10"), testCatalog
            )
            finding.status shouldBe FirmwareStatus.CHECK_MANUALLY
            finding.detail.shouldNotBeNull() shouldContain "version scheme mismatch"
        }

        test("installed major with no catalog line is never guessed") {
            val finding = FirmwareOperations.classify(
                device(deviceModel = "ZEN76", firmwareVersion = "5.00"), testCatalog
            )
            finding.status shouldBe FirmwareStatus.CHECK_MANUALLY
            finding.detail.shouldNotBeNull() shouldContain "no known firmware line"
        }

        test("id-triple match with warn recommendation reports WARN, not update") {
            val finding = FirmwareOperations.classify(
                device(
                    driverType = "Generic Z-Wave Lock", firmwareVersion = "1.01",
                    manufacturer = 1106, deviceType = 4, productId = 1
                ),
                testCatalog
            )
            finding.status shouldBe FirmwareStatus.WARN
            finding.detail shouldBe "known to fail on early hardware"
        }

        test("driver-type match with skip recommendation skips even without a version") {
            val finding = FirmwareOperations.classify(
                device(driverType = "Springs Window Fashions Roller Shade"), testCatalog
            )
            finding.status shouldBe FirmwareStatus.SKIP
            finding.entry?.model shouldBe "RSZ1"
        }

        test("unknown device is reported as not in catalog") {
            FirmwareOperations.classify(
                device(deviceModel = "UNK00", firmwareVersion = "3.40"), testCatalog
            ).status shouldBe FirmwareStatus.NOT_IN_CATALOG
        }

        test("matched device without a readable version is reported unreadable") {
            val finding = FirmwareOperations.classify(
                device(deviceModel = "ZEN76", readError = "boom"), testCatalog
            )
            finding.status shouldBe FirmwareStatus.UNREADABLE
            finding.detail shouldBe "boom"
        }

        test("device matching multiple catalog entries is never guessed") {
            val ambiguous = FirmwareCatalog(
                catalogVersion = "test",
                entries = listOf(
                    CatalogEntry("A", "V", match = MatchSpec(deviceModels = listOf("X"))),
                    CatalogEntry("B", "V", match = MatchSpec(deviceModels = listOf("X")))
                )
            )
            val finding = FirmwareOperations.classify(
                device(deviceModel = "X", firmwareVersion = "1.0"), ambiguous
            )
            finding.status shouldBe FirmwareStatus.CHECK_MANUALLY
            finding.detail.shouldNotBeNull() shouldContain "multiple catalog entries"
        }
    }

    context("bundled catalog") {
        val catalog = FirmwareOperations.loadCatalog()

        test("loads and is populated") {
            catalog.entries.shouldNotBeEmpty()
        }

        test("every entry is matchable, sane, and update lines carry https URLs") {
            catalog.entries.forEach { entry ->
                val matchable = entry.match.deviceModels.isNotEmpty() ||
                    entry.match.driverTypes.isNotEmpty() ||
                    entry.match.ids.isNotEmpty()
                assert(matchable) { "${entry.model} has no match keys" }
                assert(entry.recommendation in setOf("update", "warn", "skip")) {
                    "${entry.model} has unknown recommendation '${entry.recommendation}'"
                }
                if (entry.recommendation != "skip") {
                    assert(entry.lines.isNotEmpty()) { "${entry.model} has no firmware lines" }
                }
                entry.lines.forEach { line ->
                    line.url shouldStartWith "https://"
                    parseFirmwareVersion(line.latest).shouldNotBeNull()
                    assert(line.installedMajor.isNotEmpty()) {
                        "${entry.model}/${line.line} has no installedMajor selector"
                    }
                }
            }
        }

        test("no entry has overlapping installedMajor selectors across lines") {
            catalog.entries.forEach { entry ->
                val majors = entry.lines.flatMap { it.installedMajor }
                assert(majors.size == majors.distinct().size) {
                    "${entry.model} has overlapping installedMajor selectors"
                }
            }
        }

        test("generic driver names are never match keys") {
            catalog.entries.flatMap { it.match.driverTypes }.forEach {
                assert(!it.startsWith("Generic")) { "'$it' is a generic driver name used as a match key" }
            }
        }

        test("real-world classifications hold against the bundled catalog") {
            FirmwareOperations.classify(
                device(deviceModel = "ZEN76", firmwareVersion = "3.40"), catalog
            ).status shouldBe FirmwareStatus.UPDATE_AVAILABLE

            FirmwareOperations.classify(
                device(
                    driverType = "Generic Z-Wave Lock", firmwareVersion = "1.01",
                    manufacturer = 1106, deviceType = 4, productId = 1
                ),
                catalog
            ).status shouldBe FirmwareStatus.WARN

            FirmwareOperations.classify(
                device(driverType = "Springs Window Fashions Roller Shade", firmwareVersion = "13.02"), catalog
            ).status shouldBe FirmwareStatus.SKIP

            // Catalog only knows the ZSE43 1.x line; a 2.x install must not be
            // offered 1.20 as a "downgrade update".
            FirmwareOperations.classify(
                device(deviceModel = "ZSE43", firmwareVersion = "2.00"), catalog
            ).status shouldBe FirmwareStatus.CHECK_MANUALLY
        }
    }

    context("catalog fetching") {
        test("uses the live catalog from the repo main branch when reachable") {
            val networkClient = mock<NetworkClient>()
            whenever(networkClient.getBody(argThat { equals(FirmwareOperations.CATALOG_URL) }, any()))
                .thenReturn("""{"catalogVersion": "live-version", "entries": []}""")

            val (catalog, note) = FirmwareOperations.fetchCatalog(networkClient)

            catalog.catalogVersion shouldBe "live-version"
            note shouldBe null
        }

        test("falls back to the bundled catalog when the live one is unreachable, and says so") {
            val networkClient = mock<NetworkClient>()
            whenever(networkClient.getBody(any(), any())).thenThrow(RuntimeException("no route to host"))

            val (catalog, note) = FirmwareOperations.fetchCatalog(networkClient)

            catalog.entries.shouldNotBeEmpty()
            note.shouldNotBeNull() shouldContain "bundled fallback"
        }

        test("the fallback note surfaces in the report header") {
            val report = FirmwareOperations.formatReport(
                emptyList(), testCatalog, catalogNote = "bundled fallback — live catalog unreachable"
            ).joinToString("\n\n")
            report shouldContain "catalog test (bundled fallback"
        }
    }

    context("collecting devices from a hub") {
        val hub = Device.Hub(1, "Test Hub", ip = "192.168.1.50")

        val devicesListJson = """
            {"devices": [
                {"data": {"id": 725, "dni": "016F", "name": "Closet Light", "type": "Zooz ZEN Switch Advanced", "isZwave": true}},
                {"data": {"id": 39, "dni": "75", "name": "Left Shade", "type": "Springs Window Fashions Roller Shade", "isZwave": true}},
                {"data": {"id": 452, "dni": "x", "name": "Hue Bulb", "type": "CoCoHue RGBW Bulb", "isZwave": false}}
            ]}
        """.trimIndent()

        test("reads driver data, normalizing int-or-string values") {
            val networkClient = mock<NetworkClient>()
            whenever(networkClient.getBody(argThat { endsWith("/hub2/devicesList") }, any()))
                .thenReturn(devicesListJson)
            whenever(networkClient.getBody(argThat { endsWith("/device/fullJson/725") }, any()))
                .thenReturn("""{"device": {"data": {"deviceModel": "ZEN76", "firmwareVersion": "3.60", "manufacturer": "634", "deviceType": 28672, "deviceId": 40966}}}""")
            whenever(networkClient.getBody(argThat { endsWith("/device/fullJson/39") }, any()))
                .thenReturn("""{"device": {"data": {"zwNodeInfo": "53 BC"}}}""")
            whenever(networkClient.getBody(argThat { contains("deviceFirmware/details?nodeId=117") }, any()))
                .thenReturn("""{"success": true, "nodeId": 117, "targets": [{"target": 0, "version": "13.02"}]}""")

            val devices = FirmwareOperations.collectZwaveDevices(hub, networkClient)

            devices.size shouldBe 2
            val zen = devices.first { it.name == "Closet Light" }
            zen.deviceModel shouldBe "ZEN76"
            zen.firmwareVersion shouldBe "3.60"
            zen.manufacturer shouldBe 634
            zen.deviceType shouldBe 28672
            zen.productId shouldBe 40966

            // Springs driver exposes no firmware: version must come from the
            // zwaveJS endpoint via the DNI-as-hex node id (0x75 = 117).
            val shade = devices.first { it.name == "Left Shade" }
            shade.firmwareVersion shouldBe "13.02"
            shade.readError shouldBe null
        }

        test("a device whose firmware cannot be read carries the error instead of failing the sweep") {
            val networkClient = mock<NetworkClient>()
            whenever(networkClient.getBody(argThat { endsWith("/hub2/devicesList") }, any()))
                .thenReturn(devicesListJson)
            whenever(networkClient.getBody(argThat { endsWith("/device/fullJson/725") }, any()))
                .thenReturn("""{"device": {"data": {"deviceModel": "ZEN76", "firmwareVersion": "3.60"}}}""")
            whenever(networkClient.getBody(argThat { endsWith("/device/fullJson/39") }, any()))
                .thenReturn("""{"device": {}}""")
            whenever(networkClient.getBody(argThat { contains("deviceFirmware/details?nodeId=117") }, any()))
                .thenReturn("""{"success": false}""")

            val devices = FirmwareOperations.collectZwaveDevices(hub, networkClient)

            devices.size shouldBe 2
            devices.first { it.name == "Left Shade" }.readError.shouldNotBeNull() shouldContain "deviceFirmware/details failed"
        }
    }

    context("report formatting") {
        test("report groups findings by action and counts up-to-date only") {
            val findings = listOf(
                FirmwareOperations.classify(device(name = "Fan", deviceModel = "ZEN76", firmwareVersion = "3.40"), testCatalog),
                FirmwareOperations.classify(device(name = "Bath Light", deviceModel = "ZEN76", firmwareVersion = "3.30"), testCatalog),
                FirmwareOperations.classify(device(name = "Closet", deviceModel = "ZEN76", firmwareVersion = "3.60"), testCatalog),
                FirmwareOperations.classify(
                    device(name = "Front Lock", driverType = "Generic Z-Wave Lock", firmwareVersion = "1.01", manufacturer = 1106, deviceType = 4, productId = 1),
                    testCatalog
                ),
                FirmwareOperations.classify(device(name = "Shade", driverType = "Springs Window Fashions Roller Shade"), testCatalog),
                FirmwareOperations.classify(device(name = "Mystery", deviceModel = "UNK00", firmwareVersion = "1.0"), testCatalog)
            )

            val report = FirmwareOperations.formatReport(findings, testCatalog).joinToString("\n\n")

            report shouldContain "Updates available (2)"
            report shouldContain "ZEN76 → 3.60"
            report shouldContain "Fan [Hub] 3.40"
            report shouldContain "https://example.com/ZEN76_V03R60.gbl"
            report shouldContain "NOT recommended (1)"
            report shouldContain "known to fail on early hardware"
            report shouldContain "Skipped by policy (1)"
            report shouldContain "Not in catalog (1)"
            report shouldContain "UNK00 ×1 @ 1.0"
            report shouldContain "Up to date: 1"
        }

        test("hub errors surface in the report") {
            val report = FirmwareOperations.formatReport(emptyList(), testCatalog, listOf("Devices Hub: connect timeout"))
                .joinToString("\n\n")
            report shouldContain "could not be checked"
            report shouldContain "Devices Hub: connect timeout"
        }

        test("oversized reports split into multiple messages under the Telegram cap") {
            val findings = (1..400).map {
                FirmwareOperations.classify(
                    device(name = "Device with a fairly long descriptive name $it", deviceModel = "ZEN76", firmwareVersion = "3.40"),
                    testCatalog
                )
            }
            val messages = FirmwareOperations.formatReport(findings, testCatalog)
            assert(messages.size > 1) { "expected multiple messages, got ${messages.size}" }
            messages.forEach { assert(it.length <= 4096) { "message exceeds Telegram cap: ${it.length}" } }
        }
    }
})
