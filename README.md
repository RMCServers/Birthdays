# Birthdays

Birthdays is a Minecraft plugin that allows players to set birthdays for players.

It provides commands to easily manage birthdays and executes a command on a player's birthday.

## Features

- Set birthdays for players
- List all saved birthdays
- Remove birthdays
- Get the birthday of a player
- Execute a custom command on a player's birthday

## Commands

- `/birthday set <player> <birthday>` - Set a player's birthday
  - Alias: `/bd s`
- `/birthday list` - List all birthdays
  - Alias: `/bd l`
- `/birthday remove <player>` - Remove a player's birthday
  - Alias: `/bd r`
- `/birthday get <player>` - Get a player's birthday
  - Alias: `/bd g`

## Permissions

- `birthdays.set` - Permission to set birthdays
- `birthdays.list` - Permission to list birthdays
- `birthdays.remove` - Permission to remove birthdays
- `birthdays.get` - Permission to get birthdays

## Installation

1. Download `Birthdays.jar` from https://github.com/RMCServers/Birthdays/releases.
2. Place `Birthdays.jar` in the `plugins` folder of the Minecraft server.
3. Restart the Minecraft server.

## Configuration

The plugin can be configured in the `config.yml` file located in the plugin's data folder.

- `birthday_command`: The command to be executed when the system date matches a player's birthday.

## Compatibility

This plugin is compatible with Spigot for Minecraft version 1.8.8.

## Contributing

Contributions are welcome! If you find any issues or have suggestions for improvements, please open an issue or pull request on the GitHub repository.

## License

This plugin is open-source and available under the [GNU General Public License version 3.0](LICENSE.txt).