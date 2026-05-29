# Commands & Permissions

---

## Commands

All commands use the base `/antilitematica` or its alias `/al`.

| Command | Description | Permission |
|---------|-------------|------------|
| `/al reload` | Reload configuration from disk | antilitematica.admin |
| `/al gui` | Open configuration GUI (in-game only) | antilitematica.admin |
| `/al status` | View current plugin settings | antilitematica.admin |
| `/al update` | Check for new versions on GitHub | antilitematica.admin |
| `/al reset <player\|all\|expired>` | Reset violation records | antilitematica.admin |
| `/al history <player> [page]` | View violation history with pagination | antilitematica.admin |
| `/al export` | Export violation records to JSON file | antilitematica.admin |
| `/al import` | Import violation records from JSON file | antilitematica.admin |
| `/al testnotify` | Test Discord webhook and OneBot QQ bot | antilitematica.admin |
| `/al kickall` | Kick all currently flagged players | antilitematica.admin |
| `/al whitelist list` | List all whitelisted players | antilitematica.admin |
| `/al whitelist add <player>` | Add a player to the whitelist | antilitematica.admin |
| `/al whitelist remove <player>` | Remove a player from the whitelist | antilitematica.admin |

### Command Details

#### `/al reload`
Reloads config.yml and messages.yml without restarting. All modules re-initialized.

#### `/al gui`
Opens an interactive inventory GUI for managing settings. In-game only.

#### `/al status`
Displays current settings: enabled, detection action, storage type, signal checks, anti-printer, command guard, graduated punishment.

#### `/al update`
Checks GitHub for new releases. Shows version and download link if available.

#### `/al reset`
- `/al reset <player>` -- Reset a specific player's record
- `/al reset all` -- Reset ALL violation records
- `/al reset expired` -- Clear only expired records

#### `/al history <player> [page]`
Displays violation record with pagination (5 entries per page).

#### `/al export`
Exports all violation records to violations_export.json in the plugin folder.

#### `/al import`
Imports violation records from violations_export.json in the plugin folder.

#### `/al testnotify`
Tests all configured notification services (Discord webhook and OneBot QQ bot).

#### `/al kickall`
Kicks all players currently marked as punished. Respects antilitematica.bypass permission.

#### `/al whitelist`
Manage the violation whitelist. See Configuration for details.

---

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| antilitematica.admin | OP | Access to all /al commands |
| antilitematica.bypass | OP | Bypass all detection checks entirely |
| antilitematica.notify | OP | Receive staff alert messages |

### Notes

- **antilitematica.bypass**: Players with this permission are never detected or punished.
- **antilitematica.notify**: Players receive real-time alerts on detection events.
- Players without antilitematica.admin cannot use any /al commands.

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
