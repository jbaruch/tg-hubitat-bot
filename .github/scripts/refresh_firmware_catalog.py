#!/usr/bin/env python3
"""Refresh the Z-Wave firmware catalog against vendor download servers.

Two mechanisms, per vendor:

- **Zooz**: scrapes the OTA firmware files KB page (an authoritative index)
  for MODEL_VxxRyy links and compares each against the catalog's lines,
  matched by the major version of the line's current `latest`.
- **Leviton / Ultraloq**: no index page exists, so each catalog line's URL is
  used as a template and a bounded window of higher version numbers is probed
  with HEAD requests. A version counts as published only when the server
  answers 200 with a binary content type and a plausible file size (their
  404/403 pages are HTML/XML). The whole window is scanned - versions are
  sometimes skipped (U-Tec shipped 1.5 then 1.7, never 1.6).

Catalog rewriting policy: lines on entries recommended "update" are bumped in
place (latest + url). Entries flagged "warn" are never auto-bumped - the
warning text is version-specific and needs a human - and new hardware lines
(unknown majors) are never auto-added because `installedMajor` selectors are
human-curated. Both are still reported as news.

Outputs (when GITHUB_OUTPUT is set): `changed` - the catalog file was
rewritten, a PR is needed; `news` - anything newsworthy was found, a Telegram
ping is warranted. The summary lands in `catalog-refresh-summary.md` for use
as the PR body and notification text. Any scrape/parse/network failure other
than a vendor's own not-found answer exits non-zero so a broken page shape or
server change fails the workflow visibly instead of silently reporting "no
changes".
"""

import json
import os
import re
import sys
import urllib.error
import urllib.request
from decimal import Decimal
from pathlib import Path

KB_URL = "https://www.support.getzooz.com/kb/article/1158"
DOWNLOAD_PREFIX = "https://www.getzooz.com"
CATALOG_PATH = Path("src/main/resources/zwave-firmware-catalog.json")
FILE_PATTERN = re.compile(
    r'(/firmware/([A-Z0-9]+)_V(\d+)R(\d+)(?:_[A-Z]+)?\.(?:gbl|otz))"'
)

# URL templates for vendors without an index page. Groups: prefix, major,
# minor, suffix. The version string is rebuilt as f"{major}.{minor}".
PROBE_PATTERNS = {
    "Leviton": re.compile(r"^(.*_v)(\d+)_(\d+)(\.ota)$"),
    "Ultraloq": re.compile(r"^(.*_V)(\d+)\.(\d+)(\.gbl)$"),
}
PROBE_SEPARATORS = {"Leviton": "_", "Ultraloq": "."}
# Same-major minors scanned above the current one, and minors scanned for the
# next major. Wide enough to survive skipped versions.
PROBE_MINOR_WINDOW = 10
PROBE_NEXT_MAJOR_MINORS = 6
MIN_FIRMWARE_BYTES = 50_000


def fetch_kb_page() -> str:
    request = urllib.request.Request(KB_URL, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(request, timeout=30) as response:
        if response.status != 200:
            raise RuntimeError(f"KB page returned HTTP {response.status}")
        return response.read().decode("utf-8", errors="replace")


def scrape_firmware_files(html: str) -> dict:
    """Return {model: [(version, url), ...]} for every standard-named file."""
    files: dict = {}
    for path, model, major, minor in FILE_PATTERN.findall(html):
        version = Decimal(f"{int(major)}.{minor}")
        files.setdefault(model, []).append((version, DOWNLOAD_PREFIX + path))
    if not files:
        raise RuntimeError(
            "KB page yielded no firmware links - the page shape has changed"
        )
    return files


def check_zooz(catalog: dict, scraped: dict) -> list:
    changes = []
    for entry in catalog["entries"]:
        if entry["vendor"] != "Zooz":
            continue
        model_files = scraped.get(entry["model"])
        known_urls = {line["url"] for line in entry["lines"]}
        known_majors = set()
        for line in entry["lines"]:
            current = Decimal(line["latest"])
            line_major = int(current)
            known_majors.add(line_major)
            candidates = [
                (version, url)
                for version, url in (model_files or [])
                if int(version) == line_major
            ]
            if not candidates:
                continue
            best_version, best_url = max(candidates)
            if best_version > current:
                changes.append(
                    f"{entry['model']} ({line['line']}): {line['latest']} -> {best_version} ({best_url})"
                )
                line["latest"] = str(best_version)
                line["url"] = best_url
        # A scraped major no catalog line claims is likely a new hardware
        # line - it needs a human-authored installedMajor selector, so it is
        # reported, never auto-added.
        for version, url in model_files or []:
            if int(version) not in known_majors and url not in known_urls:
                changes.append(
                    f"NEW LINE NEEDED for {entry['model']}: {version} ({url}) matches no cataloged line - add it manually with an installedMajor selector"
                )
    return changes


def firmware_file_exists(url: str) -> bool:
    """True only for a published firmware binary; vendor not-found answers
    (Leviton: HTML 404 page, U-Tec: XML 403) are False. Anything else raises."""
    request = urllib.request.Request(
        url, method="HEAD", headers={"User-Agent": "Mozilla/5.0"}
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            content_type = response.headers.get("Content-Type", "")
            content_length = int(response.headers.get("Content-Length", "0"))
            return (
                response.status == 200
                and "octet-stream" in content_type
                and content_length >= MIN_FIRMWARE_BYTES
            )
    except urllib.error.HTTPError as error:
        if error.code in (403, 404):
            return False
        raise


def probe_line(vendor: str, line: dict) -> tuple | None:
    """Probe a bounded window of versions above the line's current latest.
    Returns (version, url) of the highest published one, or None."""
    pattern = PROBE_PATTERNS[vendor]
    match = pattern.match(line["url"])
    if not match:
        raise RuntimeError(
            f"{vendor} URL '{line['url']}' does not match the expected pattern - update PROBE_PATTERNS"
        )
    prefix, major_text, minor_text, suffix = match.groups()
    major, minor = int(major_text), int(minor_text)
    separator = PROBE_SEPARATORS[vendor]
    minor_width = len(minor_text)

    candidates = [
        (major, candidate_minor)
        for candidate_minor in range(minor + 1, minor + 1 + PROBE_MINOR_WINDOW)
    ] + [
        (major + 1, candidate_minor)
        for candidate_minor in range(0, PROBE_NEXT_MAJOR_MINORS)
    ]

    best = None
    for candidate_major, candidate_minor in candidates:
        minor_string = str(candidate_minor).zfill(minor_width)
        url = f"{prefix}{candidate_major}{separator}{minor_string}{suffix}"
        if firmware_file_exists(url):
            version = Decimal(f"{candidate_major}.{minor_string}")
            if best is None or version > best[0]:
                best = (version, url)
    return best


def check_probed_vendors(catalog: dict) -> list:
    changes = []
    for entry in catalog["entries"]:
        if entry["vendor"] not in PROBE_PATTERNS or entry.get("recommendation") == "skip":
            continue
        for line in entry["lines"]:
            found = probe_line(entry["vendor"], line)
            if found is None:
                continue
            version, url = found
            if version <= Decimal(line["latest"]):
                continue
            if entry.get("recommendation") == "warn":
                changes.append(
                    f"NEW VERSION for {entry['model']} ({entry['vendor']}): {line['latest']} -> {version} ({url}) - entry is warn-flagged, review the warning text and update manually"
                )
            else:
                changes.append(
                    f"{entry['model']} ({line['line']}): {line['latest']} -> {version} ({url})"
                )
                line["latest"] = str(version)
                line["url"] = url
    return changes


def main() -> None:
    catalog = json.loads(CATALOG_PATH.read_text())
    scraped = scrape_firmware_files(fetch_kb_page())
    print(f"Zooz KB page: {sum(len(v) for v in scraped.values())} firmware files for {len(scraped)} models")

    changes = check_zooz(catalog, scraped) + check_probed_vendors(catalog)
    catalog_changed = any(
        not change.startswith(("NEW LINE NEEDED", "NEW VERSION")) for change in changes
    )

    if catalog_changed:
        CATALOG_PATH.write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + "\n")

    summary_lines = ["## Firmware catalog refresh", ""]
    if changes:
        summary_lines += [f"- {change}" for change in changes]
    else:
        summary_lines.append("No changes - catalog is current.")
    summary_lines += [
        "",
        f"Sources: {KB_URL} (Zooz index), vendor download servers (Leviton/U-Tec URL probing)",
        "",
        "Review before merging: hardware-line selectors (`installedMajor`) are human-curated; flashing the wrong line can brick a device.",
    ]
    summary = "\n".join(summary_lines)
    print(summary)
    Path("catalog-refresh-summary.md").write_text(summary + "\n")

    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a") as handle:
            handle.write(f"changed={'true' if catalog_changed else 'false'}\n")
            handle.write(f"news={'true' if changes else 'false'}\n")


if __name__ == "__main__":
    sys.exit(main())
