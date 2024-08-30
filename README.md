## Telegram Bot for Hubitat Elevation.
### Currently Supported Commands:
* /on [device to turn on]
* /off [device to turn off]
* /open [device to open]
* /close [device to close]
* /cancel_alerts - Cancels all alerts in HSM
* /update - Updates all hubs for which Hub Information Drivers are exposed in the Maker API of `hubitat.local`

### Prerequisites and Setup:
1. Install the Maker API app on the `hubitat.local` hub.
2. Save `MAKER_API_APP_ID` in environment variables. The ID is shown in the request examples in the Maker API app.
3. Save `MAKER_API_TOKEN` in environment variables.
4. For `cancel_alerts`, ensure Maker API control of HSM is allowed. 
5. Expose all needed devices in Maker API (including devices from other hubs via mesh).
6. For `update`, ensure Hub Information Driver v3 is installed on every hub (via Hubitat Package Manager) and exposed in Maker API on the `hubitat.local` hub (directly and via mesh).
7. Create a bot in Telegram using BotFather bot.
8. Save the BOT_TOKEN in environment variables.
9. Using BotFather, add the commands to the bot command list
10. Deploy tg-hubitat-bot-docker-image.tar as an image in your Docker environment and create and run a new container.

### Adding New Device Types and Building From Source:
It's almost certain that you'll need to add new device types to the JSON hierarchy to support your devices. 
[Device.tk](https://github.com/jbaruch/tg-hubitat-bot/blob/main/src/main/kotlin/model/Device.kt) contains the list of supported devices in the form of classes with their abilities. 
As currently, only actuators and shades are supported, so all the concrete device implementations extend from either `jbaru.ch.telegram.hubitat.model.Device.Actuator` or `jbaru.ch.telegram.hubitat.model.Device.Shade` (except `jbaru.ch.telegram.hubitat.model.Device.Hub`).
Add another class that extends from the relevant abstract class (to provide support for the correct commands) and enter the device type from Hubitat in the `@SerialName` annotation. Done.

Run `./gradlew build` to build a regular JVM project, `./gradlew jibDockerBuild` to build a docker container, and `./gradlew jibBuildTar` to create a portable tar of the container.
