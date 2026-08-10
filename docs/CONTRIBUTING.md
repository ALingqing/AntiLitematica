# 贡献指南

感谢你愿意为 AntiLitematica 贡献！以下是参与开发的指引。

## 环境要求

- JDK 21+
- Maven 3.8+
- （可选）IntelliJ IDEA（推荐）

## 构建

```bash
mvn clean package
```

产物：`target/AntiLitematica-7.0.1.jar`（shade 打包，内置 kotlin-stdlib / gson / sqlite-jdbc / bstats）

## 项目结构

```
src/main/kotlin/icu/epochcraft/antilitematica/
├── AntiLitematica.kt          # 主类：生命周期、依赖装配
├── api/                       # 公开 API（附属插件入口）
├── ban/                       # 封禁管理 + 后端联动（内置/LiteBans/AdvancedBan）
├── command/                   # 命令与 Tab 补全
├── config/                    # 配置 + 多语言管理
├── database/                  # SQLite 持久化
├── detection/                 # 检测总线与通道检测
├── event/                     # Bukkit 事件（DetectionEvent）
├── guard/                     # 防 Printer / 命令防护
├── integration/               # 反作弊联动（Grim/Vulcan/Matrix）
├── notify/                    # Discord / QQ 通知（纯出站）
├── punish/                    # 渐进惩罚 / 基础动作
├── signal/                    # ProtocolLib 信号检测（全反射）
├── statistics/                # 统计 + bStats
└── update/                    # 更新检查器
```

## 开发规范

- **语言**：Kotlin，与现有代码风格保持一致（KDoc 注释、`@author 阿清`）
- **消息文案**：一律走 `LangManager`（`lang/` 文件夹），不要在代码中硬编码玩家可见消息
- **对外扩展**：面向附属插件的功能放进 `api/` 包，保持 Java 友好（getXxx 命名）
- **联动插件**：一律反射调用（零编译期依赖），检测不到时优雅降级
- **面向玩家的文档**：新增配置/功能后同步更新 `wiki/` 与 `README.md`

## 提交 PR

1. Fork 本仓库并创建功能分支
2. 提交改动，写清晰的提交信息
3. 确保 `mvn package` 构建通过
4. 发起 Pull Request 到 `main` 分支

## 发布版本

维护者发布流程：

```bash
git tag vX.Y.Z
git push origin vX.Y.Z
```

GitHub Actions 会自动构建并发布 Release（附 jar），插件内置更新检查器将检测到新版本。

## 许可

本项目**不接受任何商业用途**，详见 [LICENSE](../LICENSE)。
