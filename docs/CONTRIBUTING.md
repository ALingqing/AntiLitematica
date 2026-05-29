# Contributing

Thanks for your interest in contributing to AntiLitematica!

---

## Development Setup

### Prerequisites

- Java 21+ (JDK)
- Apache Maven 3.8+
- Git
- A Paper/Spigot test server (optional but recommended)

### Getting Started

```bash
# Clone the repository
git clone https://github.com/ALingqing/AntiLitematica.git
cd AntiLitematica

# Build the plugin
mvn clean package

# The output JAR will be in target/
```

### Import into IDE

**IntelliJ IDEA:**
1. `File → New → Project from Existing Sources`
2. Select the `pom.xml`
3. IntelliJ will automatically import the Maven project

**Eclipse:**
1. `File → Import → Maven → Existing Maven Projects`
2. Select the project root directory

---

## Project Structure

```
AntiLitematica/
├── pom.xml                          # Maven build configuration
├── README.md                        # This file
├── CHANGELOG.md                     # Version history
├── docs/                            # Developer documentation
│   ├── API.md                       # Plugin API reference
│   ├── ARCHITECTURE.md              # Architecture overview
│   └── CONTRIBUTING.md              # This file
├── wiki/                            # User documentation
│   ├── INSTALL.md                   # Installation guide
│   ├── CONFIG.md                    # Configuration reference
│   ├── COMMANDS.md                  # Commands & permissions
│   └── FAQ.md                       # Frequently asked questions
└── src/
    ├── main/
    │   ├── java/top/chenray/antilitematica/
    │   │   ├── AntiLitematicaPlugin.java    # Main plugin class
    │   │   ├── api/                         # Public API
    │   │   ├── cmd/                         # Command handlers
    │   │   ├── config/                      # Configuration
    │   │   ├── detection/                   # Detection modules
    │   │   ├── guard/                       # Guard modules
    │   │   ├── punish/                      # Punishment system
    │   │   ├── integration/                 # Anti-cheat integration
    │   │   ├── protocol/                    # ProtocolLib bridge
    │   │   ├── web/                         # Web dashboard
    │   │   ├── state/                       # State listeners
    │   │   ├── threshold/                   # Dynamic thresholds
    │   │   ├── update/                      # Update checker
    │   │   ├── build/                       # Auto-update download
    │   │   ├── placeholder/                 # PlaceholderAPI expansion
    │   │   └── util/                        # Utility classes
    │   └── resources/
    │       ├── plugin.yml                   # Bukkit plugin descriptor
    │       ├── config.yml                   # Default configuration
    │       └── messages.yml                 # Default messages
    └── test/                               # (future)
```

---

## Coding Standards

### Java

- **Java version:** 21
- **Package naming:** `top.chenray.antilitematica.<module>`
- **Formatting:** 3-space indentation (consistent with existing code)
- **Naming:**
  - Classes: `PascalCase`
  - Methods/Variables: `camelCase`
  - Constants: `UPPER_SNAKE_CASE`
  - Records: `PascalCase`
- **Nullability:** Use `@NotNull` / `@Nullable` annotations from `org.jetbrains.annotations`

### Conventions

- Use Java records for immutable data carriers (`Settings` records)
- Use `CompletableFuture` for async operations
- Use `volatile` for fields accessed from multiple threads
- Log with `plugin.getLogger()` rather than `System.out`
- Use `Msg.color()` for color translation instead of direct `ChatColor` usage

### Pull Request Checklist

- [ ] Code compiles (`mvn clean compile`)
- [ ] No new warnings introduced
- [ ] Follows existing code style (3-space indent, etc.)
- [ ] New features include config.yml defaults (if applicable)
- [ ] Messages added to messages.yml (if applicable)
- [ ] API methods documented with Javadoc (if applicable)
- [ ] Testing performed on a Paper server (if applicable)

---

## Testing

1. Build the plugin: `mvn clean package`
2. Copy `target/AntiLitematica-{version}.jar` to your test server's `plugins/` folder
3. Restart or reload the server
4. Test with a client that has Litematica installed

### Testing Without Litematica

You can use the API to trigger detections programmatically:

```java
AntiLitematicaAPI api = AntiLitematicaAPI.getInstance();
if (api != null) {
    // Test channel detection
    api.triggerDetection(player, "servux:litematics", "API test");

    // Test printer detection
    api.triggerPrinterDetection(player, "API test");
}
```

---

## Issue Reporting

When reporting bugs, please include:

- Server software and version (e.g., Paper 1.21.4)
- AntiLitematica version
- Relevant config.yml sections
- Server logs showing the error
- Steps to reproduce

---

## Feature Requests

Feature requests are welcome. Please provide:

- A clear description of the feature
- Use case / why it's needed
- Any relevant configuration or API design suggestions

---

## License

By contributing, you agree that your contributions will be licensed under the same license as the project.
