# AntiLitematica 公开 API

> 面向附属插件 / 外部系统开发者：检测联动、封禁管理、通道配置、统计查询
>
> 作者：阿清 | 许可：见 [LICENSE](../LICENSE)（**禁止任何商业用途**）

---

## 引入依赖

**方式一：安装到本地 Maven 仓库（推荐）**

```bash
# 1. 先构建插件 jar
mvn package

# 2. 安装到本地仓库（版本号按实际修改）
mvn install:install-file -Dfile=target/AntiLitematica-7.0.0.jar \
  -DgroupId=icu.epochcraft -DartifactId=AntiLitematica \
  -Dversion=7.0.0 -Dpackaging=jar
```

```xml
<!-- pom.xml -->
<dependency>
    <groupId>icu.epochcraft</groupId>
    <artifactId>AntiLitematica</artifactId>
    <version>7.0.0</version>
    <scope>provided</scope>
</dependency>
```

**方式二：直接放 jar 到 libs/ 目录（Gradle）**

```kotlin
// build.gradle.kts
dependencies {
    compileOnly(files("libs/AntiLitematica-7.0.0.jar"))
}
```

> 建议在 plugin.yml / mods.toml 中声明软依赖：`softdepend: [AntiLitematica]`

---

## 获取 API 实例

```java
import icu.epochcraft.antilitematica.api.AntiLitematicaAPI;

public class MyAddon extends JavaPlugin {

    @Override
    public void onEnable() {
        AntiLitematicaAPI api = AntiLitematicaAPI.get();
        if (api == null) {
            getLogger().warning("AntiLitematica 未加载，本附属插件功能不可用");
            return;
        }
        // ... 使用 api
    }
}
```

> 注意：`get()` 在插件未加载 / 卸载过程中返回 `null`，请务必判空。

---

## 方法总览

| 分类 | 方法 | 返回值 | 说明 |
|---|---|---|---|
| 基础 | `getPluginVersion()` | `String` | 插件版本号 |
| | `getLanguage()` | `String` | 当前语言（zh_cn / zh_tw / en_us） |
| | `getMode()` | `String` | 预设模式（strict / normal / lite） |
| | `getBanBackendName()` | `String` | 封禁后端（内置SQLite / LiteBans / AdvancedBan） |
| 通道 | `getChannels()` | `Map<String, ChannelConfig>` | 全部禁用通道（只读副本） |
| | `getChannelAction(channel)` | `String` | 通道动作（KICK / BAN / WARN / LOG） |
| | `getChannelBanDuration(channel)` | `long` | 通道封禁时长（毫秒） |
| | `addChannel(channel, action)` | `boolean` | 动态添加禁用通道（自动保存配置） |
| | `removeChannel(channel)` | `boolean` | 移除禁用通道 |
| 封禁 | `banPlayer(uuid, reason, duration)` | `void` | 封禁玩家（毫秒；`-1` 永久） |
| | `banPlayer(uuid, name, reason, duration)` | `void` | 封禁玩家（带名字） |
| | `unbanPlayer(uuid)` | `void` | 解封 |
| | `isBanned(uuid)` | `boolean` | 是否被封禁 |
| | `getBanInfo(uuid)` | `BanInfo?` | 当前封禁信息（未封禁为 null） |
| | `getAllBans()` | `List<BanInfo>` | 全部有效封禁 |
| 检测 | `flagPlayer(player, channel)` | `boolean` | 主动触发检测（走完整惩罚管线） |
| | `flagPlayer(player, channel, reason, type)` | `boolean` | 主动触发检测（指定类型） |
| | `addDetectionListener(listener)` | `void` | 注册检测监听器 |
| | `removeDetectionListener(listener)` | `void` | 移除检测监听器 |
| 豁免 | `forgivePlayer(uuid)` | `void` | 标记玩家为误报（通道豁免） |
| | `isForgiven(uuid, channel)` | `boolean` | 是否已豁免 |
| 统计 | `getTotalDetections()` | `int` | 检测总数 |
| | `getDetectionCount(uuid)` | `int` | 指定玩家检测次数 |
| | `getChannelStats()` | `Map<String, Int>` | 通道命中分布 |

---

## 示例代码

### 1. 查询当前配置（Java）

```java
AntiLitematicaAPI api = AntiLitematicaAPI.get();

// 禁用通道列表
for (ChannelConfig cfg : api.getChannels().values()) {
    getLogger().info("通道: " + cfg.getChannel()
        + " 动作: " + cfg.getAction()
        + " 封禁时长: " + cfg.getBanDurationMillis() + "ms");
}

// 查询单个通道
String action = api.getChannelAction("servux:litematics"); // "KICK"
long banDuration = api.getChannelBanDuration("servux:litematics"); // 毫秒
```

### 2. 监听检测事件（Java）

```java
api.addDetectionListener(info -> {
    getLogger().info(info.getPlayer().getName()
        + " 命中 [" + info.getChannel() + "] "
        + "类型: " + info.getDetectionType());
});
```

> 与监听 Bukkit 事件 `DetectionEvent` 等价，但无需自行注册 Listener。
> 注意：监听回调运行在主线程，且发生在惩罚执行**之前**，此时仅能拿到检测信息。

### 3. 主动触发检测（Java）

```java
// 附属插件检测到自己的证据后，交给 AntiLitematica 统一处理（渐进惩罚/封禁/通知）
boolean handled = api.flagPlayer(player, "myplugin:cheat", "附属插件报告作弊", DetectionType.OTHER);
if (handled) {
    getLogger().info("已由 AntiLitematica 处理");
}
```

### 4. 封禁 / 解封（Java）

```java
import java.util.UUID;

UUID uuid = player.getUniqueId();

// 封禁 30 天
api.banPlayer(uuid, player.getName(), "多次使用投影 mod", 30L * 24 * 60 * 60 * 1000);

// 永久封禁
api.banPlayer(uuid, "永久封禁", -1L);

// 查询
if (api.isBanned(uuid)) {
    BanInfo ban = api.getBanInfo(uuid); // 永不为 null（此处已判 isBanned）
    long remaining = ban.getExpiresInMillis(); // -1 表示永久
}

// 解封
api.unbanPlayer(uuid);
```

### 5. 动态添加禁用通道（Java）

```java
// 给某个 VIP 权限组禁用新的 mod 通道
if (api.addChannel("myvip:channel", "KICK")) {
    getLogger().info("通道已添加，配置已自动保存");
}
```

### 6. Kotlin 版本

```kotlin
val api = AntiLitematicaAPI.get() ?: return

// 检测监听（函数式接口，可直接 lambda）
api.addDetectionListener { info ->
    logger.info("${info.player.name} 命中 ${info.channel}")
}

// 主动 flag
api.flagPlayer(player, "servux:litematics", "插件通道注册", DetectionType.CHANNEL)

// 封禁 1 小时
api.banPlayer(player.uniqueId, player.name, "测试", 3_600_000L)
```

---

## Bukkit 事件（DetectionEvent）

每次检测命中都会触发 `icu.epochcraft.antilitematica.event.DetectionEvent`，**外部插件可取消**：

```java
import icu.epochcraft.antilitematica.event.DetectionEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MyListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDetection(DetectionEvent event) {
        // 白名单玩家豁免：取消处理（注意：取消不消耗冷却，玩家可再次触发）
        if (event.getPlayer().hasPermission("myplugin.exempt")) {
            event.setCancelled(true);
        }
    }
}
```

事件字段：`getPlayer()` / `getChannel()` / `getReason()` / `getDetectionType()` / `isCancelled()` / `setCancelled(boolean)`。

---

## 注意事项

1. **主线程限制**：`flagPlayer`、`banPlayer` 等涉及 Bukkit API 的方法应在主线程调用（与绝大多数 Bukkit 插件一致）；
2. **判空**：`AntiLitematicaAPI.get()` 可能返回 `null`（插件未安装 / 正在卸载）；
3. **软依赖**：附属插件请声明 `softdepend: [AntiLitematica]`，并在 `onEnable` 时检查 API 可用性；
4. **配置持久化**：`addChannel` / `removeChannel` 会自动保存到 config.yml；
5. **封禁后端联动**：`banPlayer` 会走当前封禁后端（内置 / LiteBans / AdvancedBan），附属插件无需关心底层实现；
6. **许可**：本 API 随插件免费开放，**禁止任何商业用途**（见 LICENSE）。

---

**完整源码与示例：** [AntiLitematica](https://github.com/EpochcraftMC/AntiLitematica) | 遇到问题加 QQ 群：1102137231
