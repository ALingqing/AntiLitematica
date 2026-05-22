# AntiLitematica

**The ultimate Litematica / Schematica / Printer detection plugin for Paper/Spigot**

![Version](https://img.shields.io/badge/version-4.0.0-blue) ![MC Version](https://img.shields.io/badge/1.12.2%2B-green) [![bStats](https://bstats.org/signatures/bukkit/AntiLitematica.svg)](https://bstats.org/plugin/bukkit/AntiLitematica/31012)

---

## Overview

AntiLitematica is a powerful server-side plugin designed to detect and prevent the use of **Litematica**, **Schematica**, and their **Printer** features on your Minecraft server. It operates entirely server-side — no client mod installation required.

With deep ProtocolLib integration, graduated punishment system, and **Geyser (Bedrock) compatibility**, AntiLitematica provides robust protection while keeping false positives to a minimum.

---

## Features

- **Network Channel Detection** — Detects Litematica (via `servux:litematics`) and Schematica (via `schematica`) channel registration and payloads.
- **ProtocolLib Deep Inspection** — Catches EasyPlace protocol abuse, debug NBT queries, and custom payload fingerprints even when players disable Servux sync.
- **Anti-Printer Engine** — Multi-layered block placement analysis:
  - Enforced raytrace (players must look at blocks they place)
  - Placement rate limiting
  - Consecutive same-type block detection
  - No look-change detection (bots don't micro-adjust aim)
- **Command Guard** — Blocks rapid command execution and `/setblock` spam commonly used by schematic quick-paste.
- **Graduated Punishment System** — Escalating penalties: Warn → TempBan → Ban. Supports LiteBans, AdvancedBan, and EssentialsX.
- **Geyser Compatible** — Automatically exempts Bedrock players from all checks to prevent false positives from protocol translation.
- **Discord Webhook** — Send rich embed alerts to your staff Discord when detections or punishments occur.
- **Anti-Cheat Integration** — Optional GrimAC integration to feed violations into your existing anti-cheat.
- **Dynamic Thresholds** — Auto-adjusts detection sensitivity based on server TPS and online player count.
- **In-Game GUI Config** — Manage all settings through an intuitive inventory GUI with live reload.
- **PlaceholderAPI Support** — Exposes placeholders for external plugins.
- **Auto Update Checker** — Automatically checks for new versions on GitHub and notifies admins on join. Manual check via `/al update`.
- **Violation Whitelist** — Whitelist trusted players to only log detections without punishment. Manage via `/al whitelist`.
- **Web Dashboard** — Built-in web-based monitoring panel with real-time stats, player list, violation records, and quick settings. Supports **简体中文**, **English**, and **繁體中文**.

---

## Violation Whitelist

The whitelist feature allows you to exempt specific players from punishment while still logging their detections.

```yaml
whitelist:
  enabled: true
  mode: "LOG_ONLY"      # LOG_ONLY: log only, no punishment
  players:
    - "trusted_builder"
    - "admin_player"
```

When a whitelisted player triggers a detection:
- The detection is logged to console
- No kick/ban/warn is executed
- Staff are not spammed with false alarms

Manage the whitelist in-game:
```
/al whitelist list              — List all whitelisted players
/al whitelist add <player>      — Add a player
/al whitelist remove <player>   — Remove a player
```

---

## How It Works

1. When a player joins, the plugin monitors registered plugin channels. If `servux:litematics` or `schematica` is detected, action is taken immediately.
2. ProtocolLib intercepts suspicious packets (abnormal hit vectors, NBT queries) that are strong indicators of Litematica's EasyPlace or Printer.
3. The Anti-Printer engine analyzes every block placement for inhuman patterns (too fast, no raytrace, no camera movement).
4. All checks respect the `antilitematica.bypass` permission and skip Geyser Bedrock players automatically.

---

## Commands

```
/antilitematica reload                 — Reload configuration
/antilitematica gui                    — Open configuration GUI
/antilitematica status                 — View current settings
/antilitematica reset <player>         — Reset violation record
/antilitematica history <player>       — View violation history
/antilitematica update                 — Check for updates
/antilitematica whitelist list         — List whitelisted players
/antilitematica whitelist add <player> — Add player to whitelist
/antilitematica whitelist remove <player> — Remove player from whitelist
```

Alias: `/al`

---

## Permissions

```
antilitematica.admin    — Access to all commands
antilitematica.bypass   — Bypass all detection checks
antilitematica.notify   — Receive staff alert messages
```

---

## Dependencies

- **Required:** [ProtocolLib](https://www.spigotmc.org/resources/protocollib/1997/)
- **Soft-Depends:** PlaceholderAPI, Geyser, LiteBans, AdvancedBan, EssentialsX

---

## Configuration

All settings are stored in `config.yml` and `messages.yml`. Key sections:

- `detection` — Channel list, action type (LOG/KICK/BAN/COMMANDS), ProtocolLib signals
- `anti_printer` — Raytrace, rate limits, pattern detection thresholds
- `command_guard` — Blocked commands and burst limits
- `graduated_punishment` — Escalation levels and durations
- `geyser_compat` — Enable/disable Bedrock player exemption
- `discord` — Webhook URL and notification settings
- `locale` — Message language (`zh_CN` / `en_US` / `zh_TW`)
- `whitelist` — Violation whitelist (player names, LOG_ONLY mode)
- `web_dashboard` — Built-in web monitoring panel (port, password, locale)

---

## Web Dashboard

AntiLitematica includes a built-in web-based monitoring dashboard — no additional web server required.

### Access

1. Enable the dashboard in `config.yml`:
   ```yaml
   web_dashboard:
     enabled: true
     port: 25418
     password: "your_password"
     locale: "zh_CN"   # zh_CN / en_US / zh_TW
   ```
2. Restart or `/al reload`
3. Open `http://<your-server-ip>:25418` in your browser
4. Enter the password configured above

### Dashboard Features

- **📊 Overview** — Server TPS, online players, detection/punishment counts, plugin status
- **👤 Players** — Real-time online player list with ping, gamemode, violation count; reset player violations
- **⚠️ Violations** — View and clear violation records
- **⚙️ Quick Settings** — Toggle detection, anti-printer, and webhook on/off
- **🌐 Multi-Language** — Switch between 简体中文, English, and 繁體中文

---

## Compatibility

- **Server software:** Paper, Spigot, Purpur (1.12.2+)
- **Java:** 8+
- **Geyser/Floodgate:** Fully supported, Bedrock players auto-exempted

---

## Support

For bug reports, feature requests, or general support, please use the discussion section below.

> By using this plugin you agree to anonymous bStats metrics collection. You can disable this in the configuration.
