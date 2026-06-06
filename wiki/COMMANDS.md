# Commands & Permissions

---

## Commands

All commands use the base `/antilitematica` or its alias `/al`.

| Command | Description | Permission |
|---------|-------------|------------|
| `/al reload` | Reload configuration from disk | antilitematica.admin |
| `/al status` | View current plugin settings | antilitematica.admin |
| `/al reset <player\|all\|expired>` | Reset violation records | antilitematica.admin |
| `/al history <player> [page]` | View violation history with pagination | antilitematica.admin |
| `/al testnotify` | Test Discord webhook and OneBot QQ bot | antilitematica.admin |
| `/al whitelist list` | List all whitelisted players | antilitematica.admin |
| `/al whitelist add <player>` | Add a player to the whitelist | antilitematica.admin |
| `/al whitelist remove <player>` | Remove a player from the whitelist | antilitematica.admin |

### Command Details

#### `/al reload`
Reloads config.yml and messages.yml without restarting. All modules re-initialized.

#### `/al status`
Displays current settings: enabled, detection action, storage type, signal checks, anti-printer, command guard, graduated punishment.

#### `/al reset`
- `/al reset <player>` -- Reset a specific player's record
- `/al reset all` -- Reset ALL violation records
- `/al reset expired` -- Clear only expired records

#### `/al history <player> [page]`
Displays violation record with pagination (5 entries per page).

#### `/al testnotify`
Tests all configured notification services (Discord webhook and OneBot QQ bot).

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
