# 命令与权限

## 命令

| 命令 | 说明 |
|---|---|
| `/antilitematica` 或 `/alt` | 打开管理菜单（Paper 1.21.7+ 原生 Dialog，旧版自动回退箱子菜单） |
| `/antilitematica reload` | 重载配置与语言（控制台可用） |
| `/antilitematica list` | 列出禁用通道与动作、预设模式、自动封禁状态 |
| `/antilitematica add <通道> [动作]` | 添加禁用通道（KICK/BAN/WARN/LOG） |
| `/antilitematica remove <通道>` | 移除禁用通道 |
| `/antilitematica ban <玩家> <时长> [原因]` | 手动封禁（如 `30d` / `permanent`） |
| `/antilitematica unban <玩家>` | 解封 |
| `/antilitematica preset <模式>` | 切换预设（strict / normal / lite） |
| `/antilitematica stats` | 查看检测统计 |
| `/antilitematica history <玩家>` | 查看玩家检测记录 |
| `/antilitematica forgive <玩家>` | 标记误报豁免（该玩家命中过的通道不再拦截） |
| `/antilitematica version` | 查看版本与更新状态 |

命令别名：`/alt`、`/alitematica`

## 权限

| 权限 | 默认 | 说明 |
|---|---|---|
| `antilitematica.admin` | op | 使用管理菜单与命令 |
| `antilitematica.notify` | op | 收到玩家被检测处理的实时通知 |
| `antilitematica.bypass` | false | 豁免检测（测试号 / 白名单） |

## 使用示例

```bash
# 添加通道并指定动作
/antilitematica add servux:litematics BAN

# 手动封禁 7 天
/antilitematica ban Steve 7d 使用投影 mod

# 永久封禁
/antilitematica ban Alex permanent

# 切换严格模式
/antilitematica preset strict

# 查看统计
/antilitematica stats
```
