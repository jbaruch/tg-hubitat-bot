# Implementation Plan

- [x] 1. Add HTTP request/response logging first
  - Add logging to `NetworkClient.getBody()` to log all HTTP requests (method, URL, params)
  - Add logging for HTTP responses (status code, content-type header, first 500 chars of body)
  - Log at INFO level so we can see what's actually being called
  - This will help us see exactly what endpoint is being called and what response is received
  - _Requirements: 1.4, 1.5_

- [x] 2. Write integration test that calls real hub API
  - Create a test in `HubOperationsTest.kt` that uses real NetworkClient (not mocked)
  - Test should call `getHubVersions()` with a real hub from the device manager
  - Mark test with `@Tag("integration")` or similar to distinguish from unit tests
  - Test should currently FAIL with the HTML parsing error we're seeing in production
  - This will confirm the actual bug and what endpoint is being called
  - _Requirements: 1.1, 1.2, 1.3_

- [x] 3. Run the integration test and analyze logs
  - Execute the integration test to reproduce the bug
  - Examine the HTTP logs to see what URL is being called
  - Examine the response to see what's actually being returned
  - Determine the correct endpoint path based on the logs and error
  - Document findings before proceeding with the fix
  - _Requirements: All_

- [x] 4. Fix the endpoint path in getHubVersions()
  - Update the endpoint URL in `HubOperations.getHubVersions()` based on findings from step 3
  - Change to the correct endpoint path that returns valid JSON
  - _Requirements: 1.1, 1.2_

- [x] 5. Add error handling for non-JSON responses
  - Wrap JSON parsing in try-catch block in `getHubVersions()`
  - Create descriptive error messages that include hub label, endpoint URL, and response preview
  - Include first 200 characters of response body in error message for debugging
  - _Requirements: 1.4, 1.5_

- [x] 6. Verify integration test now passes
  - Run the integration test from step 2
  - Verify it now passes and successfully retrieves hub version information
  - Verify the logs show the correct endpoint being called
  - Ensure no regressions in existing tests
  - _Requirements: All_

- [x] 7. Update mocked integration tests
  - Update `HubUpdateIntegrationTest` mocks to use the correct endpoint path
  - Verify all mocked integration tests still pass
  - _Requirements: 1.1_

- [x] 8. Add unit tests for error handling
  - Add unit test with mocked HTML response to verify error message quality
  - Add unit test with empty response body
  - Add unit test with malformed JSON
  - Add unit test with JSON missing firmware version fields
  - These tests verify error handling works correctly
  - _Requirements: 1.3, 1.4, 1.5_
