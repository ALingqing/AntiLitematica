##  **⚠️ WARNING ⚠️**
## **YOU MUST DOWNLOAD PROTOCOLLIB FIRST! / 必须先下载 ProtocolLib 前置插件！**
## Without ProtocolLib, this plugin will NOT work! / 没有 ProtocolLib，本插件无法运行！
## **Download / 下载**: [SpigotMC](https://www.spigotmc.org/resources/protocollib.1997/) | [Hangar](https://hangar.papermc.io/dmulloy2/ProtocolLib) | [GitHub](https://github.com/dmulloy2/ProtocolLib/releases)

---

# AntiLitematica / 反投影辅助检测插件

> 🛡️ **Detects & Blocks Litematica/Printer on Your Server** | **检测并阻止 Litematica/Printer 投影打印**
> 
> ProtocolLib-based packet analysis | 基于 ProtocolLib 的数据包分析

---

## Features / 功能特性

| Feature / 功能 | Status / 状态 | Description / 描述 |
|:---------------|:-----------:|:-------------------|
| Servux Channel Detection / 频道检测 | ✅ | Detects `servux:litematics` registration / 检测 `servux:litematics` 频道注册 |
| Easy Place Detection / 快捷放置检测 | ✅ | Catches abnormal hit vectors (>1.5 rel) / 捕获异常点击向量（相对坐标>1.5） |
| NBT Query Block / NBT查询拦截 | ✅ | Blocks suspicious tag queries / 阻止可疑的标签查询 |
| Printer Prevention / 打印阻止 | ✅ | Enforces raytrace + speed limits / 强制射线检测+速率限制 |
| Low Overhead / 低性能开销 | ✅ | Async packet processing / 异步数据包处理 |

---

## How It Works / 工作原理

**English:**
This plugin uses **ProtocolLib** to intercept and analyze Minecraft protocol packets:

1. **Channel Registration Detection**: Detects when client registers `servux:litematics` (Litematica 1.21.11+ Servux integration)
2. **Easy Place Vector Analysis**: Calculates `hitVec - blockPos` relative coordinates; vanilla clients stay within `[0..1]`, Litematica's Easy Place exceeds `>2.0`
3. **NBT Query Monitoring**: Blocks unauthorized block entity/entity tag queries (debug features)
4. **Printer Prevention**: Enforces server-side raytrace validation and placement speed limits (default: 14 blocks/sec)

**简体中文:**
本插件使用 **ProtocolLib** 拦截并分析 Minecraft 协议数据包：

1. **频道注册检测**: 检测客户端注册 `servux:litematics`（Litematica 1.21.11+ 的 Servux 集成）
2. **快捷放置向量分析**: 计算 `hitVec - blockPos` 相对坐标；原版客户端保持在 `[0..1]`，Litematica 的 Easy Place 模式会超过 `>2.0`
3. **NBT 查询监控**: 阻止未授权的方块实体/实体标签查询（调试功能）
4. **打印阻止**: 强制服务端射线检测验证和放置速率限制（默认：14方块/秒）

---

## ⚠️ HARD DEPENDENCY / 硬性依赖

> **ProtocolLib is REQUIRED / ProtocolLib 是必需的**

| Download Source / 下载源 | URL |
|:-------------------------|:----|
| **SpigotMC (Stable / 稳定版)** | https://www.spigotmc.org/resources/protocollib.1997/ |
| **Hangar (PaperMC Official / Paper官方)** | https://hangar.papermc.io/dmulloy2/ProtocolLib |
| **GitHub Releases** | https://github.com/dmulloy2/ProtocolLib/releases |
| **Dev Builds (CI / 开发版)** | https://ci.dmulloy2.net/job/ProtocolLib/ |

**Supported Versions / 支持版本**: 1.8 - 1.21.8

---

## Installation / 安装方法

**English:**
1. **Install ProtocolLib FIRST** (see links above) / **先安装 ProtocolLib**（见上方链接）
2. Place `AntiLitematica.jar` into `/plugins` folder / 将 `AntiLitematica.jar` 放入 `/plugins` 文件夹
3. Restart server / 重启服务器
4. Configure `plugins/AntiLitematica/config.yml` / 配置 `plugins/AntiLitematica/config.yml`

**简体中文:**
1. **必须先安装 ProtocolLib**（见上方链接）
2. 将 `AntiLitematica.jar` 放入 `/plugins` 文件夹
3. 重启服务器
4. 配置 `plugins/AntiLitematica/config.yml`
