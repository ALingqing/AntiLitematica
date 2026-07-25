# Changelog

## v6.4.0 (2026-07-25)

### Multi-World Support
- **Per-world config** — New `worlds:` section in `config.yml` allows overriding settings per world (detection, anti-printer, command guard, graduated punishment).
- **World-aware tracking** — Violation counters, token buckets, and placement analysis are now isolated per world. Switching worlds resets tracking state.
- **World-aware punishment** — `punished` state tracked per-world; `/al world list|<name>` command for inspection.

### Admin GUI
- **In-game management panel** — `/al gui` (alias: `/al menu`, `/al panel`) opens a 6-row inventory GUI.
- **Pages** — Main menu with status overview + feature toggles; Player list with pagination; World settings with click-to-toggle; Whitelist management.
- **Safe clicks** — Border protection, material validation prevents accidental triggers.

### Command Tab Completion
- **TabCompleter** — `/al ` + Tab now completes subcommands, player names, world names.
- **New subcommands** — `/al world`, `/al gui`, `/al menu`, `/al panel`.

### Bug Fixes
- **Duplicate `settings()` method** — Removed; previously caused compilation error.
- **`getWorld()` NPE** — 3 locations now have null checks for `player.getWorld()`.
- **Command injection** — All ban hooks (LiteBans, AdvancedBan, EssentialsX) and `runCommands()` sanitize player names and reasons.
- **OneBot CQ code injection** — `auto_escape` changed from `false` to `true`.
- **Discord proxy auth** — `proxyUsername`/`proxyPassword` now properly set `Proxy-Authorization` header.
- **DetectionEvent duplicate** — Removed from `GraduatedPunisher.punish()` and API `triggerDetection()`; `Punisher.punishDetection()` is the single source.
- **CommandGuard missing event** — Now fires `DetectionEvent` with type `COMMAND_GUARD`.
- **PunishmentEvent async** — Changed from hardcoded `super(true)` to `super(!Bukkit.isPrimaryThread())`.
- **CommandGuard burst logic** — Only blocked commands are counted toward burst limit (was all non-allowed commands).
- **SQLite migration** — Uses `PRAGMA table_info` for safe schema detection instead of fragile `SELECT`.
- **Burst match precision** — `startsWith` matching now requires command boundary (space or EOL), preventing `/setblock` matching `/setblockinfo`.
- **Graduated default** — Changed from `true` to `false` to prevent accidental auto-ban on first load.
- **Config load order** — `saveDefaultConfig()` now runs before `ConfigMigrator.migrate()`.
- **`unmarkPunished(UUID)`** — Now clears from ALL worlds (was only `_global_`).
- **ConfigMigrator dead code** — Removed post-save file existence check.

### Architecture Improvements
- **`CommandSanitizer`** — New shared utility for command injection prevention.
- **`AbstractBanHook`** — Base class eliminating 3 copies of `sanitize()` and `warn()`.
- **`BedrockPlayerDetector`** — New Geyser/Bedrock player detection (Floodgate API + prefix fallback).
- **`PlayerTracker`** — Unified per-player data replaces 6 separate maps in `PlacementGuard`; single `trackers.remove(id)` on quit.
- **`DetectionBus` / `DetectionHandler`** — Detection-punishment decoupling bus; external plugins can register custom handlers.
- **`DynamicThresholdManager`** — Now reads from `Settings` record instead of raw `config.yml`.
- **`NbtLite.tryExtractServuxVersionString()`** — Shared method eliminates duplicate in `ModChannelDetector` and `ProtocolLibBridge`.
- **`Whitelist.mode` validation** — Invalid mode values log a warning and fall back to `LOG_ONLY`.
- **`PunishmentTracker.cleanupOldRecords()`** — Reads `stats.record_retention_days` from config instead of hardcoded 30 days.
- **`StatsTracker`** — Config keys `record_retention_days` and `stats_retention_days` are now properly consumed.

### Geyser Compatibility
- **Actually implemented** — Bedrock players auto-exempted from all checks via Floodgate API (reflection) with name prefix fallback.

### DetectionBus Integration
- **PunisherHandler** — Wraps `Punisher` as a `DetectionHandler` registered on the bus.
- **ModChannelDetector** & **ProtocolLibBridge** — Now use `bus.emit()` instead of calling `Punisher` directly.
- External plugins can register custom handlers via `DetectionBus.register()`.

### Auto Config Wizard
- **ConfigWizard** — First-run server profile detection: Survival / Creative / Minigame.
- Sends recommendations to console on fresh install.
- Command: `/al wizard` for manual invocation.

### New Detection Strategies
- **NbtQueryStormDetector** — Detects Litematica schematic loading via NBT query burst analysis (15+ queries/sec threshold).
- **OperationModeDetector** — Analyzes block placement spatial patterns for grid-aligned sequences (>80% axis-aligned = operation mode).

### Cross-Server Sync
- **CrossServerSync** — BungeeCord/Velocity plugin messaging for violation record broadcasting.
- Channels: `al:violation` (sync violation counts), `al:punish` (network-wide bans).
- Auto-detects proxy environment; zero-config setup.

### MasaMods Compatibility (推客入/Tweakeroo)
- **MasaCompat** — New `compat/MasaCompat.java` centralizes all Masa family mod detection & compatibility.
- Automatically detects Litematica, Tweakeroo, MiniHUD, ItemScroller via plugin channels.
- **`compatibility.tweakeroo_mode: true`** in `config.yml` enables three relaxations:
  - `enforce_raytrace` skipped for Tweakeroo users (flexi placement modifies hit vectors)
  - `easy_place` threshold raised from 3→8 consecutive hits (fewer false positives)
  - `consecutive_same_type` ratio raised from 80%→95% (builders can use same blocks)
- Integration points: `ModChannelDetector` (channel detection), `PlacementGuard` (raytrace skip), `ProtocolLibBridge` (easy place threshold).
- ConfigMigrator v5: auto-adds `compatibility.tweakeroo_mode: false` to existing configs.

### Documentation
- **`.gitignore`** — Added excluding `target/`, IDE files, OS files, logs.
- **`README.md`** — Geyser support restored (now actually implemented); commands/permissions tables updated.
- **`COMMANDS.md`** — Added `/al world`, `/al gui`, `antilitematica.gui` permission; removed non-existent `/al build`.

## v6.3.0 (2026-06-06)

### Paper API Update

- Updated to **Paper API 26.1.2.build.69-stable** (Minecraft 1.21.4+).

## v6.2.0 (2026-06-06)

### Folia Support

- **Folia-compatible** — Replaced all `Bukkit.getScheduler()` calls with Folia-aware scheduling via `SchedulerUtil`.
  - Player operations (kick, sendMessage) use `player.getScheduler().run()` (entity region thread).
  - Global sync tasks use `Bukkit.getGlobalRegionScheduler().run()`.
  - Async timers use `Bukkit.getAsyncScheduler().runAtFixedRate()` with millisecond conversion.
- **New utility** — `SchedulerUtil.java` provides a clean, unified API across Paper and Folia.
- **plugin.yml** — Added `folia-supported: true`.

### Removed

- **LocaleManager** — Dead code: per-player locale resolution was never actually called. Message delivery already goes through `Settings.Messages` and `lang/messages_*.yml`.
- **AuditLogger** — Dead code: `auditLogger.log()` was never invoked anywhere.
- **In-Game GUI** — Entire `gui/` package removed (~500 lines). Administrators primarily use `config.yml` directly.
- **UpdateChecker** — Removed GitHub API version check. Update manually via GitHub Releases.
- **`/al gui`** / **`/al update`** / **`/al export`** / **`/al import`** / **`/al kickall`** — Removed rarely-used subcommands and their helper methods.

### Simplified

- **StatsTracker** — Replaced YAML persistence + batched saves + retention cleanup with pure in-memory `AtomicInteger` counters. Zero disk I/O.
- **config.yml** — Removed `stats.record_retention_days` and `stats.stats_retention_days` (no longer applicable).

### Documentation

- README, wiki/, docs/ updated to reflect all removals and simplifications.

## v6.1.0 (2026-05-29)

### Removed

- **Web Dashboard** — Removed built-in HTTP server (DashboardServer). Use external monitoring tools instead.
- **Auto Build / GitHub Auto-Download** — Removed AutoBuildManager. Update manually via GitHub Releases.
- **Prometheus Metrics** — Removed /api/metrics endpoint (was part of Web Dashboard).

### Paper API Update

- Updated to **Paper API 1.21.11-R0.1-SNAPSHOT** (Minecraft 1.21.11+).

### Performance Optimizations

- **StatsTracker** — Batch write every 30s instead of disk I/O per detection.
- **DetectionLogger** — Memory queue + batch write every 10s (was sync flush per line).
- **AuditLogger** — Memory queue + batch write every 10s (was auto-flush per line).
- **PlacementGuard** — Config caching, staffNotify UUID cache, Location reuse, sliding ratio 80% for consecutive same-type detection, pitch tolerance 0.1°.
- **ProtocolLibBridge** — Signal config cached at start, EasyPlace consecutive tracking (3 hits / 10s window), NBT query detail extraction (TX ID, block position).
- **CommandGuard** — StaffNotify UUID cache (replaces Bukkit.getOnlinePlayers() iteration).
- **GraduatedPunisher** — DiscordWebhook instance cached, staffNotify UUID cache.
- **Punisher** — God method decomposed into 10+ focused methods.
- **TokenBucket** — Removed redundant `synchronized` (Bukkit main thread is single-threaded).
- **DynamicThresholdManager** — Config values cached on reload, not read per-tick.

### Detection Improvements

- **ModChannelDetector** — Pre-registers known Litematica channels (litematica:main, litematica:hello, litematica:place) for passive monitoring beyond config list. Channel name resolution for readable logs. Detection cooldown (5s per player). Better servux payload depth inspection.
- **ProtocolLibBridge** — Consecutive EasyPlace hit tracking to reduce false positives. Servux metadata detection for packets without version string. NBT query inspection includes transaction ID and coordinates.
- **PlacementGuard** — Consecutive same-type uses sliding ratio (80%) instead of requiring all placements to match. NoLookChange pitch tolerance relaxed to 0.1° (yaw remains 0.05°).

### Configuration

- Config.yml rewritten with concise Chinese descriptions, reduced from 310 to 130 lines.

### Documentation

- README, wiki/, docs/ updated to reflect all changes.
