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
