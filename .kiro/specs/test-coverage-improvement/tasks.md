# Implementation Plan

- [x] 1. Upgrade technology stack
  - [x] 1.1 Update Gradle wrapper to latest stable version
    - Run `./gradlew wrapper --gradle-version=<latest>` to update wrapper
    - Verify gradle/wrapper/gradle-wrapper.properties is updated
    - _Requirements: 9.2_
  
  - [x] 1.2 Update Java version in build configuration
    - Update sourceCompatibility and targetCompatibility in build.gradle.kts to latest LTS version
    - Update any Java version references in CI/CD configuration
    - _Requirements: 9.1_
  
  - [x] 1.3 Update Kotlin version in build.gradle.kts
    - Update Kotlin plugin version to latest stable release
    - Update kotlinx-coroutines version to latest stable
    - Update kotlinx-serialization version to latest stable
    - _Requirements: 9.3_
  
  - [x] 1.4 Update all dependencies to latest stable versions
    - Update Ktor to latest stable version
    - Update Kotlin Telegram Bot API to latest stable version
    - Update Kotest to latest stable version
    - Update any other dependencies to latest stable versions
    - _Requirements: 9.4_
  
  - [x] 1.5 Run all existing tests to verify compatibility
    - Execute `./gradlew test` to run all tests
    - Verify all tests pass with upgraded versions
    - _Requirements: 9.6_
  
  - [x] 1.6 Fix any breaking changes from version upgrades
    - Review compiler warnings and errors
    - Update deprecated API usage to new patterns
    - Ensure code compiles without errors
    - _Requirements: 9.5_
  
  - [x] 1.7 Verify all functionality works as expected
    - Run manual smoke tests of key bot commands
    - Verify bot connects and responds correctly
    - _Requirements: 9.5_

- [x] 2. Set up testing infrastructure
  - Add Kotest property testing dependency to build.gradle.kts
  - Create test directory structure for property tests and integration tests
  - _Requirements: 7.1, 8.1_

- [x] 2.5 Configure JaCoCo code coverage
  - [x] 2.5.1 Add JaCoCo plugin to build.gradle.kts
    - Add jacoco plugin to plugins block
    - Configure JaCoCo toolVersion to latest stable (0.8.11)
    - _Requirements: 11.3_
  
  - [x] 2.5.2 Configure coverage thresholds
    - Set up jacocoTestCoverageVerification task with 80% line coverage minimum
    - Set up jacocoTestCoverageVerification task with 75% branch coverage minimum
    - Configure build to fail if thresholds not met
    - _Requirements: 11.1, 11.2, 11.4_
  
  - [x] 2.5.3 Configure coverage reports
    - Set up jacocoTestReport task to generate HTML and XML reports
    - Configure test task to finalize with jacocoTestReport
    - Configure check task to depend on jacocoTestCoverageVerification
    - _Requirements: 11.3_

- [x] 3. Create NetworkClient abstraction
  - [x] 3.1 Define NetworkClient interface
    - Create interface with get() and getBody() methods
    - _Requirements: 6.3_
  
  - [x] 3.2 Implement KtorNetworkClient
    - Implement NetworkClient interface wrapping HttpClient
    - _Requirements: 6.3_
  
  - [x] 3.3 Write unit tests for KtorNetworkClient
    - Test successful requests
    - Test error handling
    - _Requirements: 2.1, 2.2_

- [x] 4. Extract and test string utilities
  - [x] 4.1 Create StringUtils.kt with snakeToCamelCase function
    - Move snakeToCamelCase extension function to utility file
    - _Requirements: 8.1_
  
  - [x] 4.2 Write unit tests for snakeToCamelCase
    - Test single word (edge case)
    - Test empty string (edge case)
    - Test multiple underscores
    - _Requirements: 8.2, 8.3, 8.4_
  
  - [x] 4.3 Write property test for snakeToCamelCase
    - **Property 5: Snake case to camel case conversion**
    - **Validates: Requirements 8.1**
    - Generate random snake_case strings
    - Verify capitalization and underscore removal
    - _Requirements: 8.1_

- [x] 5. Extract and test Configuration
  - [x] 5.1 Create BotConfiguration data class
    - Define configuration with all required fields
    - Add fromEnvironment() factory method
    - _Requirements: 6.4_
  
  - [x] 5.2 Write unit tests for Configuration
    - Test fromEnvironment with valid env vars
    - Test fromEnvironment with missing required vars
    - Test custom configuration for testing
    - _Requirements: 6.4_

- [x] 6. Extract and test CommandHandlers
  - [x] 6.1 Create CommandHandlers object
    - Extract handleDeviceCommand from Main.kt
    - Extract handleListCommand from Main.kt
    - Extract handleRefreshCommand from Main.kt
    - Extract handleCancelAlertsCommand from Main.kt
    - Extract handleGetOpenSensorsCommand from Main.kt
    - _Requirements: 1.1, 1.3, 1.4, 1.5, 1.7, 6.1_
  
  - [x] 6.2 Write unit tests for handleDeviceCommand
    - Test successful command execution
    - Test command with missing arguments
    - Test command with wrong number of arguments
    - Test device not found
    - Test unsupported command
    - _Requirements: 1.1, 1.2, 1.8_
  
  - [x] 6.3 Write unit tests for handleListCommand
    - Test with multiple device types
    - Test with empty device list
    - _Requirements: 1.3_
  
  - [x] 6.4 Write unit tests for handleRefreshCommand
    - Test successful refresh
    - Test refresh with warnings
    - Test network error during refresh
    - _Requirements: 1.4, 2.1_
  
  - [x] 6.5 Write unit tests for handleCancelAlertsCommand
    - Test successful alert cancellation
    - Test HSM command failure
    - _Requirements: 1.5, 2.4_
  
  - [x] 6.6 Write unit tests for handleGetOpenSensorsCommand
    - Test with open sensors
    - Test with no open sensors
    - Test with attribute query failure
    - _Requirements: 1.7, 2.3_
  
  - [x] 6.7 Write property test for command parsing
    - **Property 1: Command parsing correctness**
    - **Validates: Requirements 1.1**
    - Generate random valid commands with arguments
    - Verify correct parsing of command, device name, and args
    - _Requirements: 1.1_

- [x] 7. Extract and test HubOperations
  - [x] 7.1 Create HubOperations object
    - Extract initializeHubs from Main.kt
    - Extract updateHubs from Main.kt (current simple version)
    - _Requirements: 5.1, 4.1, 6.1_
  
  - [x] 7.2 Write unit tests for initializeHubs
    - Test successful initialization with valid IP
    - Test with null IP address (edge case)
    - Test with missing IP attribute (edge case)
    - Test management token retrieval success
    - Test management token retrieval failure
    - Test complete API failure
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_
  
  - [x] 7.3 Write unit tests for updateHubs (simple version)
    - Test all updates succeed
    - Test some updates fail
    - Test all updates fail
    - Test exception handling
    - _Requirements: 4.1, 4.2, 4.3, 4.4_
  
  - [x] 7.4 Write property test for hub initialization
    - **Property 6: Hub initialization with valid IP**
    - **Validates: Requirements 5.1**
    - Generate random valid IP addresses
    - Verify hub IP is set correctly
    - _Requirements: 5.1_

- [x] 8. Test DeviceCommandFilter
  - [x] 8.1 Write unit tests for DeviceCommandFilter
    - Test with system command (should return false)
    - Test with null message text (edge case)
    - Test with empty message text (edge case)
    - _Requirements: 3.2, 3.4_
  
  - [x] 8.2 Write property test for valid device commands
    - **Property 2: DeviceCommandFilter accepts valid device commands**
    - **Validates: Requirements 3.1**
    - Generate random valid device commands
    - Verify filter returns true
    - _Requirements: 3.1_
  
  - [x] 8.3 Write property test for invalid commands
    - **Property 3: DeviceCommandFilter rejects invalid commands**
    - **Validates: Requirements 3.3**
    - Generate random invalid commands
    - Verify filter returns false
    - _Requirements: 3.3_

- [x] 9. Add property tests for DeviceAbbreviator
  - [x] 9.1 Write property test for abbreviation uniqueness
    - **Property 4: Abbreviation uniqueness**
    - **Validates: Requirements 7.1**
    - Generate random sets of device names
    - Verify all abbreviations are unique
    - Test with names of different token counts (edge case)
    - _Requirements: 7.1, 7.3_

- [x] 10. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Implement hub update polling
  - [x] 11.1 Add HubVersionInfo data class
    - Define data class for tracking versions
    - _Requirements: 10.1_
  
  - [x] 11.2 Add UpdateProgress data class
    - Define data class for tracking update progress
    - _Requirements: 10.6_
  
  - [x] 11.3 Add getHubVersions helper function to HubOperations
    - Query hub for current and available firmware versions
    - Parse JSON response to extract version strings
    - _Requirements: 10.1_
  
  - [x] 11.4 Implement updateHubsWithPolling in HubOperations
    - Check current and available versions for all hubs
    - Skip hubs that are already up to date
    - Initiate updates for hubs that need them
    - Poll hub versions periodically
    - Report progress via callback
    - Handle timeout scenario
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_
  
  - [x] 11.5 Write unit tests for getHubVersions
    - Test successful version retrieval
    - Test with malformed JSON response
    - Test with network error
    - _Requirements: 10.1_
  
  - [x] 11.6 Write unit tests for updateHubsWithPolling
    - Test all hubs up to date
    - Test successful updates with polling
    - Test timeout scenario
    - Test progress reporting
    - Test version change reporting
    - _Requirements: 10.2, 10.3, 10.4, 10.5, 10.6_

- [x] 12. Update Main.kt to use polling for /update command
  - [x] 12.1 Update /update command to use updateHubsWithPolling
    - Replace simple updateHubs with updateHubsWithPolling
    - Add progress message callback that sends messages to user
    - Handle Result properly for success and failure cases
    - _Requirements: 10.1, 10.6_

- [x] 13. Add integration tests
  - [x] 13.1 Write integration test for command flow
    - Test full flow from message to response
    - Test with real DeviceManager and mocked network
    - _Requirements: 1.1, 1.3, 1.4, 1.5, 1.7_
  
  - [x] 13.2 Write integration test for hub update with polling
    - Test full update flow with polling
    - Test with mocked network responses
    - Verify progress messages
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_

- [x] 14. Final checkpoint - Ensure all tests pass and coverage meets thresholds
  - Run `./gradlew test jacocoTestReport jacocoTestCoverageVerification`
  - Verify all tests pass
  - Verify coverage meets 80% line and 75% branch thresholds
  - Review coverage report at build/reports/jacoco/test/html/index.html
  - Ask the user if questions arise
  - _Requirements: 11.1, 11.2, 11.3, 11.4_
