# AntiLitematica API Documentation

> **Version:** 6.0.0+  
> **Package:** `top.chenray.antilitematica.api`

Other plugins can integrate with AntiLitematica through the official API to query player states, trigger detections, manage whitelists, and listen to events.

---

## Getting the API Instance

### Method 1: Static Singleton (Recommended)

```java
import top.chenray.antilitematica.api.AntiLitematicaAPI;

AntiLitematicaAPI api = AntiLitematicaAPI.getInstance();
if (api != null) {
    // API is available
}
```

### Method 2: Bukkit Services Manager

```java
import org.bukkit.plugin.RegisteredServiceProvider;
import top.chenray.antilitematica.api.AntiLitematicaAPI;

RegisteredServiceProvider<AntiLitematicaAPI> provider =
    Bukkit.getServicesManager().getRegistration(AntiLitematicaAPI.class);
if (provider != null) {
    AntiLitematicaAPI api = provider.getProvider();
}
```

### Dependency Setup

Add AntiLitematica as a dependency in your `plugin.yml`:

```yaml
softdepend: [AntiLitematica]
```

Then shade or compile against the plugin JAR.

---

## API Reference

### Plugin Status

| Method | Return | Description |
|--------|--------|-------------|
| `isPluginEnabled()` | `boolean` | Whether AntiLitematica is globally enabled |
| `isDetectionEnabled()` | `boolean` | Whether detection system is active |
| `isAntiPrinterEnabled()` | `boolean` | Whether anti-printer is active |
| `isCommandGuardEnabled()` | `boolean` | Whether command guard is active |
| `isGraduatedPunishmentEnabled()` | `boolean` | Whether graduated punishment is enabled |
| `getPluginVersion()` | `String` | Current plugin version string |

### Player State

| Method | Return | Description |
|--------|--------|-------------|
| `isPlayerPunished(UUID)` | `boolean` | Whether the player is currently marked as punished |
| `isPlayerWhitelisted(UUID)` | `boolean` | Whether the player is on the violation whitelist |
| `isPlayerWhitelisted(String)` | `boolean` | Same, by player name |
| `markPlayerPunished(UUID)` | `boolean` | Manually flag a player as punished |
| `unmarkPlayerPunished(UUID)` | `void` | Remove player from punished set |

### Violation Records

| Method | Return | Description |
|--------|--------|-------------|
| `getViolationRecord(UUID)` | `ViolationRecord` | Get full record, or `null` if none |
| `getViolationCount(UUID)` | `int` | Current window violation count |
| `getAllViolationRecords()` | `List<ViolationRecord>` | All players with records |
| `resetViolationRecord(UUID)` | `boolean` | Reset a player's record |

### Whitelist Management

| Method | Return | Description |
|--------|--------|-------------|
| `getWhitelistedPlayers()` | `List<String>` | Player names on whitelist |
| `addToWhitelist(String)` | `boolean` | Add a player by name |
| `removeFromWhitelist(String)` | `boolean` | Remove a player by name |
| `getWhitelistMode()` | `String` | `LOG_ONLY` or `NORMAL` or `NONE` |

### Detection Triggers

| Method | Description |
|--------|-------------|
| `triggerDetection(Player, String channel, String reason)` | Simulate a channel detection and apply punishment |
| `triggerPrinterDetection(Player, String reason)` | Simulate an anti-printer detection |

### Anti-Cheat Integration

| Method | Description |
|--------|-------------|
| `flagAntiCheat(Player, String checkName, int vl, String details)` | Flag player in GrimAC or other integrated anti-cheat |

### Configuration

| Method | Return | Description |
|--------|--------|-------------|
| `reloadConfig()` | `void` | Reload config from disk |
| `getDetectionAction()` | `String` | `LOG`, `KICK`, `BAN`, or `COMMANDS` |
| `getMonitoredChannels()` | `List<String>` | Plugin channels being monitored |
| `getPunishmentReason()` | `String` | Configured punishment reason |

### Auto-Update

| Method | Return | Description |
|--------|--------|-------------|
| `isAutoUpdateEnabled()` | `boolean` | Whether auto-update is configured |
| `triggerAutoUpdate()` | `CompletableFuture<Boolean>` | Trigger download of latest release from GitHub |

### Detection Log

| Method | Return | Description |
|--------|--------|-------------|
| `isDetectionLogEnabled()` | `boolean` | Whether detection log file is enabled |

---

### Storage Type

| Method | Return | Description |
|--------|--------|-------------|
| `getStorageType()` | `String` | `sqlite`, `mysql`, or `memory` |

---

## Events

AntiLitematica fires custom Bukkit events that other plugins can listen to.

### DetectionEvent

Fired when a player is detected using Litematica/Schematica/Printer. **Cancellable** — if cancelled, no punishment is applied.

```java
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import top.chenray.antilitematica.api.event.DetectionEvent;

public class MyListener implements Listener {
    @EventHandler
    public void onDetection(DetectionEvent event) {
        // Access player
        event.getPlayer();

        // Detection details
        String channel = event.getChannel();    // e.g. "servux:litematics"
        String reason = event.getReason();       // Human-readable reason
        DetectionEvent.DetectionType type = event.getDetectionType();

        // Cancel punishment for specific players
        if (event.getPlayer().hasPermission("myplugin.bypass")) {
            event.setCancelled(true);
        }
    }
}
```

#### DetectionType enum

| Constant | Description |
|----------|-------------|
| `CHANNEL` | Plugin channel registration/payload |
| `PRINTER` | Anti-printer block placement analysis |
| `SIGNAL` | ProtocolLib signal (EasyPlace, NBT query, etc.) |
| `COMMAND_GUARD` | Rapid command execution |
| `API` | Triggered manually via API |

### PunishmentEvent

Fired **after** a punishment has been executed. Not cancellable — use `DetectionEvent` to prevent punishment.

```java
import top.chenray.antilitematica.api.event.PunishmentEvent;

@EventHandler
public void onPunishment(PunishmentEvent event) {
    event.getPlayer();
    event.getAction();         // PunishmentAction enum
    event.getReason();         // Punishment reason message
    event.getViolationCount(); // Current violation count
    event.getPunishmentType(); // "graduated" or "legacy"
    event.getChannel();        // Related channel, may be null
}
```

#### PunishmentAction enum

| Constant | Description |
|----------|-------------|
| `LOG` | Detection logged only |
| `KICK` | Player was kicked |
| `WARN` | Player received a warning |
| `TEMPBAN` | Player was temporarily banned |
| `BAN` | Player was permanently banned |
| `COMMANDS` | Console commands were executed |

---

## Example: Integration Plugin

```java
package my.plugin;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import top.chenray.antilitematica.api.AntiLitematicaAPI;
import top.chenray.antilitematica.api.event.DetectionEvent;

public class MyIntegration extends JavaPlugin implements Listener {

    private AntiLitematicaAPI antiLitematica;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onLoad() {
        antiLitematica = AntiLitematicaAPI.getInstance();
    }

    @EventHandler
    public void onDetection(DetectionEvent event) {
        if (event.getPlayer().hasPermission("myplugin.trusted")) {
            event.setCancelled(true);
            getLogger().info("Cancelled detection for trusted player: "
                    + event.getPlayer().getName());
        }
    }

    public void checkPlayer(org.bukkit.entity.Player player) {
        if (antiLitematica != null) {
            int violations = antiLitematica.getViolationCount(player.getUniqueId());
            getLogger().info(player.getName() + " has " + violations + " violations");

            if (antiLitematica.isPlayerWhitelisted(player.getUniqueId())) {
                getLogger().info(player.getName() + " is whitelisted");
            }
        }
    }
}
```

---

## PlaceholderAPI Placeholders

| Placeholder | Description |
|-------------|-------------|
| `%antilitematica_version%` | Plugin version |
| `%antilitematica_detections_<player>%` | Detection count |
| `%antilitematica_whitelist_<player>%` | Whether player is whitelisted |

---

## bStats

Plugin ID: `31012`  
View metrics: [https://bstats.org/plugin/bukkit/AntiLitematica/31012](https://bstats.org/plugin/bukkit/AntiLitematica/31012)

---

## Changelog (API)

### v5.0.0
- Initial public API release
- `AntiLitematicaAPI` interface + static singleton
- `DetectionEvent` (cancellable) + `PunishmentEvent` (post-execution)
- Bukkit ServicesManager registration

### v5.0.0+ (this version)
- Added `isGraduatedPunishmentEnabled()`
- Added `isDetectionLogEnabled()`
- Added `getStorageType()`
- Added `isCommandGuardEnabled()`
- Added StatsTracker for daily detection/punishment statistics
- Added AuditLogger for admin action logging
- Added world whitelist support for per-world detection skipping
- Added command guard allowlist (allowed_commands)
- Added SSE real-time push to Web Dashboard
- Added Prometheus metrics endpoint (/api/metrics)
- Added batch kick command (/al kickall)
- Added violation ranking API
