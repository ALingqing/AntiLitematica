# Frequently Asked Questions

---

### Q: What does AntiLitematica detect?

AntiLitematica detects:
- **Litematica** client mod (via `servux:litematics` plugin channel)
- **Schematica** client mod (via `schematica` channel - legacy)
- **Litematica Printer** (via block placement analysis)
- **EasyPlace Protocol** (via ProtocolLib hit-vector inspection)
- **Schematic Quick-Paste** (via command guard, `/setblock` spam)

---

### Q: Can players bypass detection by disabling Servux sync?

**No.** Even if a player disables Servux sync in Litematica, the plugin uses ProtocolLib to detect:
1. Abnormal hit vectors (EasyPlace signal)
2. NBT debug queries
3. Servux metadata requests

These are low-level packet fingerprints that Litematica cannot disable.

---

### Q: Will this cause false positives with vanilla players?

False positives are minimized through:
- **Dynamic thresholds** — Auto-adjusts sensitivity based on server TPS/player count
- **Whitelist** — Exempt trusted builders from punishment
- **Geyser detection** — Bedrock players are auto-exempted
- **Configurable tolerances** — Adjust reach, rate limits, and thresholds
- **LOG mode** — Test with logging only before enabling punishment

---

### Q: Does it work with Geyser/Bedrock players?

**Yes.** AntiLitematica automatically detects Geyser/Floodgate and exempts Bedrock players from all checks. Bedrock protocol translation can cause false positives, so this is handled automatically.

---

### Q: What's the difference between LOG_ONLY and NORMAL whitelist modes?

- **`LOG_ONLY`** — Detection events are logged to console but no punishment is applied. Use for testing or monitoring trusted builders.
- **`NORMAL`** — All checks are completely skipped for the player. Use for server staff who need to use schematic tools legitimately.

---

### Q: Which ban plugins are supported for graduated punishment?

The plugin supports:
1. [LiteBans](https://www.spigotmc.org/resources/litebans.3715/)
2. [AdvancedBan](https://www.spigotmc.org/resources/advancedban.8695/)
3. [EssentialsX](https://essentialsx.net/)

If none are available, it falls back to Bukkit's native ban system.

---

### Q: Can I use this on a Spigot server?

**Yes.** AntiLitematica works on Paper, Spigot, and Purpur. ProtocolLib is required.

---

### Q: How do I test if the plugin is working?

1. Install Litematica on a test client
2. Join the server
3. Try using Litematica's schematic viewer or printer
4. Check console for detection messages
5. Run `/al status` to verify plugin state

---

### Q: The plugin is detecting legitimate builders. What should I do?

1. Add them to the whitelist: `/al whitelist add <player>`
2. Adjust anti-printer tolerances in config.yml
3. Enable dynamic threshold adjustment
4. Consider using `LOG` action mode temporarily to monitor without punishing

---

### Q: What is the web dashboard and how do I use it?

The web dashboard is a built-in monitoring panel accessible via browser. See [Web Dashboard Guide](WEB_DASHBOARD.md) for full instructions.

---

### Q: How do I update the plugin?

**Automatic:** Configure `auto_build` in config.yml and run `/al build`, or set a nightly time for automatic updates.

**Manual:** Download from [GitHub Releases](https://github.com/ALingqing/AntiLitematica/releases) and replace the JAR.

---

### Q: What permissions should I give my staff?

Recommended setup:

| Staff Role | Permissions |
|-----------|-------------|
| Admin | `antilitematica.admin`, `antilitematica.bypass`, `antilitematica.notify` |
| Moderator | `antilitematica.notify` |
| Builder | Add to whitelist (`/al whitelist add`) |

---

### Q: Does the plugin collect data?

The plugin uses [bStats](https://bstats.org/plugin/bukkit/AntiLitematica/31012) for anonymous usage statistics. You can disable this in config.yml:

```yaml
bstats:
  enabled: false
```

---

### Q: Can I customize the punishment messages?

Yes. All messages are in `messages.yml`. Edit the file and run `/al reload`. You can also create locale-specific files like `messages_en_US.yml` or `messages_zh_TW.yml`.

---

### Q: The plugin says "Update available" — how do I update?

Run `/al build` if auto-update is configured. Otherwise, download the latest JAR from [GitHub Releases](https://github.com/ALingqing/AntiLitematica/releases), replace the old file, and restart the server or run `/plugman reload AntiLitematica`.
