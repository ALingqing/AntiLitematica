# Web Dashboard

AntiLitematica includes a built-in web-based monitoring dashboard — no additional web server required. It uses JDK's built-in `com.sun.net.httpserver.HttpServer`.

---

## Enabling

Edit `config.yml`:

```yaml
web_dashboard:
  enabled: true
  port: 25418
  password: "your_password"
  locale: "zh_CN"     # zh_CN / en_US / zh_TW
```

Then run `/al reload` or restart the server.

---

## Accessing

1. Open your browser
2. Navigate to `http://<your-server-ip>:25418`
   - Local server: `http://localhost:25418`
   - Remote server: `http://192.168.x.x:25418` (or your public IP)
3. Enter the password configured in `config.yml`

---

## Dashboard Pages

### 📊 Overview

Displays server status at a glance:

| Widget | Description |
|--------|-------------|
| Server Status | Online players, TPS, plugin version |
| Detection System | Toggle indicators for detection, anti-printer, webhook, graduated punishment |
| Today's Stats | Detection count, punishment count for today |

### 👤 Players

Real-time list of online players with:

- Player name
- UUID
- Ping (ms)
- Game mode
- World
- Violation count
- **Reset** button (clears violation record)

### ⚠️ Violations

View recorded violations:

- Player name
- Reason / channel
- Action taken
- Timestamp
- Violation count
- **Clear** button (removes all records)

### ⚙️ Quick Settings

Toggle plugin features on/off without editing config files:

- Detection system
- Anti-printer
- Discord webhook

Changes are saved immediately.

### 🌐 Language Switch

Toggle between:
- **简体中文** (Chinese Simplified)
- **English**
- **繁體中文** (Chinese Traditional)

---

## Security

- The dashboard requires a password to access (default: `admin` — **change this**)
- No HTTPS support (use a reverse proxy like Nginx for SSL)
- The dashboard binds to all network interfaces by default
- Consider firewall rules to restrict access to trusted IPs

---

## Customization

### Port
Change the port if `25418` conflicts with other services.

### Password
Set a strong password. The dashboard is only as secure as this password.

### Language
Choose the UI language for all dashboard text.

---

## Troubleshooting

**Cannot connect to dashboard:**
- Verify `enabled: true` in config
- Check that the port is not blocked by a firewall
- Ensure no other service is using the same port
- Check server console for "Dashboard started on port XXXX" message

**Password not working:**
- Check for trailing spaces in the config value
- Run `/al reload` after changing the password
- Reset the password in `config.yml` and reload

**Dashboard shows no data:**
- Ensure the plugin has been running for at least a few seconds
- Detection data accumulates over time as events occur
