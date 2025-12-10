# Design Document

## Overview

This feature adds Hubitat modes control to the Telegram bot by implementing three new commands: `/get_mode`, `/list_modes`, and `/set_mode`. The implementation follows the existing architecture pattern using the Maker API for hub communication, the NetworkClient for HTTP operations, and CommandHandlers for command processing.

Modes in Hubitat represent the operational state of the home automation system. Each hub has a set of defined modes (typically Home, Away, Night, Day, etc.) with one mode active at any time. This feature enables users to query and change modes remotely via Telegram.

## Architecture

The modes control feature integrates into the existing bot architecture:

1. **Main.kt** - Registers new command handlers in the bot dispatcher
2. **CommandHandlers** - Implements handler functions for mode commands
3. **ModeOperations** (new) - Encapsulates mode-related API operations
4. **NetworkClient** - Existing interface for HTTP communication (note: needs PUT support)
5. **BotConfiguration** - Existing configuration for API credentials

The design follows the same pattern as existing features like device commands and hub operations, ensuring consistency and maintainability.

**Important Note:** The NetworkClient interface currently only has a `get()` method. Since the Maker API uses PUT for setting modes, we need to either:
- Add a `put()` method to NetworkClient, or
- Use the underlying HTTP client directly for PUT requests

The implementation will need to handle this appropriately.

## Components and Interfaces

### ModeOperations Object

A new singleton object that encapsulates mode-related operations:

```kotlin
data class ModeInfo(
    val id: Int,
    val name: String,
    val active: Boolean
)

object ModeOperations {
    suspend fun getAllModes(
        networkClient: NetworkClient,
        makerApiAppId: String,
        makerApiToken: String,
        hubIp: String
    ): Result<List<ModeInfo>>
    
    suspend fun getCurrentMode(
        networkClient: NetworkClient,
        makerApiAppId: String,
        makerApiToken: String,
        hubIp: String
    ): Result<ModeInfo>
    
    suspend fun setMode(
        networkClient: NetworkClient,
        makerApiAppId: String,
        makerApiToken: String,
        hubIp: String,
        modeName: String
    ): Result<String>
    
    // Internal helper to find mode ID by name
    private suspend fun findModeIdByName(
        modes: List<ModeInfo>,
        modeName: String
    ): Int?
}
```

### CommandHandlers Extensions

Add three new handler functions to the existing CommandHandlers object:

```kotlin
object CommandHandlers {
    // ... existing handlers ...
    
    suspend fun handleGetModeCommand(
        networkClient: NetworkClient,
        makerApiAppId: String,
        makerApiToken: String,
        hubIp: String
    ): String
    
    suspend fun handleListModesCommand(
        networkClient: NetworkClient,
        makerApiAppId: String,
        makerApiToken: String,
        hubIp: String
    ): String
    
    suspend fun handleSetModeCommand(
        message: Message,
        networkClient: NetworkClient,
        makerApiAppId: String,
        makerApiToken: String,
        hubIp: String
    ): String
}
```

### Main.kt Integration

Register command handlers in the bot dispatcher:

```kotlin
command("get_mode") {
    val result = runBlocking {
        CommandHandlers.handleGetModeCommand(
            networkClient, config.makerApiAppId, 
            config.makerApiToken, config.defaultHubIp
        )
    }
    bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = result)
}

command("list_modes") {
    val result = runBlocking {
        CommandHandlers.handleListModesCommand(
            networkClient, config.makerApiAppId, 
            config.makerApiToken, config.defaultHubIp
        )
    }
    bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = result)
}

command("set_mode") {
    val result = runBlocking {
        CommandHandlers.handleSetModeCommand(
            message, networkClient, config.makerApiAppId, 
            config.makerApiToken, config.defaultHubIp
        )
    }
    bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = result)
}
```

## Data Models

### Mode Response Format

The Maker API returns mode information in JSON format:

**Get All Modes Response:**
```json
[
  {
    "name": "Home",
    "id": 1,
    "active": true
  },
  {
    "name": "Away",
    "id": 2,
    "active": false
  },
  {
    "name": "Night",
    "id": 3,
    "active": false
  }
]
```

The current mode is identified by the `"active": true` field in the modes list.

### API Endpoints

Based on Hubitat Maker API documentation:

- **Get All Modes:** `GET /apps/api/{appId}/modes?access_token={token}`
  - Returns a JSON array of all available modes with their IDs and active status
  - The current mode has `"active": true`

- **Set Mode:** `PUT /apps/api/{appId}/modes/{modeId}?access_token={token}`
  - Sets the hub mode to the specified mode ID
  - Note: The endpoint uses PUT method and requires the mode ID (not name)
  - **Implementation Flow:** When a user provides a mode name, the implementation must:
    1. Call GET /modes to retrieve all available modes with their IDs
    2. Find the mode with matching name (case-insensitive)
    3. Extract the mode ID
    4. Call PUT /modes/{modeId} with the extracted ID

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Mode list display indicates active mode

*For any* mode list response, the currently active mode should be clearly indicated in the formatted output string.

**Validates: Requirements 2.2**

### Property 2: Successful mode retrieval includes mode name

*For any* successful getCurrentMode operation, the response string should contain the mode name in a clear, readable format.

**Validates: Requirements 1.3**

### Property 3: Set mode with valid name succeeds

*For any* mode name that appears in the getAllModes response, calling setMode with that name should succeed and return a success status.

**Validates: Requirements 3.1**

### Property 4: Invalid mode name rejection

*For any* string that does not appear in the getAllModes response, calling setMode with that string should fail with an appropriate error message.

**Validates: Requirements 3.3**

### Property 5: Successful mode change confirmation includes mode name

*For any* successful setMode operation, the confirmation message should contain the new mode name.

**Validates: Requirements 3.4**

## Error Handling

### Network Errors

- Connection failures to the Hubitat hub should return user-friendly error messages
- HTTP error responses should be logged and converted to readable error messages
- Timeout scenarios should be handled gracefully

### Validation Errors

- Missing mode name in `/set_mode` command should prompt the user for input
- Invalid mode names should return a message listing valid modes
- Empty or malformed API responses should be handled with appropriate error messages

### Error Message Format

All error messages should:
- Be clear and actionable
- Include relevant context (e.g., which mode was invalid)
- Suggest next steps when appropriate (e.g., "Use /list_modes to see available modes")

## Testing Strategy

### Unit Tests

Unit tests will verify:
- Command handler functions return expected messages for various inputs
- Error handling for missing parameters
- Message formatting for mode lists and confirmations
- Integration between command handlers and ModeOperations

### Property-Based Tests

Property-based tests will use Kotest's property testing framework to verify:
- Universal properties that should hold across all mode operations
- Round-trip consistency (set then get returns the same mode)
- Validation logic for mode names
- Error handling across various invalid inputs

Each property-based test will:
- Run a minimum of 100 iterations
- Be tagged with a comment referencing the correctness property from this design document
- Use the format: `**Feature: modes-control, Property {number}: {property_text}**`
- Implement exactly one correctness property per test

The dual testing approach ensures both specific examples work correctly (unit tests) and general correctness properties hold across all inputs (property tests).
