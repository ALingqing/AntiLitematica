package top.chenray.antilitematica.web;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Multi-language support for the web dashboard.
 * Built-in translations for zh_CN, en_US, zh_TW.
 */
public final class DashboardLang {

    private static final Map<String, Map<String, String>> LANG_MAP = new HashMap<>();

    static {
        // ========== zh_CN (简体中文) ==========
        Map<String, String> zhCN = new HashMap<>();
        zhCN.put("login.title", "AntiLitematica 管理面板 - 登录");
        zhCN.put("login.header", "AntiLitematica 管理面板");
        zhCN.put("login.password", "密码");
        zhCN.put("login.btn", "登录");
        zhCN.put("login.error", "密码错误，请重试");
        zhCN.put("dashboard.title", "AntiLitematica 管理面板");
        zhCN.put("dashboard.header", "AntiLitematica 监控面板");
        zhCN.put("nav.overview", "概览");
        zhCN.put("nav.players", "玩家");
        zhCN.put("nav.violations", "违规记录");
        zhCN.put("nav.settings", "设置");
        zhCN.put("nav.logout", "退出");
        zhCN.put("overview.server", "服务器状态");
        zhCN.put("overview.version", "插件版本");
        zhCN.put("overview.online", "在线玩家");
        zhCN.put("overview.uptime", "运行时间");
        zhCN.put("overview.detections", "今日检测");
        zhCN.put("overview.punishments", "今日处罚");
        zhCN.put("overview.tps", "TPS");
        zhCN.put("overview.status", "状态");
        zhCN.put("overview.enabled", "已启用");
        zhCN.put("overview.disabled", "已禁用");
        zhCN.put("overview.detection", "检测系统");
        zhCN.put("overview.printer", "反打印机");
        zhCN.put("overview.webhook", "Webhook");
        zhCN.put("overview.graduated", "阶梯惩罚");
        zhCN.put("players.title", "在线玩家");
        zhCN.put("players.name", "玩家名");
        zhCN.put("players.uuid", "UUID");
        zhCN.put("players.ping", "延迟");
        zhCN.put("players.gamemode", "游戏模式");
        zhCN.put("players.world", "世界");
        zhCN.put("players.violations", "违规");
        zhCN.put("players.actions", "操作");
        zhCN.put("players.reset", "重置");
        zhCN.put("players.no_data", "暂无数据");
        zhCN.put("violations.title", "违规记录");
        zhCN.put("violations.player", "玩家");
        zhCN.put("violations.reason", "原因");
        zhCN.put("violations.channel", "频道");
        zhCN.put("violations.action", "动作");
        zhCN.put("violations.time", "时间");
        zhCN.put("violations.count", "次数");
        zhCN.put("violations.total", "总计");
        zhCN.put("violations.clear", "清空记录");
        zhCN.put("violations.no_data", "暂无违规记录");
        zhCN.put("settings.title", "快速设置");
        zhCN.put("settings.detection", "检测开关");
        zhCN.put("settings.printer", "反打印机");
        zhCN.put("settings.webhook", "Webhook");
        zhCN.put("settings.save", "保存设置");
        zhCN.put("settings.saved", "设置已保存");
        zhCN.put("lang.switch", "English");
        zhCN.put("lang.code", "en_US");
        zhCN.put("footer.text", "AntiLitematica v{version} — Built-in Web Dashboard");
        zhCN.put("loading", "加载中...");
        zhCN.put("error.load", "加载数据失败");
        LANG_MAP.put("zh_CN", zhCN);

        // ========== en_US (English) ==========
        Map<String, String> enUS = new HashMap<>();
        enUS.put("login.title", "AntiLitematica Dashboard - Login");
        enUS.put("login.header", "AntiLitematica Dashboard");
        enUS.put("login.password", "Password");
        enUS.put("login.btn", "Login");
        enUS.put("login.error", "Invalid password, please try again");
        enUS.put("dashboard.title", "AntiLitematica Dashboard");
        enUS.put("dashboard.header", "AntiLitematica Monitor");
        enUS.put("nav.overview", "Overview");
        enUS.put("nav.players", "Players");
        enUS.put("nav.violations", "Violations");
        enUS.put("nav.settings", "Settings");
        enUS.put("nav.logout", "Logout");
        enUS.put("overview.server", "Server Status");
        enUS.put("overview.version", "Plugin Version");
        enUS.put("overview.online", "Online Players");
        enUS.put("overview.uptime", "Uptime");
        enUS.put("overview.detections", "Today Detections");
        enUS.put("overview.punishments", "Today Punishments");
        enUS.put("overview.tps", "TPS");
        enUS.put("overview.status", "Status");
        enUS.put("overview.enabled", "Enabled");
        enUS.put("overview.disabled", "Disabled");
        enUS.put("overview.detection", "Detection");
        enUS.put("overview.printer", "Anti-Printer");
        enUS.put("overview.webhook", "Webhook");
        enUS.put("overview.graduated", "Graduated Punish");
        enUS.put("players.title", "Online Players");
        enUS.put("players.name", "Name");
        enUS.put("players.uuid", "UUID");
        enUS.put("players.ping", "Ping");
        enUS.put("players.gamemode", "Gamemode");
        enUS.put("players.world", "World");
        enUS.put("players.violations", "Violations");
        enUS.put("players.actions", "Actions");
        enUS.put("players.reset", "Reset");
        enUS.put("players.no_data", "No data");
        enUS.put("violations.title", "Violation Records");
        enUS.put("violations.player", "Player");
        enUS.put("violations.reason", "Reason");
        enUS.put("violations.channel", "Channel");
        enUS.put("violations.action", "Action");
        enUS.put("violations.time", "Time");
        enUS.put("violations.count", "Count");
        enUS.put("violations.total", "Total");
        enUS.put("violations.clear", "Clear Records");
        enUS.put("violations.no_data", "No violation records");
        enUS.put("settings.title", "Quick Settings");
        enUS.put("settings.detection", "Detection");
        enUS.put("settings.printer", "Anti-Printer");
        enUS.put("settings.webhook", "Webhook");
        enUS.put("settings.save", "Save Settings");
        enUS.put("settings.saved", "Settings saved");
        enUS.put("lang.switch", "简体中文");
        enUS.put("lang.code", "zh_CN");
        enUS.put("footer.text", "AntiLitematica v{version} — Built-in Web Dashboard");
        enUS.put("loading", "Loading...");
        enUS.put("error.load", "Failed to load data");
        LANG_MAP.put("en_US", enUS);

        // ========== zh_TW (繁体中文) ==========
        Map<String, String> zhTW = new HashMap<>();
        zhTW.put("login.title", "AntiLitematica 管理面板 - 登入");
        zhTW.put("login.header", "AntiLitematica 管理面板");
        zhTW.put("login.password", "密碼");
        zhTW.put("login.btn", "登入");
        zhTW.put("login.error", "密碼錯誤，請重試");
        zhTW.put("dashboard.title", "AntiLitematica 管理面板");
        zhTW.put("dashboard.header", "AntiLitematica 監控面板");
        zhTW.put("nav.overview", "概覽");
        zhTW.put("nav.players", "玩家");
        zhTW.put("nav.violations", "違規記錄");
        zhTW.put("nav.settings", "設定");
        zhTW.put("nav.logout", "登出");
        zhTW.put("overview.server", "伺服器狀態");
        zhTW.put("overview.version", "插件版本");
        zhTW.put("overview.online", "線上玩家");
        zhTW.put("overview.uptime", "運行時間");
        zhTW.put("overview.detections", "今日檢測");
        zhTW.put("overview.punishments", "今日處罰");
        zhTW.put("overview.tps", "TPS");
        zhTW.put("overview.status", "狀態");
        zhTW.put("overview.enabled", "已啟用");
        zhTW.put("overview.disabled", "已停用");
        zhTW.put("overview.detection", "檢測系統");
        zhTW.put("overview.printer", "反打印機");
        zhTW.put("overview.webhook", "Webhook");
        zhTW.put("overview.graduated", "階梯懲罰");
        zhTW.put("players.title", "線上玩家");
        zhTW.put("players.name", "玩家名");
        zhTW.put("players.uuid", "UUID");
        zhTW.put("players.ping", "延遲");
        zhTW.put("players.gamemode", "遊戲模式");
        zhTW.put("players.world", "世界");
        zhTW.put("players.violations", "違規");
        zhTW.put("players.actions", "操作");
        zhTW.put("players.reset", "重置");
        zhTW.put("players.no_data", "暫無數據");
        zhTW.put("violations.title", "違規記錄");
        zhTW.put("violations.player", "玩家");
        zhTW.put("violations.reason", "原因");
        zhTW.put("violations.channel", "頻道");
        zhTW.put("violations.action", "動作");
        zhTW.put("violations.time", "時間");
        zhTW.put("violations.count", "次數");
        zhTW.put("violations.total", "總計");
        zhTW.put("violations.clear", "清空記錄");
        zhTW.put("violations.no_data", "暫無違規記錄");
        zhTW.put("settings.title", "快速設定");
        zhTW.put("settings.detection", "檢測開關");
        zhTW.put("settings.printer", "反打印機");
        zhTW.put("settings.webhook", "Webhook");
        zhTW.put("settings.save", "儲存設定");
        zhTW.put("settings.saved", "設定已儲存");
        zhTW.put("lang.switch", "English");
        zhTW.put("lang.code", "en_US");
        zhTW.put("footer.text", "AntiLitematica v{version} — Built-in Web Dashboard");
        zhTW.put("loading", "載入中...");
        zhTW.put("error.load", "載入數據失敗");
        LANG_MAP.put("zh_TW", zhTW);
    }

    public static String get(String locale, String key) {
        Map<String, String> lang = LANG_MAP.get(locale);
        if (lang == null) lang = LANG_MAP.get("zh_CN");
        if (lang == null) return key;
        return lang.getOrDefault(key, key);
    }

    public static String getOrDefault(String locale, String key, String fallback) {
        Map<String, String> lang = LANG_MAP.get(locale);
        if (lang == null) lang = LANG_MAP.get("zh_CN");
        if (lang == null) return fallback;
        return lang.getOrDefault(key, fallback);
    }
}
