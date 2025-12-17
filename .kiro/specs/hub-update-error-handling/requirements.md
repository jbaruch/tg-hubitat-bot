# Requirements Document

## Introduction

This feature fixes a critical bug in the hub update functionality where the system attempts to access hub endpoints directly (`/hub/advanced/hubInfo` or `/hub/hubInfo`) which return HTML error pages instead of JSON. The root cause is that hub firmware version information must be retrieved through the Maker API by querying the Hub Information Driver device attributes, not through direct hub endpoints. The system needs to use the Maker API to query the Hub Information Driver device for firmware version attributes.

## Glossary

- **Hub**: A Hubitat home automation hub device that can be updated remotely
- **Hub Information Driver**: A virtual device driver in Hubitat that exposes hub information as a device
- **NetworkClient**: The component responsible for making HTTP requests to hub endpoints
- **HubOperations**: The module that manages hub-related operations including version checking and updates
- **Local Hub API**: The HTTP API exposed directly by the Hubitat hub for management operations

## Requirements

### Requirement 1

**User Story:** As a system administrator, I want the update command to use the correct hub info endpoint path, so that the command retrieves version information successfully instead of receiving HTML error pages.

#### Acceptance Criteria

1. WHEN the system retrieves hub version information THEN the system SHALL query the Hub Information Driver device through the Maker API
2. WHEN the system constructs the hub info URL THEN the system SHALL use the Maker API device endpoint format `/apps/api/{appId}/devices/{deviceId}`
3. WHEN the system receives a response from the Maker API THEN the system SHALL receive valid JSON containing device attributes including `firmwareVersionCurrent` and `firmwareVersionLatest`
4. WHEN the endpoint returns HTML THEN the system SHALL provide a clear error message indicating the endpoint returned unexpected content
5. WHEN JSON parsing fails THEN the system SHALL include diagnostic information about what was received

### Requirement 2

**User Story:** As a developer, I want failing tests that reproduce the HTML parsing error, so that I can verify the fix prevents this issue from recurring.

#### Acceptance Criteria

1. WHEN writing tests for hub version retrieval THEN the system SHALL include a test case that simulates receiving HTML instead of JSON
2. WHEN testing with HTML error responses THEN the system SHALL verify the error message is clear and actionable
3. WHEN testing the correct endpoint path THEN the system SHALL verify `/hub/hubInfo` is called not `/hub/advanced/hubInfo`
4. WHEN testing JSON parsing errors THEN the system SHALL verify diagnostic information is included in error messages
5. WHEN tests run THEN the system SHALL verify that valid JSON responses are parsed correctly
