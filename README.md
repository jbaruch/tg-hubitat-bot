# Telegram Bot for Hubitat Elevation

## Quick Start
1. Set up Maker API on `hubitat.local`.
2. Configure environment variables: `MAKER_API_APP_ID`, `MAKER_API_TOKEN`, `BOT_TOKEN`.
3. Deploy the Docker container.
4. Start sending commands to your bot!

## Currently Supported Commands:
* `/on [device to turn on]` - Turns on a specified device.
  - Example: `/on Kitchen Lights`
* `/off [device to turn off]` - Turns off a specified device.
  - Example: `/off Porch Light`
* `/open [device to open]` - Opens a specified device (e.g., shades).
  - Example: `/open Loft Shades`
* `/close [device to close]` - Closes a specified device.
  - Example: `/close Loft Shades`
* `/cancel_alerts` - Cancels all alerts in HSM.
* `/update` - Updates all hubs for which Hub Information Drivers are exposed in the Maker API of `hubitat.local`.

## Device Name Notations:
You can refer to devices in several ways:
* **Full name:** Use the complete device name.
  - Example: `/on Kitchen Lights`
* **Short name:** Omit `Lights` or `Light` if they are the last word in the name.
  - Example: `/off Kitchen`
* **Abbreviation:** Use the first letters of each word in the full name.
  - Example: `/on kl`
  - **Abbreviation conflicts** can be resolved by specifying the first non-conflicting letter in the first non-conflicting word of the name:
    * Let's assume you have three conflicting devices, `Main Bedroom Lights`, `Main Backyard Lights`, and `Main Bathroom Lights`. All three are abbreviated to `mbl`, which creates a conflict.
    * Use `mbel` for `Main Bedroom Lights` as `e` in the second word (Bedroom) is the first unique letter, distinguishing it from `Main Backyard Lights` and `Main Bathroom Lights`.
    * Use `mbacl` for `Main Backyard Lights` as `c` in the second word (Backyard) is the first unique letter, distinguishing it from `Main Bathroom Lights`. Using `mbal` won't be enough, as it is still the same for both `Main Bathroom Lights` and `Main Backyard Lights`.
    * Correspondingly, `Main Bathroom Lights` is uniquely abbreviated to `mbatl`, not `mbal`.


## Prerequisites and Setup:
1. Install the Maker API app on the `hubitat.local` hub.
2. Ensure Maker API control of HSM is allowed for `/cancel_alerts`.
3. Expose all needed devices in Maker API (including devices from other hubs via mesh).
4. Ensure Hub Information Driver v3 is installed on every hub (via Hubitat Package Manager) and exposed in Maker API on `hubitat.local` (directly and via mesh).
5. Create a bot in Telegram using BotFather bot.
6. Configure environment variables:
* `MAKER_API_APP_ID` - The Maker API app ID.
* `MAKER_API_TOKEN` - The Maker API token.
* `BOT_TOKEN` - The Telegram bot token.
7. Deploy the Docker image:
  - Load the Docker image: `docker load < tg-hubitat-bot-docker-image.tar`
  - Create and run the container: `docker run -d --name tg-hubitat-bot -e MAKER_API_APP_ID -e MAKER_API_TOKEN -e BOT_TOKEN tg-hubitat-bot`

### Adding New Device Types:
It's almost certain that you'll need to add new device types to the JSON hierarchy to support your devices.
[Device.tk](https://github.com/jbaruch/tg-hubitat-bot/blob/main/src/main/kotlin/model/Device.kt) contains the list of supported devices in the form of classes with their abilities.
As currently, only actuators and shades are supported, so all the concrete device implementations extend from either `jbaru.ch.telegram.hubitat.model.Device.Actuator` or `jbaru.ch.telegram.hubitat.model.Device.Shade` (except `jbaru.ch.telegram.hubitat.model.Device.Hub`).
Add another class that extends from the relevant abstract class (to provide support for the correct commands) and enter the device type from Hubitat in the `@SerialName` annotation. Done.

### Building:
* `./gradlew build` - Build the project.
* `./gradlew jibDockerBuild` - Build a Docker container.
* `./gradlew jibBuildTar` - Create a portable tar of the container.

## Additional Resources
* [Hubitat Maker API Documentation](https://docs.hubitat.com/index.php?title=Maker_API)
* [Telegram BotFather](https://core.telegram.org/bots#botfather)
* [Hubitat Community Forums](https://community.hubitat.com/)