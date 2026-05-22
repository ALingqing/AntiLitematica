# Commands & Permissions

---

## Commands

All commands use the base `/antilitematica` or its alias `/al`.

| Command | Description | Permission |
|---------|-------------|------------|
| `/al reload` | Reload configuration from disk | `antilitematica.admin` |
| `/al gui` | Open configuration GUI (in-game only) | `antilitematica.admin` |
| `/al status` | View current plugin settings | `antilitematica.admin` |
| `/al update` | Check for new versions on GitHub | `antilitematica.admin` |
| `/al build` | Download latest release from GitHub | `antilitematica.admin` |
| `/al reset <player>` | Reset violation record for a player | `antilitematica.admin` |
| `/al history <player>` | View violation history for a player | `antilitematica.admin` |
| `/al whitelist list` | List all whitelisted players | `antilitematica.admin` |
| `/al whitelist add <player>` | Add a player to the whitelist | `antilitematica.admin` |
| `/al whitelist remove <player>` | Remove a player from the whitelist | `antilitematica.admin` |

### Command Details

#### `/al reload`
Reloads `config.yml` and `messages.yml` without restarting the server. All modules are re-initialized with the new settings.

#### `/al gui`
Opens an interactive inventory GUI for managing plugin settings. Only available to in-game players.

#### `/al status`
Displays a summary of current plugin settings, including:
- Plugin enabled status
- Detection enabled/disabled
- Detection action (LOG/KICK/BAN/COMMANDS)
- Signal checks (Servux metadata, EasyPlace, NBT query)
- Anti-printer status
- Command guard status
- Graduated punishment status

#### `/al update`
Checks GitHub for new releases. If an update is available, displays the version number and download link.

#### `/al build`
Downloads the latest release JAR from GitHub to the configured `output_path`. Only works if `auto_build` is enabled and configured in `config.yml`.

#### `/al reset <player>`
Resets the violation record for the specified player. This clears their violation count and punishment level in the graduated system.

#### `/al history <player>`
Displays the violation record for a player:
- Current window violation count
- Total lifetime violations
- First and last violation timestamps

#### `/al whitelist`
Manage the violation whitelist. See [Configuration](CONFIG.md#whitelist) for details.

---

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `antilitematica.admin` | OP | Access to all `/al` commands |
| `antilitematica.bypass` | OP | Bypass all detection checks entirely |
| `antilitematica.notify` | OP | Receive staff alert messages |

### Permission Notes

- **`antilitematica.bypass`**: Players with this permission will never be detected or punished. Use for server staff and testers.
- **`antilitematica.notify`**: Players with this permission receive real-time alerts when detections occur, including player name, reason, and action taken.
- Players without `antilitematica.admin` cannot use any `/al` commands.

---

## Example Permission Setup

```yaml
# permissions.yml
groups:
  admin:
    permissions:
      - antilitematica.admin
      - antilitematica.bypass
      - antilitematica.notify
  moderator:
    permissions:
      - antilitematica.notify
  builder:
    permissions:
      - antilitematica.bypass
```

---

## Console Usage

All commands can also be executed from the server console:

```
/al reload
/al status
/al reset aqing
/al history aqing
/al whitelist list
/al whitelist add trusted_player
/al build
```
