# Installation Guide

---

## Requirements

| Requirement | Version |
|-------------|---------|
| **Server** | Paper, Spigot, or Purpur 1.12.2+ |
| **Java** | 8+ (21+ recommended) |
| **ProtocolLib** | Required ([download](https://www.spigotmc.org/resources/protocollib/1997/)) |
| **PlaceholderAPI** | Optional ([download](https://www.spigotmc.org/resources/placeholderapi.6245/)) |
| **LiteBans / AdvancedBan / EssentialsX** | Optional (for graduated punishment) |

---

## Installation Steps

### 1. Download

Download the latest `AntiLitematica-{version}.jar` from the [Releases page](https://github.com/ALingqing/AntiLitematica/releases).

### 2. Install ProtocolLib

AntiLitematica requires ProtocolLib. Download it from [SpigotMC](https://www.spigotmc.org/resources/protocollib/1997/) and place it in your `plugins/` folder.

### 3. Install AntiLitematica

1. Stop your server
2. Place `AntiLitematica-{version}.jar` in your `plugins/` folder
3. Start your server
4. The plugin will generate default `config.yml` and `messages.yml` in `plugins/AntiLitematica/`

### 4. Configure

Edit `plugins/AntiLitematica/config.yml` to suit your server. At minimum:

```yaml
enabled: true
detection:
  enabled: true
  action: "KICK"    # LOG / KICK / BAN / COMMANDS
```

Run `/al reload` to apply changes without restarting.

### 5. Verify Installation

```
/al status
```

You should see output showing the plugin is enabled with your configured settings.

---

## Quick Start

### Basic Protection (Kick on Detection)

```yaml
# config.yml
detection:
  enabled: true
  action: "KICK"
  reason: "Forbidden client mod detected."

anti_printer:
  enabled: true

command_guard:
  enabled: true
```

### Graduated Punishment (Recommended)

```yaml
# config.yml
detection:
  enabled: true
  action: "KICK"   # Fallback, graduated takes priority

graduated_punishment:
  enabled: true
  window_minutes: 60
  storage: sqlite
  levels:
    1:
      action: warn
      reason: "警告 - 请勿使用投影模组 (1/3)"
    2:
      action: tempban
      duration: "30m"
      reason: "多次使用投影模组 - 临时封禁 (2/3)"
    3:
      action: ban
      reason: "屡次使用投影模组 - 永久封禁 (3/3)"
```

---

## Upgrading

### Manual

1. Download the new JAR from [Releases](https://github.com/ALingqing/AntiLitematica/releases)
2. Replace the old JAR in `plugins/`
3. Run `/plugman reload AntiLitematica` or restart the server

---

## Compatibility Notes

### Geyser/Floodgate (Bedrock Players)

AntiLitematica automatically detects Geyser via Floodgate API and exempts Bedrock players from all checks. No configuration needed.

### Anti-Cheat Plugins

GrimAC integration is supported. Configure:

```yaml
integration:
  enabled: true
  type: "grim"
  violation_level: 10
```

### Ban Plugins

For graduated punishment, the plugin tries ban plugins in this order:
1. LiteBans
2. AdvancedBan
3. EssentialsX
4. Bukkit native ban (fallback)

---

## Troubleshooting

### Plugin doesn't load

- Ensure ProtocolLib is installed and up-to-date
- Check server logs for compatibility errors
- Verify Java version (8+ required)

### No detections happening

- Run `/al status` to verify detection is enabled
- Check `detection.channels` in config.yml includes `servux:litematics`
- Ensure players don't have `antilitematica.bypass` permission

### False positives

- Enable `dynamic_threshold` to auto-adjust sensitivity
- Use whitelist for trusted builders
- Adjust `anti_printer` tolerances (reach, rate, etc.)
