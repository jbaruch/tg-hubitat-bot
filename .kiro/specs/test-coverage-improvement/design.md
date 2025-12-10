# Design Document

## Overview

This design document outlines the approach for improving test coverage and code quality in the Telegram Bot for Hubitat Elevation project. The improvements focus on four main areas:

1. **Technology Stack Upgrade**: Upgrading Java, Gradle, Kotlin, and all dependencies to their latest stable versions
2. **Test Coverage Enhancement**: Adding comprehensive tests for untested components, particularly Main.kt command handlers, error handling paths, and hub initialization
3. **Code Refactoring**: Improving separation of concerns to make the codebase more testable and maintainable
4. **Feature Enhancement**: Adding firmware update polling functionality to provide better user feedback

The design maintains backward compatibility while improving testability through dependency injection and interface abstraction.

## Architecture

### Current Architecture

The current architecture is monolithic with most logic in Main.kt:

```
Main.kt
├── Bot initialization
├── Command handlers (inline)
├── Device operations
├── Hub operations
└── Utility functions

DeviceManager.kt
├── Device caching
├── Device lookup
└── Abbreviation integration

DeviceAbbreviator.kt
└── Abbreviation algorithm
```

### Proposed Architecture

The refactored architecture introduces better separation:

```
Main.kt
├── Bot initialization
└── Command routing

CommandHandlers.kt (new)
├── Device command handler
├── System command handlers
└── Error handling

HubOperations.kt (new)
├── Hub initialization
├── Hub updates with polling
└── Firmware version tracking

NetworkClient.kt (new - interface)
└── HTTP operations abstraction

DeviceManager.kt (unchanged)
DeviceAbbreviator.kt (unchanged)
```

## Components and Interfaces

### 1. CommandHandlers

Extracts command handling logic from Main.kt for better testability.

```kotlin
object CommandHandlers {
    suspend fun handleDeviceCommand(
        bot: Bot,
        message: Message,
        deviceManager: DeviceManager,
        networkClient: NetworkClient
    ): String
    
    suspend fun handleListCommand(deviceManager: DeviceManager): Map<String, String>
    
    suspend fun handleRefreshCommand(
        deviceManager: DeviceManager,
        networkClient: NetworkClient
    ): Pair<Int, List<String>>
    
    suspend fun handleCancelAlertsCommand(networkClient: NetworkClient): String
    
    suspend fun handleUpdateCommand(
        hubs: List<Device.Hub>,
        networkClient: NetworkClient,
        progressCallback: suspend (String) -> Unit
    ): Result<String>
    
    suspend fun handleGetOpenSensorsCommand(
        deviceManager: DeviceManager,
        networkClient: NetworkClient
    ): String
}
```

### 2. HubOperations

Manages hub-specific operations including initialization and updates.

```kotlin
object HubOperations {
    suspend fun initializeHubs(
        deviceManager: DeviceManager,
        networkClient: NetworkClient,
        hubIp: String,
        makerApiAppId: String,
        makerApiToken: String
    ): List<Device.Hub>
    
    suspend fun updateHubsWithPolling(
        hubs: List<Device.Hub>,
        networkClient: NetworkClient,
        maxAttempts: Int = 20,
        delayMillis: Long = 30000,
        progressCallback: suspend (String) -> Unit
    ): Result<String>
    
    suspend fun getHubVersions(
        hub: Device.Hub,
        networkClient: NetworkClient
    ): Pair<String, String> // current, available
}
```

### 3. NetworkClient Interface

Abstracts HTTP operations for easier testing.

```kotlin
interface NetworkClient {
    suspend fun get(url: String, params: Map<String, String> = emptyMap()): HttpResponse
    suspend fun getBody(url: String, params: Map<String, String> = emptyMap()): String
}

class KtorNetworkClient(private val client: HttpClient) : NetworkClient {
    override suspend fun get(url: String, params: Map<String, String>): HttpResponse {
        return client.get(url) {
            params.forEach { (key, value) ->
                parameter(key, value)
            }
        }
    }
    
    override suspend fun getBody(url: String, params: Map<String, String>): String {
        return get(url, params).body()
    }
}
```

### 4. Configuration

Externalize configuration for testing.

```kotlin
data class BotConfiguration(
    val botToken: String,
    val makerApiAppId: String,
    val makerApiToken: String,
    val chatId: String = "",
    val defaultHubIp: String = "hubitat.local"
) {
    companion object {
        fun fromEnvironment(): BotConfiguration {
            return BotConfiguration(
                botToken = getenv("BOT_TOKEN") ?: throw IllegalStateException("BOT_TOKEN not set"),
                makerApiAppId = getenv("MAKER_API_APP_ID") ?: throw IllegalStateException("MAKER_API_APP_ID not set"),
                makerApiToken = getenv("MAKER_API_TOKEN") ?: throw IllegalStateException("MAKER_API_TOKEN not set"),
                chatId = getenv("CHAT_ID") ?: "",
                defaultHubIp = getenv("DEFAULT_HUB_IP") ?: "hubitat.local"
            )
        }
    }
}
```

## Data Models

### HubVersionInfo

Track hub firmware versions for polling.

```kotlin
data class HubVersionInfo(
    val hubLabel: String,
    val currentVersion: String,
    val availableVersion: String,
    val needsUpdate: Boolean = currentVersion != availableVersion
)
```

### UpdateProgress

Track update progress for multiple hubs.

```kotlin
data class UpdateProgress(
    val totalHubs: Int,
    val updatedHubs: Set<String>,
    val failedHubs: Map<String, String>, // hub label to error message
    val inProgressHubs: Set<String>
) {
    val isComplete: Boolean
        get() = updatedHubs.size + failedHubs.size == totalHubs
    
    val successCount: Int
        get() = updatedHubs.size
    
    val failureCount: Int
        get() = failedHubs.size
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*


### Property Reflection

After reviewing all properties identified in the prework, I've identified the following redundancies:

- Requirements 9.1-9.5 are duplicates of Requirements 3.1-3.4 (both testing DeviceCommandFilter)
- Requirement 7.2 is subsumed by 7.1 (uniqueness property covers prefix handling)

These redundant properties will be consolidated into single, comprehensive properties.

### Property 1: Command parsing correctness
*For any* valid device command with the correct number of arguments, parsing should extract the command name, device name, and all arguments correctly
**Validates: Requirements 1.1**

### Property 2: DeviceCommandFilter accepts valid device commands
*For any* message containing a valid device command (a command that exists in the device's supportedOps), the DeviceCommandFilter predicate should return true
**Validates: Requirements 3.1**

### Property 3: DeviceCommandFilter rejects invalid commands
*For any* message containing a command that is not a valid device command, the DeviceCommandFilter predicate should return false
**Validates: Requirements 3.3**

### Property 4: Abbreviation uniqueness
*For any* set of distinct device names, the abbreviation algorithm should produce unique abbreviations for each name
**Validates: Requirements 7.1**

### Property 5: Snake case to camel case conversion
*For any* snake_case string, converting to camelCase should capitalize the first letter of each word after the first, and remove underscores
**Validates: Requirements 8.1**

### Property 6: Hub initialization with valid IP
*For any* valid IP address string, hub initialization should successfully set the hub's IP field to that value
**Validates: Requirements 5.1**

## Error Handling

### Error Categories

1. **Network Errors**
   - Connection failures (UnresolvedAddressException, ConnectException)
   - Timeouts (SocketTimeoutException, HttpRequestTimeoutException)
   - HTTP errors (4xx, 5xx responses)

2. **Validation Errors**
   - Missing required arguments
   - Invalid command formats
   - Device not found

3. **State Errors**
   - Hub not initialized
   - Device manager not initialized
   - Bot not initialized

### Error Handling Strategy

1. **Network Errors**: Catch at the network client level, wrap in descriptive exceptions, log and return error messages to user
2. **Validation Errors**: Validate early, return user-friendly error messages
3. **State Errors**: Fail fast with IllegalStateException during initialization

### Error Response Format

All error responses to users should follow this format:
```
"Error: [Brief description of what went wrong]"
```

For example:
- "Error: Device 'Kitchen Light' not found"
- "Error: Command '/on' requires a device name"
- "Error: Failed to connect to hub at hubitat.local"

## Testing Strategy

### Unit Testing Approach

Unit tests will verify individual components in isolation using mocks for dependencies.

**Key areas for unit tests:**
- Command handlers with mocked DeviceManager and NetworkClient
- Hub initialization with mocked network responses
- DeviceCommandFilter with various message types
- String utility functions (snakeToCamelCase)
- Configuration loading

**Unit test examples:**
- Test handleListCommand returns correct format
- Test handleRefreshCommand with successful response
- Test handleCancelAlertsCommand with error response
- Test hub initialization with null IP address
- Test snakeToCamelCase with single word
- Test snakeToCamelCase with empty string

### Property-Based Testing Approach

Property-based tests will verify universal properties across many generated inputs using Kotest property testing.

**Property testing library**: Kotest (io.kotest:kotest-property)

**Configuration**: Each property test should run a minimum of 100 iterations.

**Key properties to test:**
1. Command parsing correctness (Property 1)
2. DeviceCommandFilter accepts valid commands (Property 2)
3. DeviceCommandFilter rejects invalid commands (Property 3)
4. Abbreviation uniqueness (Property 4)
5. Snake case conversion (Property 5)
6. Hub initialization with valid IPs (Property 6)

**Property test tagging format**: Each property-based test must include a comment with this exact format:
```kotlin
// **Feature: test-coverage-improvement, Property 1: Command parsing correctness**
```

**Generators needed:**
- Valid device commands (command name + device name + correct number of args)
- Invalid commands (unknown commands, wrong arg counts)
- Device names (for abbreviation testing)
- Snake_case strings
- Valid IP addresses (IPv4 format)

### Integration Testing Approach

Integration tests will verify multiple components working together with real (non-mocked) implementations where possible.

**Key integration scenarios:**
- Full command flow from message to response
- Hub update flow with polling
- Device lookup and command execution
- Error propagation through layers

### Test Organization

```
src/test/kotlin/
├── CommandHandlersTest.kt (unit tests)
├── HubOperationsTest.kt (unit tests)
├── DeviceCommandFilterTest.kt (unit tests)
├── StringUtilsTest.kt (unit tests)
├── ConfigurationTest.kt (unit tests)
├── CommandHandlersPropertyTest.kt (property tests)
├── DeviceCommandFilterPropertyTest.kt (property tests)
├── AbbreviationPropertyTest.kt (property tests)
├── StringUtilsPropertyTest.kt (property tests)
├── HubOperationsPropertyTest.kt (property tests)
└── integration/
    ├── CommandFlowIntegrationTest.kt
    └── HubUpdateIntegrationTest.kt
```

### Mocking Strategy

**Mock external dependencies:**
- HttpClient / NetworkClient (for network calls)
- Bot (for Telegram API calls)
- DeviceManager (when testing command handlers)

**Use real implementations:**
- DeviceAbbreviator (fast, deterministic)
- String utilities (pure functions)
- Data classes (no side effects)

**Mocking library**: Mockito-Kotlin

### Test Coverage Goals

- **Line coverage**: Minimum 80% for new code
- **Branch coverage**: Minimum 75% for new code
- **Property coverage**: All identified properties must have property-based tests
- **Error path coverage**: All error handling paths must be tested

### Code Coverage Tooling

**Coverage Plugin**: JaCoCo (Java Code Coverage)

**Configuration**: Add JaCoCo plugin to build.gradle.kts with the following settings:
```kotlin
plugins {
    jacoco
}

jacoco {
    toolVersion = "0.8.11" // Latest stable version
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal() // 80% line coverage
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                minimum = "0.75".toBigDecimal() // 75% branch coverage
            }
        }
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
```

**Coverage Reports**: JaCoCo will generate HTML reports in `build/reports/jacoco/test/html/index.html` and XML reports for CI integration.

**Build Integration**: The build will fail if coverage thresholds are not met, ensuring code quality standards are maintained.

### Continuous Integration

Tests should run on every commit and pull request. The CI pipeline should:
1. Run all unit tests
2. Run all property-based tests
3. Run integration tests
4. Generate coverage reports with JaCoCo
5. Fail if coverage drops below thresholds (80% line, 75% branch)

## Implementation Notes

### Backward Compatibility

All refactoring must maintain backward compatibility:
- Existing command syntax unchanged
- Existing device lookup behavior unchanged
- Existing error messages unchanged (or improved)

### Performance Considerations

- Hub update polling should not block other bot operations
- Property tests should complete in reasonable time (< 10 seconds per property)
- Abbreviation algorithm performance should not degrade with refactoring

### Technology Stack Upgrade Strategy

The project will be upgraded to use the latest stable versions of all components:

**Target Versions:**
- **Java**: Latest LTS version (Java 21 as of December 2024)
- **Gradle**: Latest stable version (8.x series)
- **Kotlin**: Latest stable version (2.x series)
- **Dependencies**: All libraries upgraded to latest stable versions including:
  - Ktor (for HTTP client)
  - Kotlin Telegram Bot API
  - Kotest (for testing)
  - Kotlinx Serialization
  - Kotlinx Coroutines

**Upgrade Process:**
1. Update Gradle wrapper to latest version
2. Update Java version in build configuration
3. Update Kotlin version in build.gradle.kts
4. Update all dependency versions to latest stable releases
5. Run all existing tests to verify compatibility
6. Fix any breaking changes from version upgrades
7. Verify all functionality works as expected

**Compatibility Considerations:**
- Review release notes for breaking changes in major version upgrades
- Update deprecated API usage to new recommended patterns
- Ensure all tests pass after each upgrade step
- Test bot functionality manually after upgrades complete

### Migration Strategy

1. **Phase 0**: Upgrade technology stack (Java, Gradle, Kotlin, dependencies)
2. **Phase 1**: Add NetworkClient interface and implementation
3. **Phase 2**: Extract CommandHandlers with tests
4. **Phase 3**: Extract HubOperations with tests
5. **Phase 4**: Add property-based tests
6. **Phase 5**: Implement hub update polling
7. **Phase 6**: Add integration tests

Each phase should be independently testable and deployable.
