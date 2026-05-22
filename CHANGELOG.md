# Changelog

## v5.0.0 (2026-05-22)

### ✨ New Features

- **Auto Update Checker** — Automatically checks GitHub for new releases on startup. Admins are notified on join when an update is available. Manual check via `/al update`.
- **Violation Whitelist** — Whitelist trusted players to only log detections without punishment. Three modes supported via `config.yml` (`whitelist` section). Manage in-game with `/al whitelist add|remove|list`.
- **Web Dashboard (Multi-Language)** — Built-in web-based monitoring panel using JDK `HttpServer` (no extra dependencies). Access at `http://<ip>:25418`. Features:
  - Server overview (TPS, online players, detection/punishment stats)
  - Real-time player list with ping, gamemode, violation count
  - Violation records viewer with clear action
  - Quick settings toggles (detection, anti-printer, webhook)
  - Multi-language UI: **简体中文** / **English** / **繁體中文**
- **Public API** — `AntiLitematicaAPI` interface with static singleton and Bukkit ServicesManager registration. `DetectionEvent` (cancellable) and `PunishmentEvent` for external plugin integration.
- **Auto GitHub Release Download** — Download latest plugin JAR directly from GitHub Releases via `/al build` (manual) or nightly schedule. Auto-detects plugins folder if `output_path` is empty.
- **MySQL Storage** — Violation records can now be stored in MySQL/MariaDB (`storage: mysql`). Includes configurable host, port, database, user, password.
- **Detection Log File** — Dedicated `detections.log` with daily date rotation. Capture every detection event in a separate file for auditing.
- **Config Auto-Migration** — `config.yml` is automatically updated with new default sections when upgrading, preserving existing settings. Tracks version via `config_version`.
- **Vulcan & Matrix Integration** — Reflection-based anti-cheat integration for Vulcan and Matrix (no compile-time dependency required, auto-detected at runtime).

### 🔧 Improvements

- **Standard Maven Project Layout** — Source files moved from `top/` to `src/main/java/top/`, resources moved to `src/main/resources/`, removed custom `<sourceDirectory>` from `pom.xml`.
- **Locale-Aware Messages** — Added `locale` config option (`zh_CN` / `en_US` / `zh_TW`). Plugin loads `messages_{locale}.yml` with fallback to `messages.yml`.
- **GitHub Repository** — Updated update checker URL to `ALingqing/AntiLitematica`.
- **Documentation Structure** — Added `docs/` (API reference, architecture, contributing) and `wiki/` (install, config, commands, FAQ, web dashboard) with full documentation.
- **README Rewrite** — Streamlined with quick-start, feature icons, and links to all documentation.
- **Removed Outdated META-INF** — Deleted manually-maintained `src/main/resources/META-INF/` (Maven auto-generates these files).

### 🐛 Bug Fixes

- **Shade Plugin Warning** — Excluded `META-INF/MANIFEST.MF` from bstats shaded JARs to eliminate resource overlap warnings.
- **README Badge** — Fixed version badge to reflect actual version.

### 📦 Dependency Changes

- No new dependencies added. Vulcan/Matrix integrations use runtime reflection.
- MySQL support requires MySQL Connector/J on the server classpath (not bundled).
