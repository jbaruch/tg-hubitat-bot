# Changes Log

## Improved EweLink Address Resolution Error Handling

### Overview
This document describes the changes made to improve error handling for address resolution issues in the EweLink integration.

### Changes Made
- Added specific handling for UnresolvedAddressException in the EweLink connection process
- Added detailed error messages that include the likely hostnames that couldn't be resolved
- Improved logging to help diagnose network connectivity issues with the EweLink API
- Enhanced error reporting for both REST API and WebSocket connection failures
- Added region-specific hostname information to error messages

### Benefits
- Better diagnosis of network connectivity issues with the EweLink API
- More informative error messages that help users troubleshoot DNS resolution problems
- Improved reliability by providing specific information about the hostnames that couldn't be resolved
- Better user experience with more detailed error reporting

## Telegram Error Notifications

### Overview
This document describes the changes made to improve error reporting by sending notifications to Telegram when connectivity issues occur.

### Changes Made
- Added a helper function to send error notifications to Telegram when CHAT_ID is set
- Modified all network-related functions to send notifications about connectivity errors
- Added special handling for the connectivity test at startup to report errors after the bot is initialized
- Ensured that the application continues to function even if sending notifications fails

### Benefits
- Users receive immediate notifications about connectivity issues via Telegram
- Administrators can be alerted to problems even when not actively monitoring the console
- Better visibility of network issues helps with faster troubleshooting
- Improved user experience with proactive error reporting

## Enhanced Network Error Handling and Connectivity Testing

### Overview
This document describes the changes made to improve network error handling and connectivity testing in the application.

### Changes Made
- Added comprehensive error handling for various network-related exceptions in all network connection functions
- Added detailed error messages for different types of connection failures (connection refused, timeouts, etc.)
- Enhanced the HTTP client configuration with a 30-second request timeout
- Added a connectivity test function that runs at startup to verify hub connectivity
- Implemented graceful handling of connectivity test failures to allow the application to start even if the hub is temporarily unavailable

### Benefits
- Users receive more specific error messages that help diagnose the exact nature of network issues
- The application is more resilient to network problems and provides better feedback
- Timeouts prevent the application from hanging indefinitely when network issues occur
- The startup connectivity test provides early warning of potential hub connectivity issues
- Better user experience with more informative error messages and improved reliability

## Network Resolution Improvements

### Overview
This document describes the changes made to improve network resolution handling in the application.

### Changes Made
- Reverted the default hub hostname back to "hubitat.local" in Main.kt
- Reverted all references in the README.md back to "hubitat.local"
- Reverted the test file to use "hubitat.local"
- Added explicit error handling for UnresolvedAddressException in all network connection functions
- Added clear error messages that explain the issue when an address can't be resolved

### Benefits
- The application now uses the correct default hub hostname "hubitat.local"
- When address resolution fails, users receive clear error messages explaining the issue
- Improved error handling makes it easier to diagnose network configuration problems
- Better user experience with more informative error messages

## EweLink Integration Changes

### Overview
This document describes the changes made to make the EweLink integration lazy and optional, ensuring that it doesn't affect the initialization of the hub or other operations.

### Changes Made

#### 1. Lazy Initialization
- Removed the automatic initialization of EweLink in the `main()` function
- Created a lazy-initialized `HubPowerManager` that is only created when needed
- Added a `getOrCreateHubPowerManager()` function that handles the initialization of the EweLink connection

#### 2. Optional Functionality
- Made the deep reboot functionality completely optional
- Updated the `deepRebootHub()` function to gracefully handle the case when EweLink is not available
- Added user-friendly error messages to help users understand what went wrong

#### 3. Error Handling
- Enhanced error handling throughout the EweLink integration
- Added more descriptive error messages
- Ensured that failures in EweLink don't affect other hub operations

### Benefits
- The application now starts up faster since it doesn't initialize EweLink until needed
- The application works correctly even if EweLink credentials are not provided
- If there are issues with the EweLink connection, only the deep reboot functionality is affected
- Users receive clear error messages when the deep reboot functionality is not available

### Usage
- To use the deep reboot functionality, set the `EWELINK_EMAIL` and `EWELINK_PASSWORD` environment variables
- If these variables are not set, the deep reboot functionality will be disabled, but all other functionality will work normally
- When a deep reboot is requested, the application will attempt to configure the power control for the hub if it's not already configured
