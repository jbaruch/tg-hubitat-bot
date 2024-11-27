# Telegram Bot for Hubitat Elevation

## Quick Start
1. Set up Maker API on `hubitat.local` or use `DEFAULT_HUB_IP` environment variable to specify the IP of your primary hub.
2. Configure mandatory environment variables: `MAKER_API_APP_ID`, `MAKER_API_TOKEN`, `BOT_TOKEN`.
* If you know the ID of your DM chat with your bot, you can add it as `CHAT_ID` variable.
* If your primary hub is not using `hubitat.local` DNS entry, use `DEFAULT_HUB_IP` to specify the ip.
3. Deploy the Docker container.
4. Start sending commands to your bot!

## Currently Supported Commands:
* Device Commands:
    * `/on [device name]` - Turns on a specified device (switches, bulbs, dimmers).
    * `/off [device name]` - Turns off a specified device (switches, bulbs, dimmers).
    * `/open [device name]` - Opens a specified device (e.g., shades).
    * `/close [device name]` - Closes a specified device (e.g., shades).
    * `/double_tap [device name]` - Executes a double tap action on a button device.
    * `/push [device name]` - Executes a push action on a button device.
    * `/hold [device name]` - Executes a hold action on a button device.
    * `/release [device name]` - Executes a release action on a button device.

* System Commands:
    * `/list` - Shows all available devices grouped by type with their aliases.
    * `/refresh` - Refreshes the device list from the hub.
    * `/cancel_alerts` - Cancels all alerts in HSM.
    * `/update` - Updates all hubs for which Hub Information Drivers are exposed in the Maker API of `hubitat.local`.
    * `/get_open_sensors` - Lists all contact sensors that are currently open.
    * `/reboot [Hub Information Driver v3 instance name]` - Performs a soft reboot of a specified hub.
    * `/deep_reboot [Hub Information Driver v3 instance name]` - Performs a complete power cycle of a hub (requires EweLink USB outlet setup).

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
1. For hub updates: Hubitat version 2.3.9.175 or higher.
2. Install the Maker API app on the `hubitat.local` hub.
3. Ensure Maker API control of HSM is allowed for `/cancel_alerts`.
4. Expose all needed devices in Maker API (including devices from other hubs via mesh).
5. Ensure Hub Information Driver v3 is installed on every hub (via Hubitat Package Manager) and exposed in Maker API on `hubitat.local` (directly and via mesh).
6. Create a bot in Telegram using BotFather bot.
7. (Optional) create commands in the bot using BotFather's `/setcommands` command. This is optional as you can send arbitrary commands by text without completion in the bot.
Configure environment variables:
* Mandatory:
    * `MAKER_API_APP_ID` - The Maker API app ID.
    * `MAKER_API_TOKEN` - The Maker API token.
    * `BOT_TOKEN` - The Telegram bot token.
* Optional:
    * `CHAT_ID` - The ID of your DM chat with the Bot (or other chat the bot is added to).
      If you use that variable, the bot will be able to proactively send information to the chat. If you don't use it, it will only reply to your messages.
      There are number of ways to find this ID, some of them are listed [here](https://stackoverflow.com/questions/32423837/telegram-bot-how-to-get-a-group-chat-id).
    * `DEFAULT_HUB_IP` – The IP of the hub the Maker API app is installed on. Defaults to DNS hostname of `hubitat.local`.
* Required for deep reboot functionality:
    * `EWELINK_EMAIL` - Your EweLink account email.
    * `EWELINK_PASSWORD` - Your EweLink account password.
9. Deploy the Docker image:
    - Load the Docker image: `docker load < tg-hubitat-bot-docker-image.tar`
    - Create and run the container: `docker run -d --name tg-hubitat-bot -e MAKER_API_APP_ID -e MAKER_API_TOKEN -e BOT_TOKEN -e EWELINK_EMAIL -e EWELINK_PASSWORD tg-hubitat-bot`

## Deep Reboot Setup (Optional)
To enable deep reboot functionality (complete power cycle of hubs):
1. Get an EweLink compatible USB power outlet.
2. Set up the outlet in the EweLink app.
3. Name your outlet following the pattern "Hub Power - [Hub Name]" where [Hub Name] matches exactly your Hub Information Driver instance name.
4. Provide your EweLink credentials via environment variables.
5. Connect your hub's power supply through the USB outlet.

The deep reboot process:
1. Sends a graceful shutdown command to the hub
2. Waits 45 seconds for shutdown to complete
3. Cuts power to the hub via the USB outlet
4. Waits 60 seconds to ensure complete power drain
5. Restores power
6. Hub begins normal boot process

## Adding New Device Types:
To add support for new device types:
1. Update the `Device` sealed class in `Device.kt` to include the new device type.
    - For special device types that require complex functionality (like Hub), consider creating a separate file.
2. Add the appropriate `supportedOps` for the new device type.
3. Define the appropriate `attributes` for the new device type.
4. Add the `@SerialName` annotation with the exact name matching the device handler name in Hubitat.
5. If necessary, update the `DeviceManager` to handle any special cases for the new device type.
6. Test the new device type to ensure that all operations and attribute queries work as expected.

### Build the project
1. Build the project using `./gradlew build`.
2. Create the Docker image using `./gradlew jibDockerBuild`
3. (Optional) Export the image to tar file using `./gradlew jibBuildTar`