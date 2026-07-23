# Changes Log

## Faster, Fault-Tolerant Sensor and Firmware Scans

### Overview
/get_open_sensors and /firmware fan their per-device HTTP calls out concurrently, and one flaky device no longer takes down a whole report.

### Changes Made
- /get_open_sensors reads all contact sensors concurrently (capped); an unreachable sensor is listed under "Could not read" instead of aborting the command
- /firmware fetches per-device firmware details concurrently under the same cap
- Internal warnings go through the logger consistently instead of println

### Benefits
- Reports come back quickly even on large fleets
- A single flaky device degrades one line of the report, not the whole command

## More Abbreviations Survive Dense Name Collisions

### Overview
Abbreviation resolution no longer gives up on a collision group when the first differing word is exhausted but a later word can still make the names unique.

### Changes Made
- Collision expansion scans past word positions that are already fully expanded and only declares a group unabbreviatable when no position anywhere can grow

### Benefits
- Fewer devices lose their short abbreviation in setups with many similar names

## /set_level for Dimmable Devices; README Stops Advertising Unimplemented Commands

### Overview
/set_level now actually works on dimmers and bulbs; the README no longer lists commands that never existed.

### Changes Made
- New Dimmer device family (Room Lights Activator Dimmer/Bulb, Zooz dimmers) supporting on/off/setLevel - "/set_level Kitchen Lights 50" works end to end
- /set_color and /set_color_temperature removed from the README: no color-capable device type exists

### Benefits
- Documented commands and implemented commands are the same set again

## Boot No Longer Hangs on Irreconcilable Device-Name Abbreviations

### Overview
Device labels whose abbreviations can never become unique used to spin the abbreviator forever and the bot never finished booting.

### Changes Made
- Collision groups that are fully expanded and still identical are dropped from abbreviation with a visible "was not abbreviated" warning; the devices stay reachable by full name
- Abbreviation lookups are case-insensitive, matching how names are stored
- A dropped name reports a dedicated "cannot be abbreviated" reason instead of a misleading "was not added"

### Benefits
- Boot always completes, whatever the device labels look like
- Clear warnings instead of a silent hang

## Refresh Consistency and Thread-Safe Device State

### Overview
/refresh now fully refreshes what the bot knows, and concurrent commands can never observe half-refreshed state.

### Changes Made
- The set of recognized device commands is recomputed on every /refresh, so a command class introduced by newly exposed devices (e.g. the first actuator after a sensors-only boot) starts working without a restart
- Device list, name cache, and command set are published as one atomic snapshot; a /refresh racing another command can no longer expose a mixed or half-built state
- Duplicate-label warnings from the name cache now appear in the /refresh reply instead of only the console

### Benefits
- /refresh behaves like a real refresh - no restart needed for new device classes
- No racy surprises when commands and refreshes overlap

## Handler Failure Replies, HTTP Status Checking, and /list Escaping

### Overview
Every command now answers the chat even when the hub or network fails, device/HSM command results reflect the real HTTP outcome, and /list can no longer be broken by a code-fence-breaking character in a device label.

### Changes Made
- Wrapped every single-reply handler in a dispatcher-boundary guard: an uncaught exception is logged with its stack and answered with a short, stable error message instead of dying silently
- Device commands reply with human text in the form users type ("Done: Kitchen Lights → set_level 50"); non-2xx responses read as failures ("Failed: … returned HTTP 404") instead of raw HTTP reason phrases
- /cancel_alerts reports "HSM alerts cancelled." on success and a clear failure line otherwise
- NetworkClient.getBody treats non-2xx responses as errors (URL kept in logs, not in the chat-visible message) so error pages never reach a JSON parser looking like data
- /list escapes backtick and backslash in device labels and aliases - the only two characters that are special inside a MarkdownV2 code fence
- /update's final failure reply is a stable summary; per-hub detail still arrives via progress messages and full details stay in the logs

### Benefits
- The bot never looks dead when the hub is flaky - every command gets an answer
- Failures are visibly failures, successes read like something a human would say
- Internal endpoints and exception internals stay out of the Telegram chat

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
