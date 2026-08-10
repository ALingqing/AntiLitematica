# AntiLitematica

> 检测并阻止携带 **Litematica（投影）/ Schematica** 等 mod 的玩家进入服务器的 Paper 插件
>
> 作者：阿清 | 语言：Kotlin | **完全免费开源 · 禁止任何商业用途**
>
> 仓库：[github.com/EpochcraftMC/AntiLitematica](https://github.com/EpochcraftMC/AntiLitematica)

> **许可声明**：本项目**不接受任何商业用途**（禁止出售、商业服务器、变相牟利等），详见 [LICENSE](LICENSE)。
>
> **附属插件 API**：面向开发者，见 [docs/API.md](docs/API.md)

---

## 功能特性（29 项）

**检测拦截**
- 1. **进服拦截**：检测到客户端注册禁用插件通道（如 `servux:litematics`）立即处理，不让进服
- 2. **动作分级**：每个通道可配置 `KICK 踢出 / BAN 封禁 / WARN 警告 / LOG 仅记录`
- 3. **通道指纹库**：内置已知 mod 通道说明（Litematica / Schematica / malilib / Servux…）
- 4. **Mod List 深度解析**（需 ProtocolLib）：解析 FML/Fabric 握手上报的完整 mod 列表，不注册通道的 mod 也能精确识别；按 modid 定制动作 + 变化追踪（历史有禁用 mod 本次未上报即处理）+ 三方交叉验证（通道/Brand 互证）
- 5. **Forge/FML 识别**：识别 `fml:*` 握手通道（仅记录，不误伤）
- 6. **Fabric 识别**：识别 `fabric:*` 环境通道（仅记录，不误伤）
- 7. **Brand 拦截**：黑名单客户端标识（`fabric/forge/lunar…`）命中即踢，默认关闭防误伤
- 8. **二次验证**：进服延迟复查已注册通道 + Brand，防注册-注销欺骗
- 9. **误报豁免**：管理员标记误报后，该玩家+通道组合不再拦截
- 10. **防 Printer**：令牌桶限速 + 射线校验 + 连续同类型（≥80%）+ 视角不变四重检测
- 11. **命令防护**：拦截 quick-paste 命令滥用（如 `/setblock` 连发），窗口限流
- 12. **EasyPlace 信号**（需 ProtocolLib）：命中向量偏移异常检测易放模式，连续 N 次触发
- 13. **NBT 查询风暴**（需 ProtocolLib）：高频 NBT 查询检测（频率门控，防误伤）

**惩罚与记录**
- 14. **渐进惩罚**：WARN→KICK→TEMPBAN→BAN 按违规次数逐级升级（时间窗口内计数）
- 15. **自动封禁**：累计 KICK 达阈值自动转封禁，时长可配
- 16. **SQLite 数据库**：检测记录 / 封禁 / 误报 / 违规计数持久化（含证据列）
- 17. **登录拦截**：被封禁玩家在登录阶段直接拒绝进入
- 18. **到期自动解封**：定时清理过期封禁
- 19. **统计面板**：`/antilitematica stats` 查看检测总数、通道分布、近 7 天趋势
- 20. **玩家画像**：`history <玩家>` 查看个人检测记录

**联动与通知**
- 21. **反作弊集成**：检测同步 flag 给 GrimAC / Vulcan / Matrix（反射，自动探测）
- 22. **Discord Webhook**：命中实时推送到 Discord（纯出站）
- 23. **QQ 群通知**：OneBot/NapCat 兼容，出站调用 `send_group_msg`
- 24. **管理员实时通知**：在线管理员（`antilitematica.notify`）即时收到命中信息
- 25. **封禁后端联动**：LiteBans / AdvancedBan 可选封禁后端（双写 + 自动探测，登录拦截统一）
- 26. **公开 API**：附属插件开发（查询/封禁/检测联动/统计，见 [docs/API.md](docs/API.md)）
- 27. **bStats 统计**：匿名统计（可选，config.yml 填插件 ID 启用）

**管理**
- 28. **双菜单系统**：Paper 1.21.7+ 原生 Dialog 菜单 / 低版本服务端自动回退箱子菜单
- 29. **预设模式**：strict（严格+自动封禁）/ normal / lite 一键切换
- **统一检测总线**：所有检测源 → DetectionBus → 惩罚管线（渐进 / 基础动作），路径唯一
- **多语言**：`lang/` 文件夹内置简中/繁中/英文，`config.yml` 的 `language` 一键切换，支持自定义全部文案
- **PlaceholderAPI**：`%antilitematica_detections%` 等占位符
- **更新检查器**：启动时检查 GitHub Releases 新版本

> **全部功能免费，无授权码、无功能锁定、无混淆，源码完全开源。**

## 检测原理

客户端连接服务器时，会通过 `minecraft:register` 数据包上报自己注册的所有**插件通道**（Plugin Channel）。
Bukkit 会为每个新通道触发 `PlayerRegisterChannelEvent`，插件只需判断通道是否命中禁用列表即可。

经源码分析（`litematica-26.2` / `Litematica-Forge-1.21.7-neoforge-dev`）：

| Mod | 注册的插件通道 | 说明 |
|---|---|---|
| Litematica 26.x (Fabric) | `servux:litematics` | `ServuxLitematicaHandler.CHANNEL_ID`，客户端初始化时无条件注册 |
| Litematica (Forge 移植版) | `servux:litematics` | 同上 |
| Schematica (1.12) | `schematica` | `SimpleNetworkWrapper(Reference.MODID)` |
| Litematica Printer | （无独立通道） | 依赖 Litematica，检测到 Litematica 即覆盖 |

> 通道列表可在 `config.yml` 中自由扩展，可用于检测任意通过插件通道上报 mod 的客户端。

### Mod List 深度解析（第二层防线）

通道检测存在盲区：**部分 mod 不注册插件通道**，且客户端可禁用通道上报。
但所有 Forge/NeoForge/Fabric 客户端在**握手阶段必定上报完整 mod 列表**（modid + 版本）：

| 协议 | 通道 | 阶段 | 覆盖版本 |
|---|---|---|---|
| FML | `fml:handshake` | play | Forge / NeoForge 1.13 - 1.20.1 |
| FML | `fml:login` | configuration | Forge / NeoForge 1.20.2+（需 ProtocolLib 5.2+） |
| Fabric | `fabric:handshake` | play | Fabric 1.16.2 - 1.20.4 |
| Fabric | `fabric:login` | configuration | Fabric 1.20.5+（需 ProtocolLib 5.2+） |

插件拦截并解析握手包，提取 modid 列表与黑名单比对（如 `litematica`、`litematica-printer`、`servux`），
命中即走统一惩罚管线，并把**完整 mod 列表存为证据**（`history` 可查看）。
即使 mod 不注册任何插件通道也能精确识别。

**策略化**：每个 modid 可单独配置 `KICK / BAN / WARN / LOG` 与封禁时长（`banned-mod-ids` 下的 map 配置）。

**变化追踪**：每次握手把完整 mod 列表持久化到 `player_mods` 档案。
若玩家历史安装过禁用 mod、本次握手却未上报（疑似进服前卸载），触发 `change-action` 处理——堵住"卸了再进、进了再装"的绕过路径。

**交叉验证**（进服二次验证阶段）：mod 列表 × 通道 × Brand 三方互证：
- mod 列表含禁用 mod 但未注册关联通道 → 疑似通道注销欺骗（`xcheck:channel:*`）
- mod 加载器与 Brand 矛盾（如 Fabric mod 列表 + vanilla Brand）→ 疑似伪装（`xcheck:brand:*`）

## 菜单系统（版本自适应）

```
服务端版本检测（util/PaperVersion.kt）
│
├── 服务端 >= 1.21.6 且 Dialog API 类存在（Paper 1.21.7+）
│   └── 原生 Dialog 菜单（dialog/DialogAdminMenu.kt）
│       ├── 主菜单（multiAction）：通道管理 / 设置 / 重载配置 / 关闭
│       ├── 通道管理：添加通道（text 输入）、移除通道（text 输入）
│       └── 设置：控制台日志（bool 开关）、管理员通知（bool 开关）
│
└── 低版本服务端
    └── 箱子菜单（menu/ChestAdminMenu.kt，54 格）
        ├── 状态栏 / 禁用通道列表（点击移除）
        ├── 常用通道快捷添加
        ├── 日志开关 / 通知开关
        └── 重载 / 刷新 / 关闭
```

> Dialog 菜单通过反射延迟加载（`menu/MenuFactory.kt`），低版本服务端永远不会加载
> Dialog API 类，因此不会出现 `NoClassDefFoundError`。

## 构建

要求：JDK 21+（本机使用 JDK 25 构建）、Maven 3.8+

```bash
mvn clean package
```

**一条命令完成打包**：shade 把 kotlin-stdlib + gson + sqlite-jdbc 内置进 jar，无需额外安装依赖。

产物：`target/AntiLitematica-7.0.1.jar`（15MB，内置全部依赖）

如需适配其他 Paper 版本，修改 `pom.xml` 中的 `<paper.version>`：

```xml
<paper.version>26.2.build.111-stable</paper.version>
```

> 源码完全开源、类名未混淆，方便二次开发与审计。

## 安装

1. 将 `AntiLitematica-7.0.1.jar` 放入服务端 `plugins/` 目录
2. 重启服务端（或执行 `reload`）
3. 首次启动自动生成 `plugins/AntiLitematica/config.yml`

## 配置（config.yml）

```yaml
# 要检测的插件通道
banned-channels:
  - "servux:litematics"   # Litematica 投影（Fabric/Forge）
  - "schematica"          # Schematica

# 踢出消息（支持 & 颜色代码）
kick-message: "&c检测到你安装了不允许的 Mod（Litematica 投影），&7请移除后重新加入服务器！"

# 是否在控制台记录检测日志
log-detections: true

# 是否通知拥有 antilitematica.notify 权限的在线管理员
notify-admins: true
```

## 命令与权限

| 命令 | 说明 |
|---|---|
| `/antilitematica` 或 `/alt` | 打开管理菜单（自动选择 Dialog / 箱子） |
| `/antilitematica reload` | 重载配置（控制台可用） |
| `/antilitematica list` | 列出禁用通道与动作 |
| `/antilitematica add <通道> [动作]` | 添加禁用通道（KICK/BAN/WARN/LOG） |
| `/antilitematica remove <通道>` | 移除禁用通道 |
| `/antilitematica ban <玩家> <时长> [原因]` | 手动封禁（如 30d / permanent） |
| `/antilitematica unban <玩家>` | 解封 |
| `/antilitematica preset <模式>` | 切换预设（strict/normal/lite） |
| `/antilitematica stats` | 查看检测统计 |
| `/antilitematica history <玩家>` | 查看玩家检测记录 |
| `/antilitematica forgive <玩家>` | 标记误报豁免 |
| `/antilitematica version` | 查看版本与更新 |

| 权限 | 默认 | 说明 |
|---|---|---|
| `antilitematica.admin` | op | 使用管理菜单与命令 |
| `antilitematica.notify` | op | 收到玩家被检测处理的实时通知 |
| `antilitematica.bypass` | false | 豁免检测（测试号 / 白名单） |

## 项目结构

```
src/main/kotlin/icu/epochcraft/antilitematica/
├── AntiLitematica.kt              # 主类：生命周期、依赖装配
├── api/
│   ├── AntiLitematicaAPI.kt       # 公开 API（附属插件入口，静态单例）
│   ├── DetectionInfo.kt / DetectionListener.kt   # 检测回调
│   └── BanInfo.kt / ChannelConfig.kt              # API 数据模型
├── ban/
│   ├── BanManager.kt              # 封禁/解封/登录拦截/到期自动解封（后端双写）
│   ├── BanBackend.kt              # 封禁后端接口 + 工厂（auto 探测）
│   ├── InternalBanBackend.kt      # 内置 SQLite 后端
│   ├── LiteBansBackend.kt         # LiteBans 联动（全反射）
│   └── AdvancedBanBackend.kt      # AdvancedBan 联动（全反射）
├── command/
│   └── AntiLitematicaCommand.kt   # 12 个子命令 + Tab 补全
├── config/
│   ├── PluginConfig.kt            # 配置：通道动作/预设/封禁/渐进/防Printer/信号
│   └── LangManager.kt             # 多语言管理（lang/ 文件夹，回退链 zh_cn）
├── database/
│   ├── DetectionDatabase.kt       # SQLite：检测/封禁/误报/违规计数
│   ├── DetectionRecord.kt         # 检测记录模型
│   └── BanRecord.kt               # 封禁记录模型
├── detection/
│   ├── DetectionBus.kt            # 检测总线（统一惩罚管线）
│   ├── DetectionContext.kt / DetectionHandler.kt
│   ├── ModDetectionService.kt     # 通道检测（误报/冷却/发射）
│   ├── ModDetectionListener.kt    # 事件监听 + 二次验证 + Brand
│   ├── ActionType.kt / ChannelRegistry.kt / DetectionSource.kt
├── event/
│   └── DetectionEvent.kt          # 对外 Bukkit 事件（可取消）+ DetectionType
├── guard/
│   ├── PlacementGuard.kt          # 防 Printer（限速/射线/连续/视角）
│   └── CommandGuard.kt            # 命令防护（quick-paste 拦截）
├── integration/
│   ├── IntegrationManager.kt      # 反作弊联动（自动探测）
│   ├── GrimIntegration.kt / VulcanIntegration.kt / MatrixIntegration.kt
├── punish/
│   ├── GraduatedPunisher.kt       # 渐进惩罚（按次数升级）
│   ├── DetectionPunisher.kt       # 基础动作兜底 + 记录 + 通知
│   ├── ViolationTracker.kt / ViolationRecord.kt
│   ├── PunishmentAction.kt / PunishmentLevel.kt
├── signal/
│   ├── SignalFactory.kt           # ProtocolLib 探测 + 反射加载
│   ├── ProtocolLibSignalDetector.kt # EasyPlace/NBT 信号（全反射零依赖）
│   ├── HandshakeParser.kt          # FML/Fabric 握手 Mod 列表解析（纯逻辑）
│   ├── ModListHandshakeDetector.kt # Mod List 深度解析（全反射零依赖）
│   └── HandshakeDetectorFactory.kt # Mod List 检测器工厂（无 ProtocolLib 自动跳过）
├── dialog/ ─ menu/ ─ notify/ ─ papi/ ─ statistics/ ─ update/
└── util/                          # PaperVersion/MessageUtil/DurationParser/TokenBucket
```

## 文档

- [docs/API.md](docs/API.md) — 公开 API 开发指南（附属插件）
- [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) — 贡献指南
- [wiki/INSTALL.md](wiki/INSTALL.md) — 安装指南
- [wiki/CONFIG.md](wiki/CONFIG.md) — 配置指南
- [wiki/COMMANDS.md](wiki/COMMANDS.md) — 命令与权限
- [wiki/FAQ.md](wiki/FAQ.md) — 常见问题

## ⚖️ 开源许可

本项目**完全免费开源**，无授权码、无功能锁定、无混淆保护，所有功能开箱即用。

- ✅ 源码公开，可自由查看、学习、修改
- ✅ 可自由用于个人服务器、非商业用途（保留署名）
- ❌ **不接受任何商业用途**：禁止出售/转售、禁止用于商业服务器或付费开服、禁止任何形式的变相牟利（代购/定制/整合包收费/赞助解锁等）

完整条款见 [LICENSE](LICENSE)。违反者保留追究责任的权利。欢迎提交 Issue / PR 参与改进。
