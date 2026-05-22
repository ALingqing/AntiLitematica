# Architecture

> **Last updated:** v5.0.0

AntiLitematica is a Bukkit/Paper plugin for detecting and preventing Litematica/Schematica usage on Minecraft servers. This document describes the high-level architecture and module interactions.

---

## Module Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     AntiLitematicaPlugin                      │
│                    (Main entry point)                         │
├──────────┬──────────┬──────────┬──────────┬──────────────────┤
│ Detection│  Guards  │Punishment│Integration│   Web/Utility   │
├──────────┼──────────┼──────────┼──────────┼──────────────────┤
│ModChannel│Placement │Punisher  │GrimAC    │  DashboardServer │
│Detector  │Guard     │          │Vulcan    │                  │
│          │          │Graduated │Matrix    │  DiscordWebhook  │
│Protocol  │Command   │Punisher  │NoOp      │                  │
│LibBridge │Guard     │          │          │  UpdateChecker   │
│          │          │Punishment│          │                  │
│          │          │Tracker   │          │  AutoBuildManager│
│          │          │          │          │                  │
│          │          │          │          │  DetectionLogger │
│          │          │          │          │  ConfigMigrator  │
│          │          │          │          │  StatsTracker    │
│          │          │          │          │  AuditLogger     │
└──────────┴──────────┴──────────┴──────────┴──────────────────┘
         │            │          │                    │
         ▼            ▼          ▼                    ▼
     Bukkit Events   Scheduler   SQLite/MySQL/   HTTP Server
     ProtocolLib     API         Memory           GitHub API
```

---

## Core Modules

### 1. Detection Layer

#### `ModChannelDetector`
- **Package:** `top.chenray.antilitematica.detection`
- **Purpose:** Listens for plugin channel registration and incoming payloads
- **Mechanism:** Bukkit `PlayerRegisterChannelEvent` + `PluginMessageListener`
- **Monitored Channels:** Configurable via `config.yml` → `detection.channels`
- **Servux Blocking:** Actively blocks incoming `servux:litematics` payloads to prevent projection data sync

#### `ProtocolLibBridge`
- **Package:** `top.chenray.antilitematica.protocol`
- **Purpose:** Deep packet inspection for Litematica fingerprints
- **Mechanism:** ProtocolLib packet interceptors
- **Signals:**
  - **Servux Metadata:** Catches NBT queries containing "litematica" version strings
  - **EasyPlace:** Detects abnormal hit-vector values (hitVec relative to blockPos exceeds normal range)
  - **NBT Query:** Detects debug NBT queries (block entity/entity tag queries)

### 2. Guard Layer

#### `PlacementGuard`
- **Package:** `top.chenray.antilitematica.guard`
- **Purpose:** Multi-layered block placement analysis
- **Checks:**
  - **Enforced Raytrace:** Players must actually look at the block they're placing
  - **Rate Limiting:** Token-bucket based max blocks/second
  - **Consecutive Same-Type:** Detects bots placing many identical blocks
  - **No Look Change:** Detects placements without camera movement
- **Violation Tracking:** Uses `ViolationWindow` with configurable time window
- **Dynamic Thresholds:** Thresholds adjusted by `DynamicThresholdManager` based on TPS/player count

#### `CommandGuard`
- **Package:** `top.chenray.antilitematica.guard`
- **Purpose:** Blocks rapid command execution abuse (e.g., `/setblock` spam from schematic quick-paste)
- **Mechanism:** Monitors command frequency within configurable time window

### 3. Punishment Layer

#### `Punisher` (Legacy)
- **Package:** `top.chenray.antilitematica.punish`
- **Purpose:** Single-action punishment (LOG/KICK/BAN/COMMANDS)
- **Actions:**
  - `LOG` — Console logging only
  - `KICK` — Kick player with custom message
  - `BAN` — Bukkit name ban + kick
  - `COMMANDS` — Execute console commands, optionally kick afterwards

#### `GraduatedPunisher` (Recommended)
- **Package:** `top.chenray.antilitematica.punish`
- **Purpose:** Escalating penalties based on violation count
- **Flow:** Violation → Record → Level Lookup → Action (Warn → TempBan → Ban)
- **Ban Plugin Hooks:**
  - `LiteBansHook` — LiteBans integration
  - `AdvancedBanHook` — AdvancedBan integration
  - `EssentialsHook` — EssentialsX integration
  - `NoOpBanHook` — Bukkit native ban fallback

#### `PunishmentTracker`
- **Package:** `top.chenray.antilitematica.punish`
- **Purpose:** Stores violation records
- **Storage:** SQLite (file), MySQL/MariaDB, or in-memory
- **Data:** UUID, player name, count, first/last violation timestamps, total violations
- **MySQL Config:** Host, port, database, user, password in `graduated_punishment.mysql`
- **Cleanup:** Automatic cleanup of expired records every 24 hours

### 4. Integration Layer

#### `IntegrationManager`
- **Package:** `top.chenray.antilitematica.integration`
- **Purpose:** Bridges AntiLitematica detections to anti-cheat plugins
- **Adapters:**
  - `GrimACIntegration` — Feeds violations into GrimAC
  - `VulcanIntegration` — Reflection-based Vulcan integration
  - `MatrixIntegration` — Reflection-based Matrix integration
  - `NoOpIntegration` — No-op fallback

### 5. API Layer

#### `AntiLitematicaAPI`
- **Package:** `top.chenray.antilitematica.api`
- **Registration:** Bukkit ServicesManager + static singleton
- **Events:**
  - `DetectionEvent` — Cancellable, fired before punishment
  - `PunishmentEvent` — Fired after punishment execution

### 6. Web & Utility

#### `DashboardServer`
- **Package:** `top.chenray.antilitematica.web`
- **Technology:** JDK built-in `HttpServer` (no extra dependencies)
- **Features:** Overview, player list, violation records, quick settings
- **Languages:** zh_CN, en_US, zh_TW

#### `AutoBuildManager`
- **Package:** `top.chenray.antilitematica.build`
- **Purpose:** Downloads latest release JAR from GitHub Releases
- **Auto-detect:** Automatically finds plugins folder if `output_path` is empty
- **Trigger:** Nightly schedule or `/al build` command

#### `DetectionLogger`
- **Package:** `top.chenray.antilitematica.util`
- **Purpose:** Writes detection events to a dedicated `detections.log` file
- **Rotation:** Daily date-based auto-rotation

#### `ConfigMigrator`
- **Package:** `top.chenray.antilitematica.config`
- **Purpose:** Auto-migrates old `config.yml` to add new default sections
- **Versioning:** `config_version` field tracks migration state

#### `StatsTracker`
- **Package:** `top.chenray.antilitematica.util`
- **Purpose:** Tracks daily detection/punishment counts, hit rates
- **Storage:** stats.yml with configurable retention days
- **Integration:** Called from Punisher and GraduatedPunisher

#### `AuditLogger`
- **Package:** `top.chenray.antilitematica.util`
- **Purpose:** Logs all admin actions (config changes, resets, kicks) to audit.log
- **Viewable:** In Web Dashboard audit tab

#### `UpdateChecker`
- **Package:** `top.chenray.antilitematica.update`
- **Purpose:** Checks GitHub for new versions and notifies admins

---

## Data Flow

```
Player Join
    │
    ▼
ModChannelDetector ──── Channel Register ────► Punisher
    │                                                 │
    ▼                                                 ▼
ProtocolLibBridge ──── Packet Signal ──────────► Punisher.punishDetection()
    │                                                 │
    ▼                                                 ├── Whitelist Check
PlacementGuard ──── Block Place ───────────────►      │
    │                    │                            ├── Graduated? ──► GraduatedPunisher
    │                    │                            │                      │
    ▼                    ▼                            │                      ▼
CommandGuard ──── Command Execute ─────────────►      │                 PunishmentTracker
                                                      │                      │
                                                      ▼                      ▼
                                                 DiscordWebhook         BanPluginHook
                                                      │                      │
                                                      ▼                      ▼
                                                 API Events ──────────► External Plugins
```

---

## Detection & Punishment Flow (Detailed)

```
Detection Trigger (channel/packet/placement/command)
    │
    ├──► [API] Fire DetectionEvent (cancellable)
    │         │
    │         ├── Cancelled → Log & return (no punishment)
    │         └── Not cancelled → Continue
    │
    ├──► Whitelist Check
    │         │
    │         ├── Whitelisted (LOG_ONLY) → Log & return
    │         └── Not whitelisted → Continue
    │
    ├──► Graduated Punishment?
    │         │
    │         ├── Yes → GraduatedPunisher.punish()
    │         │         ├── Record violation in PunishmentTracker
    │         │         ├── Look up punishment level
    │         │         ├── Execute action (warn/kick/tempban/ban)
    │         │         ├── Broadcast + Staff alert
    │         │         ├── Discord notification
    │         │         └── [API] Fire PunishmentEvent
    │         │
    │         └── No → Legacy Punisher
    │                   ├── Execute action (LOG/KICK/BAN/COMMANDS)
    │                   ├── Discord notification
    │                   └── [API] Fire PunishmentEvent
    │
    └──► Done
```

---

## Configuration System

- **`config.yml`** — Plugin settings (detection, punishment, integration, etc.)
- **`messages.yml`** — All customizable messages
- **`messages_{locale}.yml`** — Locale-specific overrides (zh_CN, en_US, zh_TW)
- **Parsing:** All settings are parsed into immutable `Settings` records via `Settings.from()`
- **Reload:** `/al reload` triggers `reloadSettings()` which rebuilds all components

---

## Threading Model

- **Bukkit Main Thread:** Event listeners, API calls, player interactions
- **Async Thread:** Discord webhook HTTP calls, GitHub API queries, file downloads
- **Bukkit Scheduler:** Delayed tasks (kick/ban execution, periodic checks)
- **Auto-Save:** PunishmentTracker saves to SQLite synchronously (fast local DB)

---

## Build System

- **Tool:** Maven
- **Java:** 21+
- **Shading:** bstats shaded into final JAR (relocated to `top.chenray.antilitematica.libs.bstats`)
- **Output:** `target/AntiLitematica-{version}.jar`
