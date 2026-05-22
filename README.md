# AntiLitematica

**The ultimate Litematica / Schematica / Printer detection plugin for Paper/Spigot**

![Version](https://img.shields.io/badge/version-5.0.0-blue) ![MC Version](https://img.shields.io/badge/1.12.2%2B-green) [![bStats](https://bstats.org/signatures/bukkit/AntiLitematica.svg)](https://bstats.org/plugin/bukkit/AntiLitematica/31012)

---

## Overview

AntiLitematica is a powerful server-side plugin designed to detect and prevent the use of **Litematica**, **Schematica**, and their **Printer** features on your Minecraft server. Operates entirely server-side — no client mod installation required.

With deep ProtocolLib integration, graduated punishment system, **Geyser (Bedrock) compatibility**, and a built-in **Web Dashboard**, AntiLitematica provides robust protection while keeping false positives to a minimum.

---

## Features

- **🔍 Network Channel Detection** — Detects Litematica (`servux:litematics`) and Schematica channel registration
- **🛡️ ProtocolLib Deep Inspection** — EasyPlace abuse, NBT queries, Servux metadata fingerprints
- **🚫 Anti-Printer Engine** — Raytrace enforcement, rate limiting, same-type detection, no-look-change detection
- **📋 Command Guard** — Blocks `/setblock` spam and rapid command execution
- **📈 Graduated Punishment** — Escalating penalties: Warn → TempBan → Ban (LiteBans / AdvancedBan / EssentialsX)
- **🌐 Geyser Compatible** — Bedrock players auto-exempted from checks
- **💬 Discord Webhook** — Rich embed alerts on detection and punishment
- **🔗 Anti-Cheat Integration** — Optional GrimAC integration
- **📊 Dynamic Thresholds** — Auto-adjusts sensitivity based on TPS and player count
- **🎮 In-Game GUI Config** — Manage settings through an intuitive inventory GUI
- **🔌 PlaceholderAPI Support** — Placeholders for external plugins
- **📡 Auto Update Checker** — GitHub release notifications on join
- **✅ Violation Whitelist** — Trusted players: log only, no punishment
- **🌐 Web Dashboard** — Built-in monitoring panel (no extra dependencies) with zh_CN / en_US / zh_TW
- **⬇️ Auto GitHub Download** — Nightly or command-triggered release downloads
- **📦 Public API** — Events, queries, and integration for other plugins

---

## Quick Start

```yaml
# 1. Install ProtocolLib and AntiLitematica
# 2. Edit plugins/AntiLitematica/config.yml:

detection:
  enabled: true
  action: "KICK"

# 3. Run /al reload
```

For full installation instructions, see [wiki/INSTALL.md](wiki/INSTALL.md).

---

## Commands

| Command | Alias | Description |
|---------|-------|-------------|
| `/antilitematica reload` | `/al reload` | Reload configuration |
| `/antilitematica gui` | `/al gui` | Open configuration GUI |
| `/antilitematica status` | `/al status` | View plugin status |
| `/antilitematica update` | `/al update` | Check for updates |
| `/antilitematica build` | `/al build` | Download latest release |
| `/antilitematica reset <player>` | `/al reset` | Reset violation record |
| `/antilitematica history <player>` | `/al history` | View violation history |
| `/antilitematica whitelist ...` | `/al whitelist` | Manage whitelist |

Full reference: [wiki/COMMANDS.md](wiki/COMMANDS.md)

---

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `antilitematica.admin` | OP | All commands |
| `antilitematica.bypass` | OP | Bypass all checks |
| `antilitematica.notify` | OP | Receive alerts |

---

## Documentation

| 📘 **User Guide** | 📗 **Developer Guide** |
|-------------------|------------------------|
| [Installation](wiki/INSTALL.md) | [API Reference](docs/API.md) |
| [Configuration](wiki/CONFIG.md) | [Architecture](docs/ARCHITECTURE.md) |
| [Commands & Permissions](wiki/COMMANDS.md) | [Contributing](docs/CONTRIBUTING.md) |
| [Web Dashboard](wiki/WEB_DASHBOARD.md) | |
| [FAQ](wiki/FAQ.md) | |

---

## Dependencies

- **Required:** [ProtocolLib](https://www.spigotmc.org/resources/protocollib/1997/)
- **Optional:** PlaceholderAPI, Geyser, LiteBans, AdvancedBan, EssentialsX

---

## How It Works

1. **Channel Detection** — Monitors plugin channel registration. `servux:litematics` → immediate action.
2. **ProtocolLib Signals** — Intercepts abnormal hit vectors (EasyPlace), NBT queries, and Servux metadata.
3. **Anti-Printer** — Analyzes every block placement: raytrace, rate, same-type streaks, camera movement.
4. **Command Guard** — Detects rapid command bursts used by schematic quick-paste.
5. **Punishment** — Graduated system escalates from warn → tempban → ban. Supports LiteBans, AdvancedBan, EssentialsX.

All checks respect `antilitematica.bypass` and auto-exempt Geyser Bedrock players.

---

## Web Dashboard

Built-in monitoring panel — no extra web server needed.

```yaml
web_dashboard:
  enabled: true
  port: 25418
  password: "your_password"
```

Open `http://<your-server-ip>:25418` in your browser.

Features: server overview, player list, violation records, quick settings, multi-language.

Full guide: [wiki/WEB_DASHBOARD.md](wiki/WEB_DASHBOARD.md)

---

## API for Developers

AntiLitematica provides a public API for other plugins:

```java
AntiLitematicaAPI api = AntiLitematicaAPI.getInstance();
int violations = api.getViolationCount(player.getUniqueId());
api.triggerDetection(player, "servux:litematics", "custom check");
```

Events: `DetectionEvent` (cancellable), `PunishmentEvent`.

Full reference: [docs/API.md](docs/API.md)

---

## Building from Source

```bash
git clone https://github.com/ALingqing/AntiLitematica.git
cd AntiLitematica
mvn clean package
# Output: target/AntiLitematica-<version>.jar
```

---

## Compatibility

- **Server:** Paper, Spigot, Purpur (1.12.2+)
- **Java:** 8+ (21+ recommended)
- **Geyser/Floodgate:** Fully supported

---

## bStats

[![bStats](https://bstats.org/signatures/bukkit/AntiLitematica.svg)](https://bstats.org/plugin/bukkit/AntiLitematica/31012)

Anonymous usage statistics. Disable in `config.yml`:
```yaml
bstats:
  enabled: false
```

---

## License

> By using this plugin you agree to anonymous bStats metrics collection. You can disable this in the configuration.
