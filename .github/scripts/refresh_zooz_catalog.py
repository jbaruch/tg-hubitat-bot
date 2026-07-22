#!/usr/bin/env python3
"""Refresh the Zooz portion of the Z-Wave firmware catalog.

Scrapes the Zooz OTA firmware files KB page for MODEL_VxxRyy firmware links,
compares each against the catalog's Zooz lines (matched by the major version of
the line's current `latest`), and rewrites the catalog in place when a newer
file exists. Non-Zooz vendors are never touched.

Prints a human-readable summary and, when GITHUB_OUTPUT is set, emits two
flags: `changed` (the catalog file was rewritten - a PR is needed) and `news`
(anything newsworthy was found, including new hardware lines that need manual
cataloging - a Telegram ping is warranted). The summary is written to
`catalog-refresh-summary.md` for use as the PR body and the notification text.
Exits non-zero on any scrape/parse failure so a broken page shape fails the
workflow visibly instead of silently reporting "no changes".
"""

import json
import os
import re
import sys
import urllib.request
from decimal import Decimal
from pathlib import Path

KB_URL = "https://www.support.getzooz.com/kb/article/1158"
DOWNLOAD_PREFIX = "https://www.getzooz.com"
CATALOG_PATH = Path("src/main/resources/zwave-firmware-catalog.json")
FILE_PATTERN = re.compile(
    r'(/firmware/([A-Z0-9]+)_V(\d+)R(\d+)(?:_[A-Z]+)?\.(?:gbl|otz))"'
)


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


def refresh(catalog: dict, scraped: dict) -> list:
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


def main() -> None:
    catalog = json.loads(CATALOG_PATH.read_text())
    scraped = scrape_firmware_files(fetch_kb_page())
    print(f"Scraped {sum(len(v) for v in scraped.values())} firmware files for {len(scraped)} models")

    changes = refresh(catalog, scraped)
    catalog_changed = any(not change.startswith("NEW LINE NEEDED") for change in changes)

    if catalog_changed:
        CATALOG_PATH.write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + "\n")

    summary_lines = ["## Zooz firmware catalog refresh", ""]
    if changes:
        summary_lines += [f"- {change}" for change in changes]
    else:
        summary_lines.append("No changes - catalog is current.")
    summary_lines += [
        "",
        f"Source: {KB_URL}",
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
