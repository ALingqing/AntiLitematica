# Changelog

## v6.0.0 (2026-05-22)

### New Features

- **World Whitelist** -- Skip detection entirely in configured worlds (build/creative servers). Configurable via world_whitelist section.
- **Command Allowlist** -- Exempt normal commands (/msg, /tell, etc.) from blocking via allowed_commands.
- **OneBot 11 (QQ Bot) Support** -- Send detection notifications to QQ groups via LLBot/go-cqhttp HTTP API.
- **Web Dashboard SSE Real-Time Push** -- Server-Sent Events push live updates to dashboard without manual refresh.
- **Web Dashboard Quick Settings** -- Toggle detection, printer, command guard, graduated punishment, webhook directly from dashboard.
- **Violation Ranking API** -- Top 20 violators ranking endpoint (/api/ranking), viewable in dashboard.
- **Admin Audit Log** -- All admin actions logged to audit.log, viewable in Web Dashboard.
- **Batch Kick Command** -- /al kickall kicks all currently flagged players.
- **Detection Statistics** -- StatsTracker tracks daily detection/punishment counts and hit rates. Configurable retention.
- **Prometheus Metrics Endpoint** -- /api/metrics exposes metrics for Grafana monitoring.
- **Auto-Detect Player Language** -- 50+ Minecraft client locales auto-detected, messages in player's own language.
- **Language Files in lang/ Folder** -- Clean plugin root, all messages_*.yml in lang/ subfolder.
- **JAR Auto-Replace** -- Auto replaces running JAR with backup when downloading updates.
- **Public API** -- AntiLitematicaAPI with DetectionEvent and PunishmentEvent.

### Improvements

- Configuration file reorganized into 14 numbered sections.
- Config auto-migration bumped to v3 for all new fields.
- pom.xml added release 21 to eliminate compiler warning.
- Documentation: full wiki/ and docs/ structure, no emoji.
- GitHub Actions CI: auto-build on push, release upload.

### Bug Fixes

- Fixed raw ConcurrentHashMap types in PlacementGuard
- Fixed Discord webhook 307 redirect handling
- Fixed Discord webhook hardcoded User-Agent
- Removed outdated src/main/resources/META-INF/

### Dependency Changes

- No new dependencies. Vulcan/Matrix use runtime reflection. MySQL requires external Connector/J.
