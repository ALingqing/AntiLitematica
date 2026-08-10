# 安装指南（AntiLitematica 7.0.0）

## 环境要求

| 项目 | 要求 |
|---|---|
| 服务端 | Paper / Spigot / Purpur **1.20.5+**（推荐最新 Paper） |
| Java | **21+** |
| ProtocolLib | 可选（仅 EasyPlace / NBT 信号检测需要） |
| PlaceholderAPI | 可选（提供 `%antilitematica_*%` 占位符） |
| LiteBans / AdvancedBan | 可选（作为封禁后端联动） |

## 安装步骤

1. 从 [Releases 页面](https://github.com/EpochcraftMC/AntiLitematica/releases) 下载最新的 `AntiLitematica-7.0.0.jar`
2. 停止服务器
3. 将 jar 放入 `plugins/` 目录
4. 启动服务器
5. 首次启动自动生成：
   - `plugins/AntiLitematica/config.yml`（配置文件）
   - `plugins/AntiLitematica/lang/`（语言文件：zh_cn / zh_tw / en_us）

## 验证安装

执行命令：

```
/antilitematica version
```

或打开管理菜单：

```
/antilitematica
```

显示插件版本与更新状态即安装成功。

## 快速开始

### 基础防护（检测到投影 mod 即踢出）

```yaml
# config.yml
channels:
  "servux:litematics":
    action: KICK
    ban-duration: 30d
  "schematica":
    action: KICK
    ban-duration: 30d
```

### 自动封禁（推荐）

```yaml
# config.yml
auto-ban:
  enabled: true        # 累计 KICK 达阈值自动转封禁
  kicks-before-ban: 3  # 踢出 3 次后转封禁
  duration: 30d        # 封禁时长
```

### 渐进惩罚（按违规次数逐级升级）

```yaml
# config.yml
graduated-punishment:
  enabled: true
  window-minutes: 1440   # 24 小时内计数
  levels:
    1:
      action: WARN
      reason: "&e检测到使用投影 mod，请立即退出！"
    2:
      action: KICK
      reason: "&c再次使用投影 mod，已被踢出！"
    3:
      action: TEMPBAN
      duration: 1d
      reason: "&c多次使用投影 mod，封禁 1 天！"
    4:
      action: BAN
      reason: "&c屡次使用投影 mod，永久封禁！"
```

## 升级

1. 从 [Releases](https://github.com/EpochcraftMC/AntiLitematica/releases) 下载新版本 jar
2. 替换 `plugins/` 目录中的旧 jar
3. 重启服务器（或执行 `/antilitematica reload`）

> 配置文件向后兼容，旧配置会自动读取；新增配置项使用默认值。
