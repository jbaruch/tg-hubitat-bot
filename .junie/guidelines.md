# Telegram Bot for Hubitat Elevation - Development Guidelines

This document provides essential information for developers working on the Telegram Bot for Hubitat Elevation project.

## Build/Configuration Instructions

### Prerequisites
- JDK 21 or higher
- Kotlin 2.0.20 or higher
- Gradle (wrapper included in the project)

### Environment Variables
The application requires several environment variables to be set:

#### Mandatory
- `BOT_TOKEN` - Telegram bot token obtained from BotFather
- `MAKER_API_APP_ID` - Hubitat Maker API app ID
- `MAKER_API_TOKEN` - Hubitat Maker API token

#### Optional
- `CHAT_ID` - Telegram chat ID for proactive messaging
- `DEFAULT_HUB_IP` - IP address of the Hubitat hub (defaults to "hubitat.local")

#### Required for Deep Reboot Functionality
- `EWELINK_EMAIL` - EweLink account email
- `EWELINK_PASSWORD` - EweLink account password

### Building the Project
1. Clone the repository
2. Build the project:
   ```bash
   ./gradlew build
   ```
3. Create a Docker image:
   ```bash
   ./gradlew jibDockerBuild
   ```
4. (Optional) Export the Docker image to a tar file:
   ```bash
   ./gradlew jibBuildTar
   ```

### Running the Application
Run the Docker container with the required environment variables:
```bash
docker run -d --name tg-hubitat-bot \
  -e BOT_TOKEN=your_bot_token \
  -e MAKER_API_APP_ID=your_app_id \
  -e MAKER_API_TOKEN=your_token \
  -e CHAT_ID=your_chat_id \
  -e DEFAULT_HUB_IP=your_hub_ip \
  -e EWELINK_EMAIL=your_ewelink_email \
  -e EWELINK_PASSWORD=your_ewelink_password \
  jbaru.ch/tg-hubitat-bot
```

## Testing Information

### Running Tests
Run all tests:
```bash
./gradlew test
```

Run a specific test:
```bash
./gradlew test --tests "jbaru.ch.telegram.hubitat.DeviceAbbreviatorTest"
```

### Test Resources
Test resources are located in `src/test/resources/`. The project uses a JSON file (`hubitatDevice.json`) for testing the DeviceManager.

### Writing Tests
The project uses JUnit 5 for testing. Tests should follow these guidelines:

1. Use descriptive test names with backticks (e.g., `test device abbreviation`)
2. Organize related tests using `@Nested` classes
3. Use `@BeforeEach` for common setup
4. Test both success and failure cases
5. Use appropriate assertions from JUnit 5

#### Example Test Structure
```kotlin
@Nested
inner class DeviceAbbreviatorTests {
    private lateinit var abbreviator: DeviceAbbreviator

    @BeforeEach
    fun setUp() {
        abbreviator = DeviceAbbreviator()
    }

    @Test
    fun `test simple abbreviation`() {
        // Test code here
    }
}
```

### Mocking
The project uses Mockito for mocking dependencies in tests. For Kotlin-specific mocking features, use mockito-kotlin.

Example:
```kotlin
// Create a mock
val mockHub = mock<Hub>()
whenever(mockHub.label).thenReturn("Test Hub")

// Verify interactions
verify(mockHub).powerOff()
```

### Integration Tests
Integration tests that require external services (like EweLink) should be marked with appropriate annotations and should only run when the required environment variables are set.

## Additional Development Information

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Use sealed classes for type hierarchies (see `Device.kt`)

### Adding New Device Types
1. Update the `Device` sealed class in `Device.kt`
2. Add appropriate `supportedOps` and `attributes`
3. Add the `@SerialName` annotation with the exact device handler name
4. Update the `DeviceManager` if necessary for special cases

### Error Handling
- Use Kotlin's `Result` type for operations that can fail
- Provide meaningful error messages
- Log errors appropriately

### Case Sensitivity
Note that the `DeviceAbbreviator` class stores device names in lowercase. When retrieving abbreviations, make sure to use lowercase device names:

```kotlin
// Correct
abbreviator.getAbbreviation("kitchen light")

// Incorrect - will fail
abbreviator.getAbbreviation("Kitchen Light")
```

### Coroutines
The project uses Kotlin coroutines for asynchronous operations. Use `runBlocking` for testing coroutines, and `suspend` functions for asynchronous operations.

### Serialization
The project uses Kotlinx Serialization for JSON serialization/deserialization. Use appropriate annotations (`@Serializable`, `@SerialName`) for serialization.