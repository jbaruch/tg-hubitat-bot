# Release Notes - Version 3.2

## Critical Bug Fix: Hub Update Functionality

### Problem
The `/update` command was failing with HTML parsing errors because it was attempting to access hub endpoints directly (`/hub/advanced/hubInfo` or `/hub/hubInfo`), which return HTML web pages instead of JSON.

### Root Cause
Hub firmware version information cannot be retrieved through direct hub HTTP endpoints. According to Hubitat API documentation (verified via Context7), firmware versions must be accessed through the **Hub Information Driver v3** device via the Maker API.

### Solution
Changed the implementation to query the Hub Information Driver device through the Maker API:

**Old (Broken) Approach:**
```
http://{hubIp}/hub/hubInfo  → Returns HTML, not JSON
```

**New (Correct) Approach:**
```
http://{hubIp}/apps/api/{makerApiAppId}/devices/{hubDeviceId}?access_token={makerApiToken}
```

The response now correctly returns device attributes:
- `firmwareVersionString` - Current hub firmware version
- `hubUpdateVersion` - Latest available firmware version

### Changes Made
1. Updated `HubOperations.getHubVersions()` to use Maker API device endpoint
2. Added required Maker API parameters (`hubIp`, `makerApiAppId`, `makerApiToken`) to function signatures
3. Updated all tests to use correct Maker API response format
4. Fixed all integration tests to mock the correct endpoint

### Prerequisites
- Hub Information Driver v3 must be installed on each hub (via Hubitat Package Manager)
- Hub Information Driver devices must be exposed in the Maker API
- Hubitat version 2.3.9.175 or higher

### Testing
- All unit tests pass (19 tests)
- All integration tests pass (3 tests)
- Tests now use realistic Maker API response format based on actual Hubitat API documentation

### Deployment
**Docker Image:** `jbaru.ch/tg-hubitat-bot:3.2`
**Docker Tar:** `build/tg-hubitat-bot-3.2-docker-image.tar` (108MB)

Load the image:
```bash
docker load < build/tg-hubitat-bot-3.2-docker-image.tar
```

### Lessons Learned
1. **Always consult API documentation** (Context7) before making assumptions about endpoints
2. **Mocked tests can give false confidence** - they passed but the code was broken in production
3. **Integration tests should test real API contracts** - use actual response formats from documentation
4. **Verify assumptions early** - the initial spec was based on incorrect assumptions that weren't validated

### Documentation
- See `.kiro/specs/hub-update-error-handling/ACTUAL_FIX.md` for detailed technical explanation
- Updated requirements document to reflect correct Maker API approach
