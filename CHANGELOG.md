# Changelog

## v6.1.0 (2026-05-29)

### Removed

- **Web Dashboard** — Removed built-in HTTP server (DashboardServer). Use external monitoring tools instead.
- **Auto Build / GitHub Auto-Download** — Removed AutoBuildManager. Update manually via GitHub Releases.
- **Prometheus Metrics** — Removed /api/metrics endpoint (was part of Web Dashboard).

### Paper API Update

- Updated to **Paper API 26.1.2.build.66-stable** (Minecraft 1.21.11+).

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
