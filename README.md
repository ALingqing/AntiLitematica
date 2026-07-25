# AntiLitematica

**Litematica / Schematica / Printer detection plugin for Paper/Folia**

![Version](https://img.shields.io/badge/version-6.3.0-blue) ![MC Version](https://img.shields.io/badge/1.21.4%2B-green)
[![Build](https://github.com/ALingqing/AntiLitematica/actions/workflows/maven.yml/badge.svg)](https://github.com/ALingqing/AntiLitematica/actions/workflows/maven.yml)
[![bStats](https://bstats.org/signatures/bukkit/AntiLitematica.svg)](https://bstats.org/plugin/bukkit/AntiLitematica/31012)

---

## Overview

AntiLitematica is a server-side plugin that detects and prevents the use of Litematica, Schematica, and their Printer features on your Minecraft server. No client-side mod installation required.

Built with ProtocolLib deep inspection and a graduated punishment system, AntiLitematica provides robust protection while minimizing false positives.

---

## Features

- **Network Channel Detection** -- Monitors servux:litematics and Schematica plugin channel registration and payloads
- **ProtocolLib Deep Inspection** -- Detects EasyPlace protocol abuse, NBT debug queries, and Servux metadata fingerprints
- **Anti-Printer Engine** -- Multi-layered block placement analysis: enforced raytrace, rate limiting, consecutive same-type, no-look-change
- **Command Guard** -- Blocks rapid command bursts and /setblock spam, with configurable allowlist
- **World Whitelist** -- Skip detection entirely in configured worlds (build/creative servers)
- **Graduated Punishment** -- Escalating penalties: Warn, TempBan, Ban. Supports LiteBans, AdvancedBan, EssentialsX
- **Multi-World Support** -- Per-world configuration, world-aware violation tracking
- **Admin GUI** -- In-game inventory management panel (/al gui)
- **Geyser Compatible** -- Bedrock players auto-exempted from all checks (Floodgate API + prefix)
- **Discord Webhook** -- Rich embed alerts on detection or punishment
- **OneBot 11 (QQ Bot)** -- Send notifications to QQ groups via LLBot/go-cqhttp
- **Anti-Cheat Integration** -- Optional integration with GrimAC, Vulcan, or Matrix
- **Dynamic Thresholds** -- Auto-adjusts detection sensitivity based on TPS and player count
- **PlaceholderAPI Support** -- Placeholders for external plugins
- **Violation Whitelist** -- Trusted players: log only, no punishment
- **MySQL Storage** -- Violation records in MySQL/MariaDB
- **Detection Statistics** -- In-memory detection/punishment counters
- **Config Auto-Migration** -- Old config.yml auto-updated with new defaults
- **Multi-Language** -- Built-in lang files for zh_CN, en_US, zh_TW
- **Public API** -- AntiLitematicaAPI interface with DetectionEvent (cancellable) and PunishmentEvent


---

## Quick Start

```yaml
# 1. Install ProtocolLib and AntiLitematica in plugins/
# 2. Edit plugins/AntiLitematica/config.yml:
detection:
  enabled: true
  action: "KICK"
# 3. Run /al reload
```

Full installation guide: [wiki/INSTALL.md](wiki/INSTALL.md)

---

## How It Works

1. **Channel Detection** -- Monitors Litematica/Schematica plugin channel registration and payloads (servux:litematics, litematica:main, litematica:hello, litematica:place, etc.)
2. **ProtocolLib Signals** -- Intercepts abnormal hit vectors (EasyPlace), NBT queries, and Servux metadata fingerprints
3. **Anti-Printer Analysis** -- Every block placement analyzed for inhuman patterns: no raytrace, excessive speed, identical blocks, no camera movement
4. **Command Guard** -- Detects rapid command bursts used by schematic quick-paste. Allowlist exempts normal commands
5. **Punishment Execution** -- Warnings, kicks, temp bans, or permanent bans. Ban hooks: LiteBans, AdvancedBan, EssentialsX, Bukkit native

All checks respect antilitematica.bypass.

---

## Commands

| Command | Alias | Description |
|---------|-------|-------------|
| `/al reload` | | Reload configuration |
| `/al status` | | View plugin status |
| `/al reset <player\|all\|expired>` | | Reset violation records |
| `/al history <player> [page]` | | View violation history |
| `/al testnotify` | | Test Discord/OneBot notifications |
| `/al whitelist list\|add\|remove` | | Manage whitelist |
| `/al world list\|<world>` | | View per-world config |
| `/al gui` | `/al menu` | Open admin GUI panel |

Full reference: [wiki/COMMANDS.md](wiki/COMMANDS.md)

---

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| antilitematica.admin | OP | All /al commands |
| antilitematica.bypass | OP | Bypass all checks |
| antilitematica.notify | OP | Receive alerts |
| antilitematica.gui | OP | Open GUI panel |

---

## Documentation

| User Guide | Developer Guide |
|------------|-----------------|
| [Installation](wiki/INSTALL.md) | [API Reference](docs/API.md) |
| [Configuration Reference](wiki/CONFIG.md) | [Architecture Overview](docs/ARCHITECTURE.md) |
| [Commands & Permissions](wiki/COMMANDS.md) | [Contributing Guide](docs/CONTRIBUTING.md) |
| [FAQ](wiki/FAQ.md) | |
| [Changelog](CHANGELOG.md) | |

---

---

## API for Developers

```java
AntiLitematicaAPI api = AntiLitematicaAPI.getInstance();
int violations = api.getViolationCount(player.getUniqueId());
api.triggerDetection(player, "servux:litematics", "custom check");
```

Events: DetectionEvent (cancellable), PunishmentEvent (post-execution).

Full reference: [docs/API.md](docs/API.md)

---

## Configuration Overview

- `detection` -- Channel list, action type, ProtocolLib signals
- `anti_printer` -- Raytrace, reach, rate limits, pattern thresholds
- `command_guard` -- Block/allow commands, burst limits
- `world_whitelist` -- Worlds to skip detection
- `graduated_punishment` -- Escalation levels, storage (sqlite/mysql/memory)
- `integration` -- Anti-cheat adapter (grim/vulcan/matrix/none)
- `whitelist` -- Player exemption (LOG_ONLY / NORMAL)
- `discord` -- Webhook URL, embed, proxy
- `onebot` -- QQ Bot HTTP API configuration
- `dynamic_threshold` -- TPS/player-based sensitivity
- `detection_log` -- Dedicated log file
- `stats` -- Detection statistics and record retention
- `lang` -- Server default language
- `bstats` -- Metrics opt-out

Full reference: [wiki/CONFIG.md](wiki/CONFIG.md)

---

## Dependencies

- Required: [ProtocolLib](https://www.spigotmc.org/resources/protocollib/1997/)
- Optional: PlaceholderAPI, Geyser/Floodgate, LiteBans, AdvancedBan, EssentialsX, GrimAC, Vulcan, Matrix, MySQL Connector/J

---

## Building from Source

```bash
git clone https://github.com/ALingqing/AntiLitematica.git
cd AntiLitematica
mvn clean package
# Output: target/AntiLitematica-<version>.jar
```

Built with Java 21 and Maven 3. CI via GitHub Actions.

## Compatibility

- **Server:** Paper, Spigot, Purpur (1.12.2+)
- **Paper API:** 26.1.2 (Minecraft 1.21.4+)
- **Java:** 21+
- **Geyser/Floodgate:** Fully supported, Bedrock players auto-exempted
- **Ban Plugins:** LiteBans, AdvancedBan, EssentialsX
- **Anti-Cheat:** GrimAC, Vulcan, Matrix

---

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history.

---

## bStats

[![bStats](https://bstats.org/signatures/bukkit/AntiLitematica.svg)](https://bstats.org/plugin/bukkit/AntiLitematica/31012)

Anonymous usage statistics are collected. Disable in `config.yml`:

```yaml
bstats:
  enabled: false
```

---

## License

By using this plugin you agree to anonymous bStats metrics collection. You can disable this in the configuration.
