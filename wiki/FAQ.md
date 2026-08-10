# 常见问题（FAQ）

## 安装与兼容

### Q: 支持哪些服务端？
**A:** Paper / Spigot / Purpur 1.20.5+。Paper 1.21.7+ 使用原生 Dialog 管理菜单，旧版本自动回退箱子菜单。

### Q: 需要安装 ProtocolLib 吗？
**A:** 不需要。基础通道检测、封禁、通知等功能零依赖。ProtocolLib 仅用于可选的 EasyPlace / NBT 信号检测，不装也能正常使用其他功能。

### Q: 会不会误伤 Forge / Fabric 玩家？
**A:** 不会。`fml:*` / `fabric:*` 环境通道默认只记录不处理（`detect-forge-handshake` / `detect-fabric-api`），确保正常使用 Forge/Fabric 的玩家不被误踢。

## 使用

### Q: 怎么测试插件是否工作？
1. 在测试客户端安装 Litematica
2. 加入服务器
3. 执行 `/antilitematica stats` 或 `/antilitematica history <玩家>` 查看检测记录
4. 控制台也会输出检测日志

### Q: 检测到正常玩家（误报）怎么办？
执行：

```
/antilitematica forgive <玩家>
```

该玩家命中过的所有通道将被标记为误报，不再拦截。也可给玩家 `antilitematica.bypass` 权限完全豁免检测。

### Q: 想封禁玩家但不想踢出？
将对应通道动作改为 `LOG` 或 `WARN`：

```
/antilitematica add servux:litematics LOG
```

### Q: 怎么切换语言？
修改 `config.yml` 的 `language`（如 `en_us`），然后 `/antilitematica reload`。

### Q: 封禁了玩家，用的哪个封禁系统？
由 `ban-backend` 决定：`auto` 模式下自动探测 LiteBans → AdvancedBan → 内置 SQLite。外部后端启用时采用双写，`/antilitematica list`、`stats`、登录拦截保持一致。

## 封禁相关

### Q: 自动封禁多久到期？
由 `auto-ban.duration` 决定（默认 30 天），到期自动解封，无需人工操作。

### Q: 被误封了怎么办？
管理员执行 `/antilitematica unban <玩家>` 解封。如果封禁来自 LiteBans / AdvancedBan 联动，也会同步解封。

### Q: 想立刻放行某个玩家？
```bash
/antilitematica unban <玩家>
```

## 联动

### Q: 支持哪些反作弊联动？
**A:** GrimAC / Vulcan / Matrix，通过 `anti-cheat-integration` 配置，自动探测，未安装对应反作弊时自动降级。

### Q: 怎么配置 Discord / QQ 通知？
在 `config.yml` 的 `webhook` 节配置：
- Discord：填入 Webhook 地址
- QQ：OneBot v11 / NapCat 兼容，配置 `base-url`、`access-token`、`group-id`（纯出站调用，无需开放端口）

## 开发

### Q: 想开发附属插件？
见 [docs/API.md](../docs/API.md)，公开 API 提供检测联动、封禁管理、通道配置、统计查询等能力。

### Q: 插件报错 / 有建议？
到 [GitHub Issues](https://github.com/EpochcraftMC/AntiLitematica/issues) 反馈，或加 QQ 群：1102137231
