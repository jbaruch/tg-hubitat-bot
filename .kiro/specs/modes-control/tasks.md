# Implementation Plan

- [x] 1. Create ModeInfo data class and ModeOperations object structure
  - Create the ModeInfo data class with id, name, and active fields
  - Create the ModeOperations object with function signatures
  - Create placeholder implementations that return Result.failure
  - _Requirements: 4.1, 4.2, 4.3_

- [x] 2. Write unit tests for getAllModes (TDD)
  - Test successful mode retrieval
  - Test JSON parsing
  - Test error handling for network failures
  - _Requirements: 2.1, 2.3_

- [x] 2.1 Implement getAllModes functionality
  - Implement the getAllModes function to call the Maker API /modes endpoint
  - Parse the JSON response into List<ModeInfo>
  - Handle network errors appropriately
  - Make the tests pass
  - _Requirements: 2.1, 2.3_

- [x] 3. Write unit tests for getCurrentMode (TDD)
  - Test finding active mode from list
  - Test error handling when no active mode exists
  - Test network error propagation
  - _Requirements: 1.1, 1.2_

- [x] 3.1 Implement getCurrentMode functionality
  - Implement getCurrentMode by calling getAllModes and finding the active mode
  - Return the ModeInfo with active=true
  - Handle case where no active mode is found
  - Make the tests pass
  - _Requirements: 1.1, 1.2_

- [x] 4. Add PUT support to NetworkClient
  - Add put() method to NetworkClient interface
  - Implement put() in KtorNetworkClient
  - Write unit tests for the PUT implementation
  - _Requirements: 4.1_

- [x] 5. Write unit tests for setMode (TDD)
  - Test successful mode change with valid name
  - Test error when mode name is not found
  - Test case-insensitive mode name matching
  - Test error handling for API failures
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 5.1 Implement setMode functionality
  - Implement setMode to first call getAllModes to get mode IDs
  - Find the mode ID by name (case-insensitive matching)
  - Call PUT /modes/{modeId} endpoint with the found ID
  - Return appropriate success or error messages
  - Make the tests pass
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 6. Write unit tests for handleGetModeCommand (TDD)
  - Test successful response formatting
  - Test error message formatting
  - _Requirements: 1.1, 1.2, 1.3_

- [x] 6.1 Implement handleGetModeCommand in CommandHandlers
  - Add handleGetModeCommand function to CommandHandlers object
  - Call ModeOperations.getCurrentMode
  - Format the response message with the current mode name
  - Handle errors and return user-friendly error messages
  - Make the tests pass
  - _Requirements: 1.1, 1.2, 1.3_

- [x] 7. Write unit tests for handleListModesCommand (TDD)
  - Test mode list formatting with active mode indication
  - Test error message formatting
  - _Requirements: 2.1, 2.2, 2.3_

- [x] 7.1 Implement handleListModesCommand in CommandHandlers
  - Add handleListModesCommand function to CommandHandlers object
  - Call ModeOperations.getAllModes
  - Format the response to show all modes with active mode indicated
  - Handle errors and return user-friendly error messages
  - Make the tests pass
  - _Requirements: 2.1, 2.2, 2.3_

- [x] 8. Write unit tests for handleSetModeCommand (TDD)
  - Test successful mode change response
  - Test missing mode name error
  - Test invalid mode name error with suggestions
  - Test confirmation message formatting
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 8.1 Implement handleSetModeCommand in CommandHandlers
  - Add handleSetModeCommand function to CommandHandlers object
  - Parse the mode name from the message text
  - Validate that a mode name was provided
  - Call ModeOperations.setMode with the mode name
  - Format success confirmation with the new mode name
  - Handle errors including invalid mode names
  - Make the tests pass
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 9. Write property tests for correctness properties
- [x] 9.1 Write property test for mode list display
  - **Property 1: Mode list display indicates active mode**
  - **Validates: Requirements 2.2**

- [x] 9.2 Write property test for successful mode retrieval format
  - **Property 2: Successful mode retrieval includes mode name**
  - **Validates: Requirements 1.3**

- [x] 9.3 Write property test for valid mode name
  - **Property 3: Set mode with valid name succeeds**
  - **Validates: Requirements 3.1**

- [x] 9.4 Write property test for invalid mode name rejection
  - **Property 4: Invalid mode name rejection**
  - **Validates: Requirements 3.3**

- [x] 9.5 Write property test for confirmation message
  - **Property 5: Successful mode change confirmation includes mode name**
  - **Validates: Requirements 3.4**

- [x] 10. Register command handlers in Main.kt
  - Add command("get_mode") handler that calls handleGetModeCommand
  - Add command("list_modes") handler that calls handleListModesCommand
  - Add command("set_mode") handler that calls handleSetModeCommand
  - Ensure all handlers follow the existing pattern with runBlocking and sendMessage
  - _Requirements: 4.3_

- [x] 11. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.
