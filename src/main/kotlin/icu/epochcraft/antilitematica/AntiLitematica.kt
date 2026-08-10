package icu.epochcraft.antilitematica

import icu.epochcraft.antilitematica.ban.BanManager
import icu.epochcraft.antilitematica.api.AntiLitematicaAPI
import icu.epochcraft.antilitematica.command.AntiLitematicaCommand
import icu.epochcraft.antilitematica.config.LangManager
import icu.epochcraft.antilitematica.config.PluginConfig
import icu.epochcraft.antilitematica.database.DetectionDatabase
import icu.epochcraft.antilitematica.detection.DetectionBus
import icu.epochcraft.antilitematica.detection.ModDetectionListener
import icu.epochcraft.antilitematica.detection.ModDetectionService
import icu.epochcraft.antilitematica.guard.CommandGuard
import icu.epochcraft.antilitematica.guard.PlacementGuard
import icu.epochcraft.antilitematica.integration.IntegrationManager
import icu.epochcraft.antilitematica.menu.AdminMenu
import icu.epochcraft.antilitematica.menu.ChestAdminMenu
import icu.epochcraft.antilitematica.menu.MenuFactory
import icu.epochcraft.antilitematica.notify.NotificationService
import icu.epochcraft.antilitematica.papi.PlaceholderHook
import icu.epochcraft.antilitematica.punish.DetectionPunisher
import icu.epochcraft.antilitematica.punish.GraduatedPunisher
import icu.epochcraft.antilitematica.punish.ViolationTracker
import icu.epochcraft.antilitematica.signal.ProtocolLibSignalDetector
import icu.epochcraft.antilitematica.signal.SignalFactory
import icu.epochcraft.antilitematica.statistics.BStatsHook
import icu.epochcraft.antilitematica.statistics.StatsService
import icu.epochcraft.antilitematica.update.UpdateChecker
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * AntiLitematica
 *
 * 检测客户端是否注册了 Litematica（投影）/ Schematica 等 mod 的插件通道，
 * 一旦检测到立即处理（踢出/封禁/警告），不让进服。
 *
 * 功能总览：
 *   - 检测：通道指纹库 / 动作分级 / 二次验证 / Brand 拦截 / Forge·Fabric 环境识别 / 误报豁免
 *   - 惩罚：自动封禁（SQLite 持久化）/ 到期自动解封 / 登录拦截
 *   - 管理：双菜单（Dialog 1.21.7+ / 箱子回退）/ 统计面板 / 预设模式 / 多语言
 *   - 通知：Discord Webhook / QQ OneBot（纯出站，不开放端口）
 *   - 更新检查 / PlaceholderAPI 占位符
 *
 * @author 阿清
 */
class AntiLitematica : JavaPlugin() {

    lateinit var langManager: LangManager
        private set

    lateinit var configHolder: PluginConfig
        private set

    lateinit var database: DetectionDatabase
        private set

    lateinit var banManager: BanManager
        private set

    lateinit var detectionService: ModDetectionService
        private set

    /** 检测事件总线（检测源 -> 惩罚管线） */
    lateinit var detectionBus: DetectionBus
        private set

    /** 违规记录跟踪器（渐进惩罚计数） */
    lateinit var violationTracker: ViolationTracker
        private set

    /** 渐进惩罚（按次数升级） */
    lateinit var graduatedPunisher: GraduatedPunisher
        private set

    /** 基础动作兜底处理器（检测总线最后一环） */
    lateinit var detectionPunisher: DetectionPunisher
        private set

    /** 防 Printer（自动放置检测） */
    lateinit var placementGuard: PlacementGuard
        private set

    /** 命令防护（quick-paste 滥用拦截） */
    lateinit var commandGuard: CommandGuard
        private set

    /** 反作弊集成（Grim / Vulcan / Matrix 联动） */
    lateinit var integrationManager: IntegrationManager
        private set

    /** ProtocolLib 信号检测（服务端无 ProtocolLib 时为 null） */
    var signalDetector: ProtocolLibSignalDetector? = null
        private set

    var notificationService: NotificationService? = null
        private set

    lateinit var statsService: StatsService
        private set

    lateinit var updateChecker: UpdateChecker
        private set

    lateinit var adminMenu: AdminMenu
        private set

    override fun onEnable() {
        saveDefaultConfig()

        // 依赖装配
        langManager = LangManager(this, File(dataFolder, "lang"))
        langManager.load() // 先加载默认语言并复制内置语言文件
        configHolder = PluginConfig(this, langManager).also { it.reload() }
        database = DetectionDatabase(this).also { it.init() }
        banManager = BanManager(this, database).also { it.start() }
        detectionService = ModDetectionService(this)
        notificationService = NotificationService(this)
        statsService = StatsService(this)
        updateChecker = UpdateChecker(this)

        // 检测总线 + 惩罚管线（统一：渐进 → 基础动作兜底）
        detectionBus = DetectionBus(this)
        violationTracker = ViolationTracker(this)
        graduatedPunisher = GraduatedPunisher(this, violationTracker)
        detectionPunisher = DetectionPunisher(this)
        detectionBus.register(graduatedPunisher)
        detectionBus.register(detectionPunisher)

        // 检测源（防 Printer / 命令防护）
        placementGuard = PlacementGuard(this).also { it.reload() }
        commandGuard = CommandGuard(this).also { it.reload() }

        // 反作弊集成 + ProtocolLib 信号检测
        integrationManager = IntegrationManager(this).also { it.init() }
        signalDetector = SignalFactory.create(this)
        signalDetector?.start()

        // 菜单（按版本选择实现）
        adminMenu = MenuFactory.create(this)

        // 注册监听器
        server.pluginManager.registerEvents(ModDetectionListener(this, detectionService), this)
        server.pluginManager.registerEvents(banManager, this)
        server.pluginManager.registerEvents(placementGuard, this)
        server.pluginManager.registerEvents(commandGuard, this)
        val menu = adminMenu
        if (menu is ChestAdminMenu) {
            server.pluginManager.registerEvents(menu, this)
        }

        // 注册命令
        getCommand("antilitematica")?.let { command ->
            AntiLitematicaCommand(this).let {
                command.setExecutor(it)
                command.setTabCompleter(it)
            }
        }

        // 附加模块
        AntiLitematicaAPI.init(this)
        PlaceholderHook(this).register()
        updateChecker.checkAsync()

        // bStats 匿名统计（config.yml 配置插件 ID 后启用）
        BStatsHook(this).init()

        logger.info(
            "已启用 | 语言: ${langManager.currentLang} | 禁用通道: ${configHolder.channels.keys.joinToString(", ")} | 菜单: ${adminMenu.modeName}"
        )
    }

    override fun onDisable() {
        AntiLitematicaAPI.shutdown()
        signalDetector?.shutdown()
        banManager.stop()
        database.close()
        logger.info("已卸载")
    }
}
