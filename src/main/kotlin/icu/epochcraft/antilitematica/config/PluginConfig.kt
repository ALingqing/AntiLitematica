package icu.epochcraft.antilitematica.config

import icu.epochcraft.antilitematica.AntiLitematica
import icu.epochcraft.antilitematica.detection.ActionType
import icu.epochcraft.antilitematica.punish.PunishmentAction
import icu.epochcraft.antilitematica.punish.PunishmentLevel
import icu.epochcraft.antilitematica.util.DurationParser

/**
 * 插件配置（扩展版）：
 *   - 预设模式（strict / normal / lite）
 *   - 通道 -> 动作映射（KICK / BAN / WARN / LOG）
 *   - 自动封禁 / 二次验证 / Brand 拦截 / 环境识别
 *   - 通知（Discord Webhook / QQ OneBot，纯出站）
 *   - 更新检查
 *
 * @author 阿清
 */
class PluginConfig(
    private val plugin: AntiLitematica,
    val lang: LangManager,
) {

    // ---------------- 预设模式 ----------------

    enum class Mode(val displayName: String) {
        STRICT("严格模式"),
        NORMAL("标准模式"),
        LITE("轻量模式");

        companion object {
            fun parse(raw: String?): Mode =
                entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: NORMAL
        }
    }

    /** 当前预设模式 */
    var mode: Mode = Mode.NORMAL
        private set

    // ---------------- 通道与动作 ----------------

    /** 通道动作配置 */
    data class ChannelActionConfig(
        val action: ActionType = ActionType.KICK,
        val banDuration: Long = 86_400_000L,
    )

    /** 禁用通道 -> 动作（key 小写） */
    val channels: MutableMap<String, ChannelActionConfig> = linkedMapOf()

    /** 踢出消息 */
    var kickMessage: String = "&c检测到你安装了不允许的 Mod（Litematica 投影），&7请移除后重新加入服务器！".replace('&', '§')
        private set

    /** 是否在控制台记录检测日志 */
    var logDetections: Boolean = true
        private set

    /** 是否通知在线管理员 */
    var notifyAdmins: Boolean = true
        private set

    // ---------------- 检测增强 ----------------

    /** 二次验证延迟（tick） */
    var recheckDelayTicks: Long = 10
        private set

    /** 是否记录 Forge 握手通道信息（仅记录，不踢） */
    var detectForgeHandshake: Boolean = true
        private set

    /** 是否记录 Fabric 环境通道信息（仅记录，不踢） */
    var detectFabricApi: Boolean = true
        private set

    /** 客户端 Brand 黑名单（如 fabric / forge / lunar），命中即踢（默认空，谨慎开启） */
    val brandBlocklist: MutableSet<String> = mutableSetOf()

    /** 同一玩家短时间内的重复检测冷却（毫秒），避免反复踢 */
    var detectionCooldownMillis: Long = 5_000
        private set

    // ---------------- 自动封禁 ----------------

    /** 自动封禁开关 */
    var autoBanEnabled: Boolean = false
        private set

    /** 累计 KICK 多少次后转为封禁 */
    var kicksBeforeBan: Int = 3
        private set

    /** 自动封禁默认时长 */
    var autoBanDuration: Long = 30 * 86_400_000L
        private set

    // ---------------- 渐进惩罚 ----------------

    /** 渐进惩罚配置 */
    data class GraduatedConfig(
        val enabled: Boolean = false,
        /** 按违规次数逐级触发的惩罚 */
        val levels: List<PunishmentLevel> = defaultLevels(),
        /** 超过最高级后的惩罚 */
        val exceedMax: PunishmentLevel = PunishmentLevel(PunishmentAction.BAN, "&c屡次使用投影 mod，永久封禁！", -1, true, true),
        /** 违规计数窗口（分钟），窗口外计数重置 */
        val windowMinutes: Long = 1440,
    ) {
        companion object {
            /** 默认四级：警告 → 踢出 → 临时封禁 → 封禁 */
            fun defaultLevels(): List<PunishmentLevel> = listOf(
                PunishmentLevel(PunishmentAction.WARN, "&e检测到使用投影 mod，请立即退出！", 0, false, true),
                PunishmentLevel(PunishmentAction.KICK, "&c再次使用投影 mod，已被踢出！", 0, false, true),
                PunishmentLevel(PunishmentAction.TEMPBAN, "&c多次使用投影 mod，封禁 1 天！", 86_400_000L, true, true),
                PunishmentLevel(PunishmentAction.BAN, "&c屡次使用投影 mod，永久封禁！", -1, true, true),
            )
        }
    }

    var graduatedPunishment: GraduatedConfig = GraduatedConfig()
        private set

    // ---------------- 防 Printer ----------------

    data class AntiPrinterConfig(
        val enabled: Boolean = false,
        /** 每秒最大放置数（令牌桶） */
        val maxBlocksPerSecond: Int = 14,
        /** 是否检测创造模式 */
        val applyToCreative: Boolean = false,
        /** 射线校验 */
        val enforceRaytrace: Boolean = true,
        /** 连续同类型方块检测 */
        val detectConsecutiveSameType: Boolean = true,
        val consecutiveSameTypeThreshold: Int = 8,
        val consecutiveWindowMs: Long = 3000,
        /** 视角不变检测 */
        val detectNoLookChange: Boolean = true,
        val reachSurvival: Double = 4.5,
        val reachCreative: Double = 5.0,
        val extraReachAllowance: Double = 0.5,
    )

    var antiPrinter: AntiPrinterConfig = AntiPrinterConfig()
        private set

    // ---------------- 命令防护 ----------------

    data class CommandGuardConfig(
        val enabled: Boolean = false,
        /** 放行命令（精确或前缀+空格） */
        val allowedCommands: List<String> = emptyList(),
        /** 拦截命令列表（默认空，按服配置） */
        val blockedCommands: List<String> = emptyList(),
        /** 窗口内最大执行次数（超限判定为 quick-paste） */
        val maxPerWindow: Int = 8,
        val windowMs: Long = 2000,
    )

    var commandGuard: CommandGuardConfig = CommandGuardConfig()
        private set

    // ---------------- ProtocolLib 信号检测 ----------------

    data class SignalsConfig(
        /** EasyPlace 易放模式检测（需 ProtocolLib） */
        val easyPlaceEnabled: Boolean = false,
        val easyPlaceCancel: Boolean = true,
        /** 命中向量偏移合理范围 */
        val easyPlaceRelMin: Double = -0.5,
        val easyPlaceRelMax: Double = 1.5,
        /** 连续异常命中 N 次才触发（防误伤） */
        val easyPlaceMinConsecutive: Int = 3,
        /** NBT 查询风暴检测（默认关闭，修复旧版误伤） */
        val nbtQueryEnabled: Boolean = false,
        val nbtQueryAllowOp: Boolean = true,
        val nbtQueryCancel: Boolean = true,
        /** 窗口内查询阈值 */
        val nbtQueryThreshold: Int = 15,
    )

    var signals: SignalsConfig = SignalsConfig()
        private set

    // ---------------- FML / Fabric Mod List 深度解析 ----------------

    data class ModListConfig(
        /** 是否启用（需 ProtocolLib） */
        val enabled: Boolean = false,
        /** modid(小写) -> 策略（action + 封禁时长），支持按 mod 定制 */
        val bannedMods: Map<String, ChannelActionConfig> = defaultBannedMods(),
        /** 变化追踪：历史有禁用 mod 但本次握手未上报 -> 处理（防进服前卸 mod） */
        val detectModChanges: Boolean = true,
        val changeAction: ActionType = ActionType.WARN,
        /** 交叉验证：mod 列表与通道 / Brand 互相矛盾 -> 处理（抓伪装与通道注销欺骗） */
        val detectXCheck: Boolean = true,
        val xcheckAction: ActionType = ActionType.KICK,
        /** 未命中黑名单也记录 mod 列表（审计，LOG 不处罚） */
        val logAllModLists: Boolean = true,
    ) {
        companion object {
            /** 默认禁用 modid：投影及 masa 系常被一起携带 */
            fun defaultBannedMods(): Map<String, ChannelActionConfig> = linkedMapOf(
                "litematica" to ChannelActionConfig(),
                "litematica-printer" to ChannelActionConfig(),
                "malilib" to ChannelActionConfig(),
                "servux" to ChannelActionConfig(),
                "schematica" to ChannelActionConfig(),
                "minihud" to ChannelActionConfig(),
                "tweakeroo" to ChannelActionConfig(),
                "itemscroller" to ChannelActionConfig(),
            )
        }

        /** 某 modid 的动作（未配置返回默认 KICK） */
        fun actionFor(modId: String): ActionType =
            bannedMods[modId.lowercase()]?.action ?: ActionType.KICK

        /** 某 modid 的封禁时长（未配置返回 30 天） */
        fun banDurationFor(modId: String): Long =
            bannedMods[modId.lowercase()]?.banDuration ?: (30 * 86_400_000L)
    }

    var modList: ModListConfig = ModListConfig()
        private set

    // ---------------- 反作弊集成 ----------------

    /** 优先接入的反作弊：auto / grim / vulcan / matrix */
    var antiCheatIntegration: String = "auto"
        private set

    // ---------------- 封禁后端联动 ----------------

    /** 封禁后端：auto / internal / litebans / advancedban */
    var banBackend: String = "auto"
        private set

    // ---------------- 兼容与联动 ----------------

    /** 基岩版玩家豁免（Geyser/Floodgate，基岩版无 Litematica） */
    var bedrockExempt: Boolean = true
        private set

    /** MasaMods 兼容：Tweakeroo 模式（放宽防误报阈值） */
    var tweakerooMode: Boolean = false
        private set

    /** 跨服同步（BungeeCord/Velocity 代理环境） */
    var crossServerSyncEnabled: Boolean = false
        private set

    // ---------------- 多世界兼容 ----------------

    /** 按世界覆盖配置（世界名小写 -> 覆盖项，null = 跟随全局） */
    val worldOverrides: MutableMap<String, WorldOverride> = linkedMapOf()

    /**
     * 单世界覆盖配置：字段为 null 时跟随全局设置。
     */
    data class WorldOverride(
        /** 该世界是否启用检测（null = 全局） */
        val detectionEnabled: Boolean? = null,
        /** 该世界是否启用防 Printer（null = 全局） */
        val antiPrinterEnabled: Boolean? = null,
        /** 该世界是否启用命令防护（null = 全局） */
        val commandGuardEnabled: Boolean? = null,
        /** 该世界是否启用渐进惩罚（null = 全局） */
        val graduatedEnabled: Boolean? = null,
    )

    /** 查询某世界的覆盖配置（未配置返回 null） */
    fun worldOverride(worldName: String?): WorldOverride? =
        worldName?.let { worldOverrides[it.lowercase()] }

    /** 读取可空的布尔配置（不存在返回 null） */
    private fun org.bukkit.configuration.ConfigurationSection?.optBool(path: String): Boolean? =
        if (this != null && contains(path)) getBoolean(path) else null

    // ---------------- 通知（纯出站） ----------------

    /** Discord Webhook 地址（出站 HTTPS，无需开端口） */
    var discordWebhookUrl: String = ""
        private set

    /** QQ OneBot（NapCat 兼容）出站 HTTP 上报 */
    var onebotEnabled: Boolean = false
        private set
    var onebotBaseUrl: String = "http://127.0.0.1:3001"
        private set
    var onebotAccessToken: String = ""
        private set
    var onebotGroupId: Long = 0
        private set

    // ---------------- 更新 ----------------

    var updateCheckerEnabled: Boolean = true
        private set
    var updateRepo: String = "EpochcraftMC/AntiLitematica"
        private set

    // ---------------- 加载 / 保存 ----------------

    /** 从 config.yml 加载 */
    fun reload() {
        plugin.reloadConfig()
        val yaml = plugin.config

        // 语言切换（/antilitematica reload 时生效）
        lang.load(yaml.getString("language"))

        mode = Mode.parse(yaml.getString("mode"))

        // 通道配置：优先 channels 节，兼容旧 banned-channels 列表
        channels.clear()
        val rawChannels = yaml.getConfigurationSection("channels")
        if (rawChannels != null) {
            rawChannels.getKeys(false).forEach { name ->
                val normalized = name.lowercase()
                val cfg = rawChannels.getConfigurationSection(name)
                val action = ActionType.parse(cfg?.getString("action"))
                val banDuration = DurationParser.parseMillis(cfg?.getString("ban-duration"), 86_400_000L)
                channels[normalized] = ChannelActionConfig(action, banDuration)
            }
        } else {
            yaml.getStringList("banned-channels").forEach { name ->
                val normalized = name.trim().lowercase()
                if (normalized.isNotEmpty()) channels[normalized] = ChannelActionConfig()
            }
        }

        kickMessage = (yaml.getString("kick-message") ?: "&c检测到你安装了不允许的 Mod").replace('&', '§')
        logDetections = yaml.getBoolean("log-detections", true)
        notifyAdmins = yaml.getBoolean("notify-admins", true)

        recheckDelayTicks = yaml.getLong("recheck-delay-ticks", 10)
        detectForgeHandshake = yaml.getBoolean("detect-forge-handshake", true)
        detectFabricApi = yaml.getBoolean("detect-fabric-api", true)
        brandBlocklist.clear()
        brandBlocklist += yaml.getStringList("brand-blocklist").map { it.lowercase() }
        detectionCooldownMillis = yaml.getLong("detection-cooldown-ms", 5_000)

        autoBanEnabled = yaml.getBoolean("auto-ban.enabled", mode == Mode.STRICT)
        kicksBeforeBan = yaml.getInt("auto-ban.kicks-before-ban", 3)
        autoBanDuration = DurationParser.parseMillis(yaml.getString("auto-ban.duration"), 30 * 86_400_000L)

        // 渐进惩罚
        val gpSection = yaml.getConfigurationSection("graduated-punishment")
        graduatedPunishment = if (gpSection != null) {
            val levels = mutableListOf<PunishmentLevel>()
            gpSection.getConfigurationSection("levels")?.getKeys(false)?.forEach { key ->
                levels += parsePunishmentLevel(
                    gpSection.getConfigurationSection("levels.$key"),
                    "&c检测到使用投影 mod",
                )
            }
            val exceed = parsePunishmentLevel(
                gpSection.getConfigurationSection("exceed-max"),
                "&c屡次使用投影 mod，永久封禁！",
            )
            GraduatedConfig(
                enabled = gpSection.getBoolean("enabled", false),
                levels = levels.ifEmpty { GraduatedConfig.defaultLevels() },
                exceedMax = exceed,
                windowMinutes = gpSection.getLong("window-minutes", 1440),
            )
        } else {
            GraduatedConfig()
        }

        // 防 Printer
        val ap = yaml.getConfigurationSection("anti-printer")
        antiPrinter = if (ap != null) {
            AntiPrinterConfig(
                enabled = ap.getBoolean("enabled", false),
                maxBlocksPerSecond = ap.getInt("max-blocks-per-second", 14),
                applyToCreative = ap.getBoolean("apply-to-creative", false),
                enforceRaytrace = ap.getBoolean("enforce-raytrace", true),
                detectConsecutiveSameType = ap.getBoolean("detect-consecutive-same-type", true),
                consecutiveSameTypeThreshold = ap.getInt("consecutive-same-type-threshold", 8),
                consecutiveWindowMs = ap.getLong("consecutive-window-ms", 3000),
                detectNoLookChange = ap.getBoolean("detect-no-look-change", true),
                reachSurvival = ap.getDouble("reach-survival", 4.5),
                reachCreative = ap.getDouble("reach-creative", 5.0),
                extraReachAllowance = ap.getDouble("extra-reach-allowance", 0.5),
            )
        } else {
            AntiPrinterConfig()
        }

        // 命令防护
        val cg = yaml.getConfigurationSection("command-guard")
        commandGuard = if (cg != null) {
            CommandGuardConfig(
                enabled = cg.getBoolean("enabled", false),
                allowedCommands = cg.getStringList("allowed-commands"),
                blockedCommands = cg.getStringList("blocked-commands"),
                maxPerWindow = cg.getInt("max-per-window", 8),
                windowMs = cg.getLong("window-ms", 2000),
            )
        } else {
            CommandGuardConfig()
        }

        // ProtocolLib 信号检测
        val sg = yaml.getConfigurationSection("signals")
        signals = if (sg != null) {
            SignalsConfig(
                easyPlaceEnabled = sg.getBoolean("easy-place.enabled", false),
                easyPlaceCancel = sg.getBoolean("easy-place.cancel", true),
                easyPlaceRelMin = sg.getDouble("easy-place.rel-min", -0.5),
                easyPlaceRelMax = sg.getDouble("easy-place.rel-max", 1.5),
                easyPlaceMinConsecutive = sg.getInt("easy-place.min-consecutive", 3),
                nbtQueryEnabled = sg.getBoolean("nbt-query.enabled", false),
                nbtQueryAllowOp = sg.getBoolean("nbt-query.allow-op", true),
                nbtQueryCancel = sg.getBoolean("nbt-query.cancel", true),
                nbtQueryThreshold = sg.getInt("nbt-query.threshold", 15),
            )
        } else {
            SignalsConfig()
        }

        // FML / Fabric Mod List 深度解析
        val ml = yaml.getConfigurationSection("mod-list")
        modList = if (ml != null) {
            val banned = linkedMapOf<String, ChannelActionConfig>()
            // 兼容两种格式：
            //   map:  litematica: { action: KICK, ban-duration: 30d }
            //   list: - "litematica"（默认 KICK / 30d）
            val rawMap = ml.getConfigurationSection("banned-mod-ids")
            if (rawMap != null) {
                rawMap.getKeys(false).forEach { modId ->
                    val m = rawMap.getConfigurationSection(modId)
                    banned[modId.lowercase()] = ChannelActionConfig(
                        action = ActionType.parse(m?.getString("action")),
                        banDuration = DurationParser.parseMillis(m?.getString("ban-duration"), 30 * 86_400_000L),
                    )
                }
            } else {
                ml.getStringList("banned-mod-ids").forEach { id ->
                    val normalized = id.trim().lowercase()
                    if (normalized.isNotEmpty()) banned[normalized] = ChannelActionConfig()
                }
            }
            ModListConfig(
                enabled = ml.getBoolean("enabled", false),
                bannedMods = banned.ifEmpty { ModListConfig.defaultBannedMods() },
                detectModChanges = ml.getBoolean("detect-mod-changes", true),
                changeAction = ActionType.parse(ml.getString("change-action")),
                detectXCheck = ml.getBoolean("detect-xcheck", true),
                xcheckAction = ActionType.parse(ml.getString("xcheck-action")),
                logAllModLists = ml.getBoolean("log-all-mod-lists", true),
            )
        } else {
            ModListConfig()
        }

        // 反作弊集成
        antiCheatIntegration = yaml.getString("anti-cheat-integration", "auto") ?: "auto"

        // 封禁后端联动
        banBackend = yaml.getString("ban-backend", "auto") ?: "auto"

        // 兼容与联动
        bedrockExempt = yaml.getBoolean("bedrock-exempt", true)
        tweakerooMode = yaml.getBoolean("compatibility.tweakeroo-mode", false)
        crossServerSyncEnabled = yaml.getBoolean("cross-server-sync.enabled", false)

        // 多世界覆盖
        worldOverrides.clear()
        yaml.getConfigurationSection("worlds")?.getKeys(false)?.forEach { name ->
            val ws = yaml.getConfigurationSection("worlds.$name")
            worldOverrides[name.lowercase()] = WorldOverride(
                detectionEnabled = ws.optBool("detection-enabled"),
                antiPrinterEnabled = ws.optBool("anti-printer-enabled"),
                commandGuardEnabled = ws.optBool("command-guard-enabled"),
                graduatedEnabled = ws.optBool("graduated-enabled"),
            )
        }

        discordWebhookUrl = yaml.getString("webhook.discord", "") ?: ""
        onebotEnabled = yaml.getBoolean("webhook.onebot.enabled", false)
        onebotBaseUrl = yaml.getString("webhook.onebot.base-url", "http://127.0.0.1:3001") ?: "http://127.0.0.1:3001"
        onebotAccessToken = yaml.getString("webhook.onebot.access-token", "") ?: ""
        onebotGroupId = yaml.getLong("webhook.onebot.group-id", 0)

        updateCheckerEnabled = yaml.getBoolean("update-checker", true)
        updateRepo = yaml.getString("update-repo", "EpochcraftMC/AntiLitematica") ?: "EpochcraftMC/AntiLitematica"
    }

    /** 保存到 config.yml */
    fun save() {
        val yaml = plugin.config
        yaml.set("mode", mode.name.lowercase())

        val channelsSection = yaml.createSection("channels")
        channels.forEach { (name, cfg) ->
            channelsSection.set("$name.action", cfg.action.name)
            channelsSection.set("$name.ban-duration", DurationParser.format(cfg.banDuration))
        }
        yaml.set("banned-channels", null)

        yaml.set("kick-message", kickMessage.replace('§', '&'))
        yaml.set("log-detections", logDetections)
        yaml.set("notify-admins", notifyAdmins)
        yaml.set("recheck-delay-ticks", recheckDelayTicks)
        yaml.set("detect-forge-handshake", detectForgeHandshake)
        yaml.set("detect-fabric-api", detectFabricApi)
        yaml.set("brand-blocklist", brandBlocklist.sorted())
        yaml.set("detection-cooldown-ms", detectionCooldownMillis)
        yaml.set("auto-ban.enabled", autoBanEnabled)
        yaml.set("auto-ban.kicks-before-ban", kicksBeforeBan)
        yaml.set("auto-ban.duration", DurationParser.format(autoBanDuration))

        // 渐进惩罚
        val gp = yaml.createSection("graduated-punishment")
        gp.set("enabled", graduatedPunishment.enabled)
        gp.set("window-minutes", graduatedPunishment.windowMinutes)
        val lvls = gp.createSection("levels")
        graduatedPunishment.levels.forEachIndexed { index, l ->
            val s = lvls.createSection((index + 1).toString())
            s.set("action", l.action.name)
            s.set("reason", l.reason)
            s.set("duration", DurationParser.format(l.durationMillis))
            s.set("broadcast", l.broadcast)
            s.set("staff-alert", l.staffAlert)
        }
        val ex = gp.createSection("exceed-max")
        ex.set("action", graduatedPunishment.exceedMax.action.name)
        ex.set("reason", graduatedPunishment.exceedMax.reason)
        ex.set("duration", DurationParser.format(graduatedPunishment.exceedMax.durationMillis))

        // 防 Printer
        val ap = yaml.createSection("anti-printer")
        ap.set("enabled", antiPrinter.enabled)
        ap.set("max-blocks-per-second", antiPrinter.maxBlocksPerSecond)
        ap.set("apply-to-creative", antiPrinter.applyToCreative)
        ap.set("enforce-raytrace", antiPrinter.enforceRaytrace)
        ap.set("detect-consecutive-same-type", antiPrinter.detectConsecutiveSameType)
        ap.set("consecutive-same-type-threshold", antiPrinter.consecutiveSameTypeThreshold)
        ap.set("consecutive-window-ms", antiPrinter.consecutiveWindowMs)
        ap.set("detect-no-look-change", antiPrinter.detectNoLookChange)
        ap.set("reach-survival", antiPrinter.reachSurvival)
        ap.set("reach-creative", antiPrinter.reachCreative)
        ap.set("extra-reach-allowance", antiPrinter.extraReachAllowance)

        // 命令防护
        val cg = yaml.createSection("command-guard")
        cg.set("enabled", commandGuard.enabled)
        cg.set("allowed-commands", commandGuard.allowedCommands)
        cg.set("blocked-commands", commandGuard.blockedCommands)
        cg.set("max-per-window", commandGuard.maxPerWindow)
        cg.set("window-ms", commandGuard.windowMs)

        // ProtocolLib 信号检测
        val sg = yaml.createSection("signals")
        val ep = sg.createSection("easy-place")
        ep.set("enabled", signals.easyPlaceEnabled)
        ep.set("cancel", signals.easyPlaceCancel)
        ep.set("rel-min", signals.easyPlaceRelMin)
        ep.set("rel-max", signals.easyPlaceRelMax)
        ep.set("min-consecutive", signals.easyPlaceMinConsecutive)
        val nq = sg.createSection("nbt-query")
        nq.set("enabled", signals.nbtQueryEnabled)
        nq.set("allow-op", signals.nbtQueryAllowOp)
        nq.set("cancel", signals.nbtQueryCancel)
        nq.set("threshold", signals.nbtQueryThreshold)

        // FML / Fabric Mod List 深度解析
        val ml = yaml.createSection("mod-list")
        ml.set("enabled", modList.enabled)
        val modsSection = ml.createSection("banned-mod-ids")
        modList.bannedMods.forEach { (modId, cfg) ->
            val s = modsSection.createSection(modId)
            s.set("action", cfg.action.name)
            s.set("ban-duration", DurationParser.format(cfg.banDuration))
        }
        ml.set("detect-mod-changes", modList.detectModChanges)
        ml.set("change-action", modList.changeAction.name)
        ml.set("detect-xcheck", modList.detectXCheck)
        ml.set("xcheck-action", modList.xcheckAction.name)
        ml.set("log-all-mod-lists", modList.logAllModLists)

        // 反作弊集成
        yaml.set("anti-cheat-integration", antiCheatIntegration)
        // 封禁后端联动
        yaml.set("ban-backend", banBackend)
        // 兼容与联动
        yaml.set("bedrock-exempt", bedrockExempt)
        yaml.set("compatibility.tweakeroo-mode", tweakerooMode)
        yaml.set("cross-server-sync.enabled", crossServerSyncEnabled)

        // 多世界覆盖
        val worldsSection = yaml.createSection("worlds")
        worldOverrides.forEach { (name, o) ->
            val ws = worldsSection.createSection(name)
            o.detectionEnabled?.let { ws.set("detection-enabled", it) }
            o.antiPrinterEnabled?.let { ws.set("anti-printer-enabled", it) }
            o.commandGuardEnabled?.let { ws.set("command-guard-enabled", it) }
            o.graduatedEnabled?.let { ws.set("graduated-enabled", it) }
        }

        yaml.set("webhook.discord", discordWebhookUrl)
        yaml.set("webhook.onebot.enabled", onebotEnabled)
        yaml.set("webhook.onebot.base-url", onebotBaseUrl)
        yaml.set("webhook.onebot.access-token", onebotAccessToken)
        yaml.set("webhook.onebot.group-id", onebotGroupId)
        yaml.set("update-checker", updateCheckerEnabled)
        plugin.saveConfig()
    }

    // ---------------- 通道操作 ----------------

    /** 添加通道（默认 KICK） */
    fun addChannel(channel: String, action: ActionType = ActionType.KICK): Boolean {
        val normalized = channel.trim().lowercase()
        if (normalized.isEmpty()) return false
        channels[normalized] = ChannelActionConfig(action)
        save()
        return true
    }

    /** 修改通道动作 */
    fun setChannelAction(channel: String, action: ActionType): Boolean {
        val normalized = channel.trim().lowercase()
        val current = channels[normalized] ?: return false
        channels[normalized] = current.copy(action = action)
        save()
        return true
    }

    /** 移除通道 */
    fun removeChannel(channel: String): Boolean {
        val removed = channels.remove(channel.trim().lowercase()) != null
        if (removed) save()
        return removed
    }

    /** 查询通道动作（不存在返回默认 KICK） */
    fun getAction(channel: String): ActionType =
        channels[channel.lowercase()]?.action ?: ActionType.KICK

    // ---------------- 其他操作 ----------------

    /** 切换控制台日志开关 */
    fun setLogDetections(value: Boolean) {
        logDetections = value
        save()
    }

    /** 切换管理员通知开关 */
    fun setNotifyAdmins(value: Boolean) {
        notifyAdmins = value
        save()
    }

    /** 切换预设模式（strict / normal / lite） */
    fun setMode(newMode: Mode) {
        mode = newMode
        when (newMode) {
            Mode.STRICT -> {
                autoBanEnabled = true
                kicksBeforeBan = 2
                autoBanDuration = 30 * 86_400_000L
            }
            Mode.NORMAL -> {
                autoBanEnabled = false
            }
            Mode.LITE -> {
                autoBanEnabled = false
                detectForgeHandshake = false
                detectFabricApi = false
                brandBlocklist.clear()
            }
        }
        save()
    }

    /** 语言消息快捷访问 */
    fun lang(key: String): String = lang.get(key)

    /** 解析一级惩罚配置段 */
    private fun parsePunishmentLevel(
        section: org.bukkit.configuration.ConfigurationSection?,
        defaultReason: String,
    ): PunishmentLevel {
        if (section == null) {
            return PunishmentLevel(PunishmentAction.BAN, defaultReason, -1, true, true)
        }
        return PunishmentLevel(
            action = PunishmentAction.parse(section.getString("action")),
            reason = section.getString("reason") ?: defaultReason,
            durationMillis = DurationParser.parseMillis(section.getString("duration"), 86_400_000L),
            broadcast = section.getBoolean("broadcast", false),
            staffAlert = section.getBoolean("staff-alert", true),
        )
    }
}
