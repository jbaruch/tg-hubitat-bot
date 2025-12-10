# Requirements Document

## Introduction

This feature adds device activity history capabilities to the Telegram bot. Users need the ability to view recent events and state changes for specific devices to answer questions like "When was the garage door last opened?" or "What was the temperature in the bedroom over the last hour?". This is particularly useful for troubleshooting, verification, and peace of mind.

## Glossary

- **Device Event**: A state change or attribute update for a device (e.g., switch turned on, temperature changed, door opened)
- **Event History**: A chronological list of recent events for a device
- **Maker API Events Endpoint**: The `/apps/api/{appId}/devices/{deviceId}/events` endpoint that returns device event history
- **Event Attributes**: The properties of an event including timestamp, attribute name, value, and unit

## Requirements

### Requirement 1

**User Story:** As a user, I want to view recent activity for a specific device, so that I can see when and how the device state changed.

#### Acceptance Criteria

1. WHEN a user sends the /history command with a device name THEN the Telegram Bot SHALL retrieve recent events for that device from the Hubitat Hub
2. WHEN displaying event history THEN the Telegram Bot SHALL show each event with timestamp, attribute name, and value in a readable format
3. WHEN the device is not found THEN the Telegram Bot SHALL return an appropriate error message using the existing device name resolution logic
4. WHEN the device is found but has no recent events THEN the Telegram Bot SHALL return a message indicating no recent activity
5. WHEN the Maker API request fails THEN the Telegram Bot SHALL return an error message with failure details

### Requirement 2

**User Story:** As a user, I want to limit the number of events returned, so that I can get a quick overview without being overwhelmed by data.

#### Acceptance Criteria

1. WHEN a user sends /history without specifying a count THEN the Telegram Bot SHALL return the last 10 events by default
2. WHEN a user sends /history with a count parameter (e.g., /history [device] 20) THEN the Telegram Bot SHALL return up to that many events
3. WHEN the requested count exceeds available events THEN the Telegram Bot SHALL return all available events
4. WHEN the count parameter is invalid THEN the Telegram Bot SHALL return an error message and usage instructions

### Requirement 3

**User Story:** As a user, I want to see timestamps in a readable format, so that I can easily understand when events occurred.

#### Acceptance Criteria

1. WHEN displaying event timestamps THEN the Telegram Bot SHALL format them in a human-readable format (e.g., "2 hours ago", "Dec 10, 3:45 PM")
2. WHEN events are very recent (< 1 minute) THEN the Telegram Bot SHALL display "Just now" or similar
3. WHEN events are from today THEN the Telegram Bot SHALL show time only (e.g., "3:45 PM")
4. WHEN events are from previous days THEN the Telegram Bot SHALL show date and time

### Requirement 4

**User Story:** As a user, I want to filter events by attribute type, so that I can focus on specific state changes (e.g., only temperature readings, only switch states).

#### Acceptance Criteria

1. WHEN a user sends /history with an attribute filter (e.g., /history [device] temperature) THEN the Telegram Bot SHALL return only events for that attribute
2. WHEN the attribute filter doesn't match any events THEN the Telegram Bot SHALL return a message indicating no matching events
3. WHEN no attribute filter is specified THEN the Telegram Bot SHALL return all event types

### Requirement 5

**User Story:** As a developer, I want the device activity feature to integrate with existing architecture, so that the implementation is consistent and maintainable.

#### Acceptance Criteria

1. WHEN making event API calls THEN the implementation SHALL use the existing NetworkClient interface
2. WHEN accessing hub configuration THEN the implementation SHALL use the existing BotConfiguration for API credentials
3. WHEN handling the history command THEN the CommandHandlers object SHALL follow the same pattern as existing command handlers
4. WHEN resolving device names THEN the implementation SHALL use the existing DeviceManager.findDevice() method
5. WHEN formatting responses THEN the implementation SHALL follow existing response formatting patterns

## API Details

Based on Hubitat Maker API documentation, the events endpoint:
- **URL**: `http://{hubIp}/apps/api/{appId}/devices/{deviceId}/events`
- **Method**: GET
- **Query Parameters**: 
  - `access_token` (required)
  - `max` (optional) - maximum number of events to return
- **Response**: JSON array of event objects with properties:
  - `name`: attribute name (e.g., "switch", "temperature", "contact")
  - `value`: attribute value (e.g., "on", "72", "open")
  - `unit`: unit of measurement (e.g., "°F", null for non-numeric)
  - `date`: ISO 8601 timestamp
  - `deviceId`: device ID

## Example Usage

```
User: /history garage door
Bot: Recent activity for Garage Door:
     • 2 hours ago: contact = closed
     • 5 hours ago: contact = open
     • 8 hours ago: contact = closed
     • 10 hours ago: contact = open

User: /history bedroom temp 5
Bot: Recent activity for Bedroom Temperature Sensor:
     • Just now: temperature = 72°F
     • 15 minutes ago: temperature = 71°F
     • 30 minutes ago: temperature = 71°F
     • 45 minutes ago: temperature = 70°F
     • 1 hour ago: temperature = 70°F

User: /history kitchen light temperature
Bot: Recent temperature events for Kitchen Light:
     No temperature events found.
```
