# HrdaNick 🎭

A Minecraft Spigot/Paper plugin for easily changing player nicknames and skins simultaneously on your server!

## ✨ Main Features

- 🎪 Change nickname + skin at the same time with a single command
- 👥 Admin commands for managing other players' nicknames
- 📋 Command to list all players using nicknames
- ⚙️ Easy configuration
- 🔄 Reload without server restart
- 🔐 Permission system (regular users vs. admins)

## 📦 Requirements

This plugin requires the following plugins to be installed:
- **SkinsRestorer** - for skin management
- **ProtocolLib** - for network communication
- **NickAPI** - for nickname change API

## 📥 Installation

1. Download the latest version from [Modrinth](https://modrinth.com/plugin/hrdanick)
2. Place `HrdaNick.jar` into your server's `plugins/` folder
3. Make sure all **requirements** above are installed
4. Restart your server
5. The plugin will automatically create a configuration file

## 🎮 Commands

| Command | Description | Permission |
|---------|-------------|-----------|
| `/nick <nickname>` | Changes your nickname and skin | `hrdanick.use` |
| `/nick reset` | Resets your nickname | `hrdanick.use` |
| `/nick set <player> <nickname>` | Changes someone's nickname | `hrdanick.admin` |
| `/nick reset <player/@a>` | Resets a player's/all players' nicknames | `hrdanick.admin` |
| `/nick list` | Lists all players with nicknames | `hrdanick.list` |
| `/nick reload` | Reloads the plugin | `hrdanick.admin` |
| `/nick resetall` | Resets all nicknames | `hrdanick.admin` |
| `/nick random <player/@a` | Gives you random nickname from random.yml | `hrdanick.admin` |

## ⚙️ Configuration

The plugin will automatically create a `nicknames.yml` and `random.yml` files in the `plugins/HrdaNick/` folder.

Important Game Rule:
Set this game rule on your server:

## Code
/gamerule announceAdvancements false
Otherwise, some advancements will be announced with the original nickname.

## Known Issues
The plugin requires a current version of ProtocolLib - older versions may not work
Sometimes players need to be kicked and rejoin for full effects
Some other plugins may be incompatible with nickname changes
## Supported Versions
Server Software: Spigot, Paper, Bukkit
Minecraft Versions: 1.20 - 1.21.10
## Support & Contact
Found a bug or want to suggest a feature?
Contact me on Discord: [https://discord.com/invite/TAfvqj2HBV]
## License
This project is licensed under the MIT License.

Made with ❤️ for the Minecraft community
