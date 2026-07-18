#!/usr/bin/env bash
# Build the image tar, ship it to the NAS, load it, and (re)start via docker compose.
#
# Prereqs (one-time on the NAS, in $NAS_DIR):
#   - docker-compose.yml (this script ships it each run)
#   - .env with the real secrets (see .env.example; never committed, stays on the NAS)
#
# Usage: ./deploy.sh          (host/dir overridable via NAS_HOST / NAS_DIR env vars)
set -euo pipefail

NAS_HOST="${NAS_HOST:-nas}"
NAS_DIR="${NAS_DIR:-/home/jbaruch/hubitat-bot}"

VERSION="$(grep -E '^version\s*=' build.gradle.kts | sed -E 's/.*"([^"]+)".*/\1/')"
if [ -z "$VERSION" ]; then
  echo "Could not read version from build.gradle.kts" >&2
  exit 1
fi

echo "==> Building tg-hubitat-bot ${VERSION}"
./gradlew --no-daemon jibBuildTar

TAR="build/tg-hubitat-bot-${VERSION}-docker-image.tar"
if [ ! -f "$TAR" ]; then
  echo "Image tar not found: $TAR" >&2
  exit 1
fi

echo "==> Shipping image and compose file to ${NAS_HOST}:${NAS_DIR}"
ssh "$NAS_HOST" "mkdir -p '${NAS_DIR}'"
scp -O "$TAR" "${NAS_HOST}:${NAS_DIR}/image.tar"
scp -O docker-compose.yml "${NAS_HOST}:${NAS_DIR}/docker-compose.yml"

echo "==> Loading image and starting via compose on ${NAS_HOST}"
ssh "$NAS_HOST" "cd '${NAS_DIR}' && docker load < image.tar && rm -f image.tar && TAG='${VERSION}' docker compose up -d"

echo "==> Deployed ${VERSION}. Recent logs:"
ssh "$NAS_HOST" "docker logs --tail 8 jbaru.ch_tg-hubitat-bot-1 2>&1 | grep -iE 'Init successful|Exception|Skipping' || docker logs --tail 8 jbaru.ch_tg-hubitat-bot-1"
