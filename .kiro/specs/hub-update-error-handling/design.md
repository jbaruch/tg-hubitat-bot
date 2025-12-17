# Design Document

## Overview

This design addresses the bug where `HubOperations.getHubVersions()` uses an incorrect endpoint path (`/hub/advanced/hubInfo`) that returns HTML error pages instead of JSON. The fix involves changing the endpoint to the correct path (`/hub/hubInfo`) and adding robust error handling for non-JSON responses.

## Architecture

The fix is localized to the `HubOperations` module with supporting changes to tests:

1. **HubOperations.kt**: Update `getHubVersions()` to use correct endpoint path
2. **Test Suite**: Add failing tests that reproduce the HTML parsing error before the fix
3. **Error Handling**: Improve error messages to include diagnostic information when JSON parsing fails

## Components and Interfaces

### HubOperations.getHubVersions()

**Current Implementation:**
```kotlin
suspend fun getHubVersions(
    hub: Device.Hub,
    networkClient: NetworkClient
): Pair<String, String>
```

**Changes:**
- Update endpoint URL from `http://${hub.ip}/hub/advanced/hubInfo` to `http://${hub.ip}/hub/hubInfo`
- Add try-catch around JSON parsing to provide better error messages
- Include response content preview in error messages for debugging

### Error Handling

When JSON parsing fails, the error message should include:
- The hub label that failed
- The endpoint that was called
- A preview of the response content (first 200 characters)
- The specific parsing error

## Data Models

No changes to data models required. The existing `HubVersionInfo` data class remains unchanged.

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*


### Property Reflection

After analyzing the acceptance criteria, I identified that requirements 1.1 and 1.2 are redundant - verifying the correct endpoint path is used inherently means the incorrect path is not used. Requirements 1.4 and 1.5 can be combined into a single test that verifies HTML responses produce error messages with diagnostic information.

The testable requirements are all specific examples rather than universal properties, as they test specific scenarios (correct path, valid JSON, HTML error) rather than rules that apply across all inputs.

### Example 1: Correct endpoint path usage
For the hub version retrieval operation, the system should call the endpoint `/hub/hubInfo` not `/hub/advanced/hubInfo`
**Validates: Requirements 1.1, 1.2**

### Example 2: Valid JSON parsing
For a valid JSON response containing firmware version information, the system should successfully parse and extract current and available versions
**Validates: Requirements 1.3**

### Example 3: HTML error handling
For an HTML response (such as an error page), the system should fail with a clear error message that includes diagnostic information about the unexpected content
**Validates: Requirements 1.4, 1.5**

## Error Handling

### JSON Parsing Errors

When `Json.parseToJsonElement()` throws an exception:
1. Catch the exception
2. Create an error message that includes:
   - Hub label
   - Endpoint URL that was called
   - First 200 characters of the response body
   - Original exception message
3. Wrap in a descriptive exception and rethrow

Example error message format:
```
Failed to parse hub info response for hub 'Apps Hub Info' from endpoint 'http://192.168.1.100/hub/hubInfo': 
Unexpected JSON token at offset 11...
Response preview: <!doctype html><html lang="en"><head>...
```

### Network Errors

Network errors (connection failures, timeouts) should be allowed to propagate with their original error messages, as they already contain useful diagnostic information.

## Testing Strategy

### Unit Tests

1. **Test correct endpoint path**: Mock NetworkClient and verify `getHubVersions()` calls the correct URL
2. **Test valid JSON parsing**: Provide valid JSON response and verify versions are extracted correctly
3. **Test HTML error response**: Provide HTML response and verify error message contains diagnostic info
4. **Test malformed JSON**: Provide invalid JSON and verify error handling
5. **Test missing firmware version fields**: Provide JSON without expected fields and verify graceful handling

### Property-Based Tests

No property-based tests are needed for this fix, as all requirements are specific examples rather than universal properties.

### Integration Tests

Update existing `HubUpdateIntegrationTest` to verify the correct endpoint path is used in the full update flow.

## Implementation Notes

1. The endpoint change is a one-line fix in `getHubVersions()`
2. Error handling requires wrapping the JSON parsing in try-catch
3. Tests should be written first to reproduce the bug, then verify the fix
4. The integration test mock should be updated to use the correct endpoint path

## Rollout Plan

1. Write failing tests that reproduce the HTML parsing error
2. Update `getHubVersions()` to use correct endpoint
3. Add error handling with diagnostic messages
4. Verify all tests pass
5. Update integration tests to use correct endpoint path
