# Release Notes - Version 3.3

## Bug Fix: Hub Update Status Monitoring

### Problem
During hub update monitoring with polling, the system would fail with "Cannot read Json element because of unexpected end of the input" errors when the hub returned incomplete/truncated JSON responses (e.g., just ")"). This typically happens when:
- The hub is busy processing an update
- The hub is temporarily restarting or crashing mid-response
- Network issues cause response truncation

### Solution
Added robust error handling and retry logic:

1. **Incomplete Response Detection**: Check for empty or suspiciously short responses (< 10 chars) before attempting JSON parsing
2. **Retry Logic with Exponential Backoff**: Automatically retry failed version checks up to 3 times with increasing delays (2s, 4s, 8s)
3. **Better Error Messages**: Clear error messages indicating when incomplete/malformed responses are received, showing the actual response content

### Changes Made
- Added empty response check in `HubOperations.getHubVersions()`
- Implemented retry logic with exponential backoff in `updateHubsWithPolling()` polling loop
- Added test for empty response handling
- Added test for multiple rapid version checks

### Error Handling Flow
```
Attempt 1: Empty response → Wait 2s
Attempt 2: Empty response → Wait 4s  
Attempt 3: Empty response → Wait 8s
Attempt 4: Still failing → Mark as failed
```

### Testing
- All unit tests pass (107 tests)
- All integration tests pass (7 real tests against live Hubitat system)
- New test validates empty response handling
- Tested rapid polling (5 consecutive calls) against live system

### Deployment
**Docker Image:** `jbaru.ch/tg-hubitat-bot:3.3`
**Docker Tar:** `build/tg-hubitat-bot-3.3-docker-image.tar` (108MB)

Load the image:
```bash
docker load < build/tg-hubitat-bot-3.3-docker-image.tar
```

### Backward Compatibility
Fully compatible with version 3.2. No configuration changes required.

### What's Next
The retry logic should handle most transient failures during hub updates. If you still see failures, they likely indicate:
- Hub is taking longer than expected to respond (increase maxAttempts)
- Network connectivity issues
- Hub is genuinely unavailable
