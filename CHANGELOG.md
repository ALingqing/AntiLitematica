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

### 🔧 Improvements

- **Standard Maven Project Layout** — Source files moved from `top/` to `src/main/java/top/`, resources moved to `src/main/resources/`, removed custom `<sourceDirectory>` from `pom.xml`.
- **Locale-Aware Messages** — Added `locale` config option (`zh_CN` / `en_US` / `zh_TW`). Plugin loads `messages_{locale}.yml` with fallback to `messages.yml`.
- **GitHub Repository** — Updated update checker URL to `ALingqing/AntiLitematica`.

### 🐛 Bug Fixes

- **Shade Plugin Warning** — Excluded `META-INF/MANIFEST.MF` from bstats shaded JARs to eliminate resource overlap warnings.
- **README Badge** — Fixed version badge to reflect actual version.

### 📦 Dependency Changes

- No new dependencies added. Web dashboard uses JDK built-in `com.sun.net.httpserver.HttpServer`.
