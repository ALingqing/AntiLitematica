# AntiLitematica

**The ultimate Litematica / Schematica / Printer detection plugin for Paper/Spigot**

![Version](https://img.shields.io/badge/version-5.0.0-blue) ![MC Version](https://img.shields.io/badge/1.12.2%2B-green)
[![Build](https://github.com/ALingqing/AntiLitematica/actions/workflows/maven.yml/badge.svg)](https://github.com/ALingqing/AntiLitematica/actions/workflows/maven.yml)
[![bStats](https://bstats.org/signatures/bukkit/AntiLitematica.svg)](https://bstats.org/plugin/bukkit/AntiLitematica/31012)

---

## Overview

AntiLitematica is a server-side plugin that detects and prevents the use of **Litematica**, **Schematica**, and their **Printer** features on your Minecraft server. No client-side mod installation required.

Built with ProtocolLib deep inspection, a graduated punishment system, Geyser (Bedrock) compatibility, and a built-in web dashboard, AntiLitematica provides robust protection while minimizing false positives.

---

## Features

- **Network Channel Detection** — Monitors `servux:litematics` and Schematica plugin channel registration and payloads
- **ProtocolLib Deep Inspection** — Detects EasyPlace protocol abuse, NBT debug queries, and Servux metadata fingerprints even when players disable sync
- **Anti-Printer Engine** — Multi-layered block placement analysis: enforced raytrace, rate limiting, consecutive same-type detection, no-look-change detection
- **Command Guard** — Blocks rapid command bursts and `/setblock` spam used by schematic quick-paste
- **Graduated Punishment** — Escalating penalties: Warn, TempBan, Ban. Supports LiteBans, AdvancedBan, and EssentialsX
- **Geyser Compatible** — Bedrock players auto-exempted from all checks
- **Discord Webhook** — Rich embed alerts sent to your staff Discord on detection or punishment
- **Anti-Cheat Integration** — Optional integration with GrimAC, Vulcan, or Matrix
- **Dynamic Thresholds** — Automatically adjusts detection sensitivity based on server TPS and online player count
- **In-Game GUI** — Manage all settings through an intuitive inventory interface with live reload
- **PlaceholderAPI Support** — Exposes placeholders for external plugins
- **Violation Whitelist** — Trusted players trigger only logging, no punishment
- **Web Dashboard** — Built-in monitoring panel (JDK HttpServer, no extra dependencies) with zh_CN / en_US / zh_TW
- **Auto Update Checker** — Notifies admins on join when a new GitHub release is available
- **Auto GitHub Download** — Downloads latest release JAR automatically (nightly schedule or `/al build`). Auto-detects plugins folder path.
- **MySQL Storage** — Violation records can be stored in MySQL/MariaDB (configurable host, port, database, credentials)
- **Dedicated Detection Log** — Separate `detections.log` with daily rotation for audit purposes
- **Config Auto-Migration** — Old `config.yml` files are automatically updated with new default sections while preserving existing settings
- **Public API** — `AntiLitematicaAPI` interface with Bukkit events (`DetectionEvent`, `PunishmentEvent`) for third-party plugin integration

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

1. **Channel Detection** — When a player joins, the plugin monitors plugin channel registration. If `servux:litematics` is detected, action is taken immediately.
2. **ProtocolLib Signals** — Suspicious packets (abnormal hit vectors, NBT queries, Servux metadata requests) are intercepted as strong indicators of Litematica activity.
3. **Anti-Printer Analysis** — Every block placement is analyzed for inhuman patterns: no raytrace hit, excessive speed, consecutive identical blocks, no camera movement.
4. **Command Guard** — Rapid command execution patterns (e.g., `/setblock` bursts) are detected and blocked.
5. **Punishment Execution** — Warnings, kicks, temporary bans, or permanent bans are applied. Ban plugin hooks attempt LiteBans → AdvancedBan → EssentialsX → Bukkit native in order.

All checks respect the `antilitematica.bypass` permission and automatically skip Geyser Bedrock players.

---

## Commands

| Command | Alias | Description |
|---------|-------|-------------|
| `/antilitematica reload` | `/al reload` | Reload configuration from disk |
| `/antilitematica gui` | `/al gui` | Open configuration GUI (in-game only) |
| `/antilitematica status` | `/al status` | View current plugin settings |
| `/antilitematica update` | `/al update` | Check GitHub for new releases |
| `/antilitematica build` | `/al build` | Download latest release JAR from GitHub |
| `/antilitematica reset <player>` | `/al reset` | Reset a player's violation record |
| `/antilitematica history <player>` | `/al history` | View a player's violation history |
| `/antilitematica whitelist ...` | `/al whitelist` | Manage violation whitelist |

Full reference: [wiki/COMMANDS.md](wiki/COMMANDS.md)

---

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `antilitematica.admin` | OP | Access to all `/al` commands |
| `antilitematica.bypass` | OP | Bypass all detection checks entirely |
| `antilitematica.notify` | OP | Receive staff alert messages |

---

## Documentation

| User Guide | Developer Guide |
|------------|-----------------|
| [Installation](wiki/INSTALL.md) | [API Reference](docs/API.md) |
| [Configuration Reference](wiki/CONFIG.md) | [Architecture Overview](docs/ARCHITECTURE.md) |
| [Commands & Permissions](wiki/COMMANDS.md) | [Contributing Guide](docs/CONTRIBUTING.md) |
| [Web Dashboard Guide](wiki/WEB_DASHBOARD.md) | |
| [FAQ](wiki/FAQ.md) | |
| [Changelog](CHANGELOG.md) | |

---

## Web Dashboard

AntiLitematica includes a built-in web-based monitoring dashboard. No additional web server required.

```yaml
web_dashboard:
  enabled: true
  port: 25418
  password: "your_password"
  locale: "zh_CN"    # zh_CN / en_US / zh_TW
```

Access at `http://<your-server-ip>:25418`

Features: server overview (TPS, online players), real-time player list with violation counts, violation records viewer, quick settings toggles, multi-language UI.

Full guide: [wiki/WEB_DASHBOARD.md](wiki/WEB_DASHBOARD.md)

---

## API for Developers

AntiLitematica exposes a public API for other plugins:

```java
// Obtain the API instance
AntiLitematicaAPI api = AntiLitematicaAPI.getInstance();

// Query player state
int violations = api.getViolationCount(player.getUniqueId());
boolean whitelisted = api.isPlayerWhitelisted(player.getName());

// Trigger detections programmatically
api.triggerDetection(player, "servux:litematics", "custom check");

// Listen to events
@EventHandler
public void onDetection(DetectionEvent event) {
    if (event.getPlayer().hasPermission("myplugin.exempt")) {
        event.setCancelled(true); // Prevent punishment
    }
}
```

Available events: `DetectionEvent` (cancellable), `PunishmentEvent` (post-execution).

Full reference: [docs/API.md](docs/API.md)

---

## Configuration

All settings are stored in `plugins/AntiLitematica/config.yml` and `messages.yml`. Key sections:

- `detection` — Channel list, action type (LOG/KICK/BAN/COMMANDS), ProtocolLib signal toggles
- `anti_printer` — Raytrace enforcement, reach distance, rate limits, pattern detection thresholds
- `command_guard` — Blocked commands, burst limits
- `graduated_punishment` — Escalation levels, durations, storage type (sqlite/mysql/memory), MySQL credentials
- `discord` — Webhook URL, embed customization, proxy support
- `integration` — Anti-cheat adapter (grim/vulcan/matrix/none)
- `dynamic_threshold` — TPS and player-count based sensitivity adjustment
- `whitelist` — Player exemption list (LOG_ONLY / NORMAL mode)
- `web_dashboard` — Port, password, language
- `auto_build` — GitHub auto-download settings (plugins folder auto-detection, nightly time, post-download command)
- `detection_log` — Dedicated log file with daily rotation

Full reference: [wiki/CONFIG.md](wiki/CONFIG.md)

---

## Dependencies

- **Required:** [ProtocolLib](https://www.spigotmc.org/resources/protocollib/1997/)
- **Optional:** PlaceholderAPI, Geyser/Floodgate, LiteBans, AdvancedBan, EssentialsX, GrimAC, Vulcan, Matrix, MySQL Connector/J

---

## Building from Source

```bash
git clone https://github.com/ALingqing/AntiLitematica.git
cd AntiLitematica
mvn clean package
# Output: target/AntiLitematica-<version>.jar
```

The project is built with Java 21 and Maven. CI builds are automatically run on push via GitHub Actions.

---

## Compatibility

- **Server:** Paper, Spigot, Purpur (1.12.2+)
- **Java:** 8+ (21+ recommended)
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
