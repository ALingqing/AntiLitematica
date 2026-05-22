# Configuration Reference

> File: `plugins/AntiLitematica/config.yml`

---

## Global Settings

```yaml
enabled: true
```

Master switch. Set to `false` to disable all plugin functionality without removing the JAR.

---

## Detection

```yaml
detection:
  enabled: true
  channels:
    - "servux:litematics"
  block_servux: true
  commands: []
  kick_after_commands: true
  signals:
    servux_metadata:
      enabled: true
    easy_place:
      enabled: true
      rel_min: -0.5
      rel_max: 1.5
      cancel_packet: false
    nbt_query:
      enabled: true
      allow_op: true
      cancel_packet: true
  action: "KICK"
  reason: "Forbidden client mod detected (Litematica)."
```

| Option | Default | Description |
|--------|---------|-------------|
| `enabled` | `true` | Enable/disable detection system |
| `channels` | `["servux:litematics"]` | Plugin channels to monitor |
| `block_servux` | `true` | Block `servux:litematics` payloads |
| `commands` | `[]` | Console commands to execute on detection (action=COMMANDS) |
| `kick_after_commands` | `true` | Kick after executing commands |
| `signals.servux_metadata.enabled` | `true` | Detect Litematica Servux metadata requests |
| `signals.easy_place.enabled` | `true` | Detect EasyPlace protocol abuse |
| `signals.easy_place.rel_min` | `-0.5` | Minimum relative hit vector (vanilla ~0) |
| `signals.easy_place.rel_max` | `1.5` | Maximum relative hit vector (vanilla ~1) |
| `signals.easy_place.cancel_packet` | `false` | Cancel the EasyPlace packet instead of just detecting |
| `signals.nbt_query.enabled` | `true` | Detect debug NBT queries |
| `signals.nbt_query.allow_op` | `true` | Allow OPs to send NBT queries without penalty |
| `signals.nbt_query.cancel_packet` | `true` | Cancel NBT query packets |
| `action` | `KICK` | Punishment action: `LOG`, `KICK`, `BAN`, `COMMANDS` |
| `reason` | ... | Ban/kick reason message |

**Note:** If `graduated_punishment` is enabled, `action` here serves as a fallback. The graduated system takes priority.

---

## Anti-Printer

```yaml
anti_printer:
  enabled: true
  apply_to_creative: true
  enforce_raytrace: true
  reach_survival: 5.0
  reach_creative: 6.0
  extra_reach_allowance: 0.25
  max_blocks_per_second: 14
  detect_consecutive_same_type: true
  consecutive_same_type_threshold: 6
  detect_no_look_change: true
  no_look_change_threshold: 5
  violations:
    window_ms: 8000
    kick_at: 8
```

| Option | Default | Description |
|--------|---------|-------------|
| `enabled` | `true` | Enable anti-printer engine |
| `apply_to_creative` | `true` | Also check creative mode players |
| `enforce_raytrace` | `true` | Force raytrace check on block placement |
| `reach_survival` | `5.0` | Max survival reach distance |
| `reach_creative` | `6.0` | Max creative reach distance |
| `extra_reach_allowance` | `0.25` | Extra reach tolerance |
| `max_blocks_per_second` | `14` | Max blocks placed per second (0 = unlimited) |
| `detect_consecutive_same_type` | `true` | Detect same-block-type streaks |
| `consecutive_same_type_threshold` | `6` | Consecutive same blocks before flagging |
| `detect_no_look_change` | `true` | Detect placement without camera movement |
| `no_look_change_threshold` | `5` | Placements without look change before flagging |
| `violations.window_ms` | `8000` | Violation counting window (ms) |
| `violations.kick_at` | `8` | Violations before kicking |

---

## Command Guard

```yaml
command_guard:
  enabled: true
  blocked_commands:
    - "/setblock"
  max_per_window: 8
  window_ms: 3000
  violations:
    window_ms: 8000
    kick_at: 5
```

| Option | Default | Description |
|--------|---------|-------------|
| `enabled` | `true` | Enable command guard |
| `blocked_commands` | `["/setblock"]` | Commands to block (prefix match) |
| `max_per_window` | `8` | Max commands within window before flagging |
| `window_ms` | `3000` | Command monitoring window (ms) |
| `violations.window_ms` | `8000` | Violation counting window (ms) |
| `violations.kick_at` | `5` | Violations before kicking |

---

## Graduated Punishment

```yaml
graduated_punishment:
  enabled: true
  window_minutes: 60
  storage: sqlite
  levels:
    1:
      action: warn
      duration: "0"
      reason: "使用投影模组 - 警告 (1/3)"
      broadcast: false
      staff_alert: true
    2:
      action: tempban
      duration: "30m"
      reason: "多次使用投影模组 - 临时封禁 (2/3)"
      broadcast: true
      staff_alert: true
    3:
      action: ban
      duration: "0"
      reason: "屡次使用投影模组 - 永久封禁 (3/3)"
      broadcast: true
      staff_alert: true
  exceed_max:
    action: ban
    duration: "0"
    reason: "违规次数过多 - 永久封禁"
  ban_plugins:
    - "LiteBans"
    - "AdvancedBan"
    - "EssentialsX"
```

| Option | Default | Description |
|--------|---------|-------------|
| `enabled` | `true` | Enable graduated punishment |
| `window_minutes` | `60` | Time window for violation counting |
| `storage` | `sqlite` | Storage method: `sqlite` / `memory` |
| `levels.<n>.action` | — | Action: `warn`, `kick`, `tempban`, `ban` |
| `levels.<n>.duration` | — | Ban duration (e.g. `30m`, `2h`, `7d`) |
| `levels.<n>.reason` | — | Punishment reason (supports `%player%`, `%count%`, `%total%`) |
| `levels.<n>.broadcast` | `false` | Broadcast to all players |
| `levels.<n>.staff_alert` | `true` | Alert staff with `antilitematica.notify` |
| `exceed_max.*` | — | Action when exceeding max level |
| `ban_plugins` | — | Ban plugin priority list |

---

## Discord Webhook

```yaml
discord:
  enabled: false
  webhook_url: ""
  username: "AntiLitematica"
  embed_title: "Forbidden client detected"
  embed_color: 16711680
  notify_on_detection: true
  notify_on_punish: true
```

| Option | Default | Description |
|--------|---------|-------------|
| `enabled` | `false` | Enable Discord notifications |
| `webhook_url` | `""` | Discord webhook URL |
| `username` | `"AntiLitematica"` | Webhook display name |
| `embed_title` | ... | Embed title text |
| `embed_color` | `16711680` | Embed color (decimal, red=16711680) |
| `notify_on_detection` | `true` | Notify on detection events |
| `notify_on_punish` | `true` | Notify on punishment execution |
| `proxy_host` | `""` | HTTP proxy host (optional) |
| `proxy_port` | `0` | HTTP proxy port (optional) |

---

## Whitelist

```yaml
whitelist:
  enabled: false
  mode: "LOG_ONLY"
  players: []
```

| Option | Default | Description |
|--------|---------|-------------|
| `enabled` | `false` | Enable violation whitelist |
| `mode` | `LOG_ONLY` | `LOG_ONLY` (log only, no punish) or `NORMAL` (bypass all) |
| `players` | `[]` | List of whitelisted player names (case-insensitive) |

---

## Integration

```yaml
integration:
  enabled: false
  type: "none"
  violation_level: 10
  check_prefix: "AntiLitematica"
```

| Option | Default | Description |
|--------|---------|-------------|
| `enabled` | `false` | Enable anti-cheat integration |
| `type` | `"none"` | Integration type: `none` or `grim` |
| `violation_level` | `10` | Violation level to report |
| `check_prefix` | `"AntiLitematica"` | Check name prefix |

---

## Dynamic Threshold

```yaml
dynamic_threshold:
  enabled: false
  check_interval_seconds: 30
  tps:
    high: 19.5
    low: 16.0
  players:
    high: 50
    low: 5
  multiplier:
    min: 1.0
    max: 2.0
```

Auto-adjusts detection sensitivity based on server TPS and player count. When TPS is low or many players are online, thresholds become more lenient.

---

## Web Dashboard

```yaml
web_dashboard:
  enabled: false
  port: 25418
  password: "admin"
  locale: "zh_CN"
```

See [Web Dashboard Guide](WEB_DASHBOARD.md) for details.

---

## Auto Update

```yaml
auto_build:
  enabled: false
  output_path: ""
  nightly_time: "03:00"
  auto_reload: false
  post_build_command: ""
```

| Option | Default | Description |
|--------|---------|-------------|
| `enabled` | `false` | Enable auto-update from GitHub Releases |
| `output_path` | `""` | Server plugins folder path |
| `nightly_time` | `"03:00"` | Nightly check time (24h format), empty to disable |
| `auto_reload` | `false` | Run `/plugman reload AntiLitematica` after download |
| `post_build_command` | `""` | Custom command after download (overrides auto_reload) |

---

## Locale

```yaml
locale: "zh_CN"
```

Options: `zh_CN`, `en_US`, `zh_TW`, `default`

The plugin loads `messages_{locale}.yml` with fallback to `messages.yml`.

---

## Messages

Messages are stored in `messages.yml` (and optionally `messages_{locale}.yml`). Key messages:

```yaml
prefix: "&7[&cAntiLitematica&7] "
kick: "&cYou are not allowed to use Litematica / Printer..."
blocked_place: "&cPlease aim at the block before placing..."
reload: "&aAntiLitematica configuration reloaded."
```

Edit these files directly and run `/al reload` to apply.
