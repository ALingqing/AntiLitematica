# 配置指南（config.yml）

配置文件位于 `plugins/AntiLitematica/config.yml`，修改后执行 `/antilitematica reload` 生效。

## 基础

```yaml
# 语言（lang/ 文件夹内的语言文件）
# 内置: zh_cn(简体中文) / zh_tw(繁體中文) / en_us(English)
language: zh_cn

# 预设模式: strict(严格) / normal(标准) / lite(轻量)
mode: normal
```

| 模式 | 自动封禁 | Forge/Fabric 环境记录 | 适用场景 |
|---|---|---|---|
| strict | 开启（2 次 KICK 转封禁 30 天） | 记录 | 反作弊严格的服务器 |
| normal | 关闭 | 记录 | 默认推荐 |
| lite | 关闭 | 不记录 | 仅需基础通道检测 |

## 检测通道

```yaml
channels:
  "servux:litematics":   # Litematica 投影（Fabric/Forge）
    action: KICK
    ban-duration: 30d
  "schematica":          # Schematica 旧版
    action: KICK
    ban-duration: 30d
```

| 动作 | 说明 |
|---|---|
| KICK | 踢出玩家 |
| BAN | 封禁（时长由 ban-duration 决定） |
| WARN | 仅聊天警告 |
| LOG | 仅记录日志，不做处理 |

## 检测行为

```yaml
# 踢出消息（支持 & 颜色代码）
kick-message: "&c检测到你安装了不允许的 Mod（Litematica 投影），&7请移除后重新加入服务器！"

# 控制台记录检测日志
log-detections: true

# 通知在线管理员（需 antilitematica.notify 权限）
notify-admins: true

# 二次验证延迟（tick）：进服后延迟复查，防注册-注销绕过
recheck-delay-ticks: 10

# 记录 Forge/Fabric 环境通道（仅记录，不踢出，防误伤）
detect-forge-handshake: true
detect-fabric-api: true

# 客户端 Brand 黑名单（命中即踢，谨慎开启）
brand-blocklist: []

# 同一玩家重复检测冷却（毫秒）
detection-cooldown-ms: 5000
```

## 自动封禁

```yaml
auto-ban:
  enabled: false
  kicks-before-ban: 3
  duration: 30d
```

## 渐进惩罚

```yaml
graduated-punishment:
  enabled: false
  window-minutes: 1440
  levels:
    1: { action: WARN, reason: "&e警告消息", broadcast: false, staff-alert: true }
    2: { action: KICK, reason: "&c踢出消息", broadcast: false, staff-alert: true }
    3: { action: TEMPBAN, duration: 1d, reason: "&c临时封禁", broadcast: true, staff-alert: true }
    4: { action: BAN, reason: "&c永久封禁", broadcast: true, staff-alert: true }
  exceed-max:
    action: BAN
    reason: "&c屡次使用投影 mod，永久封禁！"
```

## 防 Printer（自动放置检测）

```yaml
anti-printer:
  enabled: false
  max-blocks-per-second: 14   # 每秒最大放置数（令牌桶限速）
  apply-to-creative: false    # 是否检测创造模式
  enforce-raytrace: true      # 射线校验
  detect-consecutive-same-type: true
  consecutive-same-type-threshold: 8
  consecutive-window-ms: 3000
  detect-no-look-change: true # 视角不变检测
  reach-survival: 4.5
  reach-creative: 5.0
  extra-reach-allowance: 0.5
```

## 命令防护

```yaml
command-guard:
  enabled: false
  allowed-commands: []       # 永远放行的命令
  blocked-commands: []       # 拦截命令（如 /setblock /fill /clone）
  max-per-window: 8
  window-ms: 2000
```

## ProtocolLib 信号检测

```yaml
signals:
  easy-place:
    enabled: false
    cancel: true
    rel-min: -0.5
    rel-max: 1.5
    min-consecutive: 3
  nbt-query:
    enabled: false
    allow-op: true
    cancel: true
    threshold: 15
```

## FML / Fabric Mod List 深度解析

客户端在握手阶段必定上报完整 mod 列表（modid + 版本），即使 mod 不注册插件通道
（或客户端禁用通道上报）也能精确识别，与通道检测互补。

> 需要 ProtocolLib。`fml:login` / `fabric:login`（1.20.2+ / 1.20.5+）发生在
> configuration 阶段，还需 ProtocolLib 5.2+；老版本服务端自动降级到 play 阶段握手。

```yaml
mod-list:
  enabled: true
  # 禁用 modid -> 策略（小写；支持按 mod 定制动作与封禁时长）
  banned-mod-ids:
    litematica:
      action: KICK
      ban-duration: 30d
    litematica-printer:
      action: KICK
      ban-duration: 30d
    malilib:
      action: KICK
      ban-duration: 30d
    servux:
      action: KICK
      ban-duration: 30d
    schematica:
      action: KICK
      ban-duration: 30d
    minihud:
      action: KICK
      ban-duration: 30d
    tweakeroo:
      action: KICK
      ban-duration: 30d
    itemscroller:
      action: KICK
      ban-duration: 30d
  # 变化追踪：历史安装过禁用 mod，本次握手未上报（疑似进服前卸载）-> 处理
  detect-mod-changes: true
  change-action: WARN
  # 交叉验证：mod 列表与通道 / Brand 互相矛盾 -> 处理
  #   通道缺失 = 疑似通道注销欺骗；Brand 矛盾 = 疑似伪装
  detect-xcheck: true
  xcheck-action: KICK
  # 未命中黑名单也记录完整 mod 列表（审计用，仅 LOG 不处罚）
  log-all-mod-lists: true
```

> 兼容旧格式：`banned-mod-ids` 也可写成字符串列表（`- "litematica"`），统一默认 KICK / 30d。
> 完整 mod 列表会作为证据写入数据库（`/antilitematica history <玩家>` 可查看），
> 并持久化到 `player_mods` 档案供变化追踪跨会话对比。

## 反作弊集成

```yaml
# auto / grim / vulcan / matrix（未安装自动降级）
anti-cheat-integration: auto
```

## 封禁后端联动

```yaml
# auto / internal / litebans / advancedban
ban-backend: auto
```

| 值 | 行为 |
|---|---|
| auto | 自动探测：LiteBans → AdvancedBan → 内置 SQLite |
| litebans | 联动 LiteBans（双写，未安装回退内置） |
| advancedban | 联动 AdvancedBan（双写，未安装回退内置） |
| internal | 强制内置 SQLite |

> 联动外部后端时采用双写：外部封禁插件 + 本地数据库，`list` / `stats` / 登录拦截保持统一。

## 通知（Discord / QQ）

```yaml
webhook:
  # Discord Webhook 地址（留空关闭）
  discord: ""
  # QQ 群通知（OneBot v11 / NapCat 兼容，纯出站）
  onebot:
    enabled: false
    base-url: "http://127.0.0.1:3001"
    access-token: ""
    group-id: 0
```

## 更新检查

```yaml
update-checker: true
update-repo: "EpochcraftMC/AntiLitematica"
```

## 多世界

按世界覆盖检测设置（世界名不区分大小写），未配置的世界使用全局设置：

```yaml
worlds:
  world_creative:
    detection-enabled: false        # 该世界完全禁用检测
    anti-printer-enabled: false     # 禁用防 Printer
    command-guard-enabled: false    # 禁用命令防护
    graduated-enabled: false        # 禁用渐进惩罚
```

| 覆盖项 | 说明 |
|---|---|
| `detection-enabled` | 该世界是否启用检测（null = 全局） |
| `anti-printer-enabled` | 该世界是否启用防 Printer |
| `command-guard-enabled` | 该世界是否启用命令防护 |
| `graduated-enabled` | 该世界是否启用渐进惩罚 |

> 适用于创造服 / 建筑服 / 小游戏服等需要放行的世界。

## 多语言

语言文件位于 `plugins/AntiLitematica/lang/`，内置：

| 文件 | 语言 |
|---|---|
| zh_cn.yml | 简体中文（默认/兜底） |
| zh_tw.yml | 繁體中文 |
| en_us.yml | English |

- 可自由修改文案（支持 `&` 颜色代码和 `{占位符}`）
- 可复制文件新增语言（如 `ja_jp.yml`），然后在 config.yml 的 `language` 填入文件名前缀
- 切换语言后执行 `/antilitematica reload`
