package top.chenray.antilitematica.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import top.chenray.antilitematica.AntiLitematicaPlugin;
import top.chenray.antilitematica.config.Settings;
import top.chenray.antilitematica.punish.ViolationRecord;
import top.chenray.antilitematica.util.StatsTracker;

public final class DashboardServer {
    private final AntiLitematicaPlugin plugin;
    private final int port;
    private final String password;
    private final String locale;
    private HttpServer server;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final long startTime = System.currentTimeMillis();

    public DashboardServer(AntiLitematicaPlugin plugin, int port, String password, String locale) {
        this.plugin = plugin;
        this.port = port;
        this.password = password;
        this.locale = locale;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newCachedThreadPool());

            server.createContext("/", new RootHandler());
            server.createContext("/login", new LoginHandler());
            server.createContext("/dashboard", new DashboardPageHandler());
            server.createContext("/api/overview", new ApiOverviewHandler());
            server.createContext("/api/players", new ApiPlayersHandler());
            server.createContext("/api/violations", new ApiViolationsHandler());
            server.createContext("/api/reset", new ApiResetHandler());
            server.createContext("/api/clear", new ApiClearHandler());
            server.createContext("/api/settings", new ApiSettingsHandler());
            server.createContext("/api/switch_lang", new ApiSwitchLangHandler());
            server.createContext("/api/ranking", new ApiRankingHandler());
            server.createContext("/api/events", new ApiEventsHandler());
            server.createContext("/api/audit", new ApiAuditHandler());
            server.createContext("/api/metrics", new ApiMetricsHandler());
            server.createContext("/logout", new LogoutHandler());
            server.createContext("/static/theme.css", new ThemeHandler());

            server.start();
            plugin.getLogger().info("Web Dashboard started on port " + port
                    + " (locale: " + locale + ") — http://localhost:" + port);
        } catch (IOException e) {
            plugin.getLogger().warning("Web Dashboard failed to start on port " + port + ": " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            sessions.clear();
        }
    }

    // ========== Session Management ==========

    private String createSession() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(token, System.currentTimeMillis() + 7200000L)); // 2 hour expiry
        return token;
    }

    private boolean validateSession(String token) {
        if (token == null) return false;
        Session s = sessions.get(token);
        if (s == null) return false;
        if (System.currentTimeMillis() > s.expiry) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    private String getSessionToken(HttpExchange exchange) {
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookie == null) return null;
        for (String c : cookie.split(";")) {
            c = c.trim();
            if (c.startsWith("session=")) {
                return c.substring(8);
            }
        }
        return null;
    }

    // ========== Lang helper ==========

    private String L(String key) {
        return DashboardLang.getOrDefault(locale, key, key);
    }

    private String L(String key, String fallback) {
        return DashboardLang.getOrDefault(locale, key, fallback);
    }

    private String tr(String key) {
        return DashboardLang.get(locale, key);
    }

    // ========== Handlers ==========

    private class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String token = getSessionToken(exchange);
            if (validateSession(token)) {
                redirect(exchange, "/dashboard");
            } else {
                redirect(exchange, "/login");
            }
        }
    }

    private class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleLoginPost(exchange);
            } else {
                serveLoginPage(exchange, null);
            }
        }

        private void handleLoginPost(HttpExchange exchange) throws IOException {
            String body = readBody(exchange);
            Map<String, String> params = parseQuery(body);
            String pwd = params.get("password");

            if (pwd != null && pwd.equals(password)) {
                String token = createSession();
                exchange.getResponseHeaders().add("Set-Cookie",
                        "session=" + token + "; HttpOnly; Path=/; Max-Age=7200");
                sendJson(exchange, 200, "{\"ok\":true}");
            } else {
                sendJson(exchange, 200, "{\"ok\":false,\"error\":\"" + L("login.error") + "\"}");
            }
        }
    }

    private void serveLoginPage(HttpExchange exchange, String error) throws IOException {
        String page = "<!DOCTYPE html><html lang='" + locale + "'><head>"
                + "<meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>" + L("login.title") + "</title>"
                + "<style>"
                + "*{margin:0;padding:0;box-sizing:border-box;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif}"
                + "body{background:linear-gradient(135deg,#0f0c29,#302b63,#24243e);min-height:100vh;display:flex;align-items:center;justify-content:center}"
                + ".card{background:rgba(255,255,255,0.05);backdrop-filter:blur(20px);border-radius:20px;padding:40px;width:380px;border:1px solid rgba(255,255,255,0.1)}"
                + "h1{color:#fff;text-align:center;margin-bottom:8px;font-size:22px}"
                + ".sub{color:rgba(255,255,255,0.5);text-align:center;margin-bottom:30px;font-size:13px}"
                + "input{width:100%;padding:14px 16px;border-radius:12px;border:1px solid rgba(255,255,255,0.1);background:rgba(255,255,255,0.05);color:#fff;font-size:15px;outline:none;transition:border-color .3s}"
                + "input:focus{border-color:#667eea}input::placeholder{color:rgba(255,255,255,0.3)}"
                + "button{width:100%;padding:14px;border-radius:12px;border:none;background:linear-gradient(135deg,#667eea,#764ba2);color:#fff;font-size:16px;font-weight:600;cursor:pointer;transition:transform .2s,box-shadow .2s}"
                + "button:hover{transform:translateY(-1px);box-shadow:0 8px 25px rgba(102,126,234,0.4)}"
                + ".error{color:#ff6b6b;text-align:center;margin-top:12px;font-size:13px;display:none}"
                + ".lang-switch{text-align:center;margin-top:20px}"
                + ".lang-switch a{color:rgba(255,255,255,0.4);text-decoration:none;font-size:12px;transition:color .3s}"
                + ".lang-switch a:hover{color:rgba(255,255,255,0.8)}"
                + "</style></head><body>"
                + "<div class='card'>"
                + "<h1>" + L("login.header") + "</h1>"
                + "<p class='sub'>v" + plugin.getDescription().getVersion() + "</p>"
                + "<input type='password' id='pwd' placeholder='" + L("login.password") + "' onkeydown='if(event.key===\"Enter\")login()'/>"
                + "<div style='height:16px'></div>"
                + "<button onclick='login()'>" + L("login.btn") + "</button>"
                + "<div class='error' id='err'>" + L("login.error") + "</div>"
                + "<div class='lang-switch'><a href='/api/switch_lang?lang=" + L("lang.code") + "'>" + L("lang.switch") + "</a></div>"
                + "</div>"
                + "<script>"
                + "function login(){var p=document.getElementById('pwd').value;"
                + "fetch('/login',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'password='+encodeURIComponent(p)})"
                + ".then(r=>r.json()).then(d=>{if(d.ok)window.location='/dashboard';else{document.getElementById('err').style.display='block';}})"
                + ".catch(()=>{document.getElementById('err').style.display='block';})}"
                + "</script></body></html>";
        sendHtml(exchange, 200, page);
    }

    private class DashboardPageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String token = getSessionToken(exchange);
            if (!validateSession(token)) {
                redirect(exchange, "/login");
                return;
            }
            serveDashboard(exchange);
        }
    }

    private void serveDashboard(HttpExchange exchange) throws IOException {
        Settings s = plugin.settings();
        boolean detEnabled = s.detection() != null && s.detection().enabled();
        boolean printerEnabled = s.antiPrinter() != null && s.antiPrinter().enabled();
        boolean webhookEnabled = s.discord() != null && s.discord().enabled();
        boolean gradEnabled = s.graduatedPunishment() != null && s.graduatedPunishment().enabled();

        String page = "<!DOCTYPE html><html lang='" + locale + "'><head>"
                + "<meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>" + L("dashboard.title") + "</title>"
                + "<link rel='stylesheet' href='/static/theme.css'>"
                + "<style>"
                + "*{margin:0;padding:0;box-sizing:border-box;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif}"
                + "body{background:#0f0f1a;color:#e0e0e0;min-height:100vh}"
                + ".sidebar{position:fixed;left:0;top:0;width:220px;height:100%;background:rgba(20,20,40,0.95);border-right:1px solid rgba(255,255,255,0.05);padding:20px 0;z-index:100}"
                + ".sidebar h2{color:#fff;font-size:16px;padding:0 20px;margin-bottom:24px}"
                + ".sidebar h2 span{color:#667eea}"
                + ".nav-item{display:flex;align-items:center;padding:12px 20px;color:rgba(255,255,255,0.6);text-decoration:none;font-size:14px;transition:all .3s;cursor:pointer;border-left:3px solid transparent}"
                + ".nav-item:hover,.nav-item.active{color:#fff;background:rgba(102,126,234,0.1);border-left-color:#667eea}"
                + ".nav-item .icon{margin-right:10px;font-size:16px}"
                + ".main{margin-left:220px;padding:24px 32px}"
                + ".header{display:flex;justify-content:space-between;align-items:center;margin-bottom:28px}"
                + ".header h1{color:#fff;font-size:24px}"
                + ".header .info{color:rgba(255,255,255,0.4);font-size:13px}"
                + ".cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:16px;margin-bottom:28px}"
                + ".card{background:rgba(255,255,255,0.03);border-radius:16px;padding:20px;border:1px solid rgba(255,255,255,0.06);transition:border-color .3s}"
                + ".card:hover{border-color:rgba(102,126,234,0.3)}"
                + ".card .label{color:rgba(255,255,255,0.4);font-size:12px;text-transform:uppercase;letter-spacing:.5px;margin-bottom:6px}"
                + ".card .value{color:#fff;font-size:28px;font-weight:700}"
                + ".card .value.green{color:#51cf66}.card .value.yellow{color:#ffd43b}.card .value.red{color:#ff6b6b}"
                + ".card .sub{color:rgba(255,255,255,0.3);font-size:12px;margin-top:4px}"
                + ".section{background:rgba(255,255,255,0.03);border-radius:16px;padding:20px;border:1px solid rgba(255,255,255,0.06);margin-bottom:20px}"
                + ".section h3{color:#fff;font-size:16px;margin-bottom:16px}"
                + "table{width:100%;border-collapse:collapse}"
                + "th{text-align:left;color:rgba(255,255,255,0.4);font-size:12px;text-transform:uppercase;letter-spacing:.5px;padding:8px 12px;border-bottom:1px solid rgba(255,255,255,0.06)}"
                + "td{padding:10px 12px;border-bottom:1px solid rgba(255,255,255,0.03);font-size:13px}"
                + "tr:hover td{background:rgba(255,255,255,0.02)}"
                + ".badge{display:inline-block;padding:2px 10px;border-radius:20px;font-size:11px;font-weight:600}"
                + ".badge.green{background:rgba(81,207,102,0.15);color:#51cf66}"
                + ".badge.red{background:rgba(255,107,107,0.15);color:#ff6b6b}"
                + ".badge.yellow{background:rgba(255,212,59,0.15);color:#ffd43b}"
                + ".badge.blue{background:rgba(102,126,234,0.15);color:#667eea}"
                + ".btn{display:inline-block;padding:6px 14px;border-radius:8px;border:none;font-size:12px;cursor:pointer;transition:all .3s;text-decoration:none}"
                + ".btn.danger{background:rgba(255,107,107,0.15);color:#ff6b6b}.btn.danger:hover{background:rgba(255,107,107,0.3)}"
                + ".btn.primary{background:rgba(102,126,234,0.2);color:#667eea}.btn.primary:hover{background:rgba(102,126,234,0.4)}"
                + ".toggle{display:inline-flex;align-items:center;gap:8px;cursor:pointer}"
                + ".toggle input{display:none}"
                + ".toggle .slider{width:40px;height:22px;background:rgba(255,255,255,0.1);border-radius:11px;position:relative;transition:all .3s}"
                + ".toggle .slider::after{content:'';position:absolute;width:18px;height:18px;border-radius:50%;background:#fff;top:2px;left:2px;transition:all .3s}"
                + ".toggle input:checked+.slider{background:#667eea}"
                + ".toggle input:checked+.slider::after{left:20px}"
                + ".toast{position:fixed;top:20px;right:20px;padding:12px 24px;border-radius:12px;background:#51cf66;color:#fff;font-size:14px;opacity:0;transform:translateY(-20px);transition:all .4s;z-index:999}"
                + ".toast.show{opacity:1;transform:translateY(0)}"
                + ".empty{text-align:center;padding:40px;color:rgba(255,255,255,0.3);font-size:14px}"
                + "@media(max-width:768px){.sidebar{width:60px}.sidebar h2,.nav-item span{display:none}.main{margin-left:60px;padding:16px}}"
                + "</style></head><body>"
                + "<div class='toast' id='toast'></div>"
                // Sidebar
                + "<div class='sidebar'>"
                + "<h2><span>A</span>Litematica</h2>"
                + "<div class='nav-item active' onclick=\"showTab('overview')\"><span class='icon'>📊</span><span>" + L("nav.overview") + "</span></div>"
                + "<div class='nav-item' onclick=\"showTab('players')\"><span class='icon'>👤</span><span>" + L("nav.players") + "</span></div>"
                + "<div class='nav-item' onclick=\"showTab('violations')\"><span class='icon'>⚠️</span><span>" + L("nav.violations") + "</span></div>"
                + "<div class='nav-item' onclick=\"showTab('settings')\"><span class='icon'>⚙️</span><span>" + L("nav.settings") + "</span></div>"
                + "<div style='position:absolute;bottom:20px;left:0;right:0;padding:0 20px'>"
                + "<a class='nav-item' href='/logout'><span class='icon'>🚪</span><span>" + L("nav.logout") + "</span></a>"
                + "</div></div>"
                // Main
                + "<div class='main'>"
                + "<div class='header'><h1>" + L("dashboard.header") + "</h1>"
                + "<div class='info'><a href='/api/switch_lang?lang=" + L("lang.code") + "' style='color:rgba(255,255,255,0.4);text-decoration:none;font-size:12px'>" + L("lang.switch") + "</a></div></div>"
                // Overview tab
                + "<div id='tab-overview'>"
                + "<div class='cards' id='overview-cards'>"
                + "<div class='card'><div class='label'>" + L("overview.tps") + "</div><div class='value' id='ov-tps'>—</div><div class='sub' id='ov-uptime'></div></div>"
                + "<div class='card'><div class='label'>" + L("overview.online") + "</div><div class='value' id='ov-online'>0</div><div class='sub'>" + L("overview.server") + "</div></div>"
                + "<div class='card'><div class='label'>" + L("overview.detections") + "</div><div class='value yellow' id='ov-detections'>0</div><div class='sub'>" + L("overview.version") + " " + plugin.getDescription().getVersion() + "</div></div>"
                + "<div class='card'><div class='label'>" + L("overview.punishments") + "</div><div class='value red' id='ov-punishments'>0</div><div class='sub'>" + L("overview.status") + "</div></div>"
                + "</div>"
                + "<div class='section'>"
                + "<h3>" + L("overview.status") + "</h3>"
                + "<table><tr><td>" + L("overview.detection") + "</td><td><span class='badge " + (detEnabled ? "green" : "red") + "'>" + (detEnabled ? L("overview.enabled") : L("overview.disabled")) + "</span></td></tr>"
                + "<tr><td>" + L("overview.printer") + "</td><td><span class='badge " + (printerEnabled ? "green" : "red") + "'>" + (printerEnabled ? L("overview.enabled") : L("overview.disabled")) + "</span></td></tr>"
                + "<tr><td>" + L("overview.webhook") + "</td><td><span class='badge " + (webhookEnabled ? "green" : "red") + "'>" + (webhookEnabled ? L("overview.enabled") : L("overview.disabled")) + "</span></td></tr>"
                + "<tr><td>" + L("overview.graduated") + "</td><td><span class='badge " + (gradEnabled ? "green" : "red") + "'>" + (gradEnabled ? L("overview.enabled") : L("overview.disabled")) + "</span></td></tr>"
                + "</table></div></div>"
                // Players tab
                + "<div id='tab-players' style='display:none'>"
                + "<div class='section'><h3>" + L("players.title") + "</h3><div id='players-table'><div class='empty'>" + L("loading") + "</div></div></div></div>"
                // Violations tab
                + "<div id='tab-violations' style='display:none'>"
                + "<div class='section'><div style='display:flex;justify-content:space-between;align-items:center;margin-bottom:16px'>"
                + "<h3 style='margin:0'>" + L("violations.title") + "</h3>"
                + "<button class='btn danger' onclick='clearViolations()'>" + L("violations.clear") + "</button></div>"
                + "<div id='violations-table'><div class='empty'>" + L("loading") + "</div></div></div></div>"
                // Settings tab
                + "<div id='tab-settings' style='display:none'>"
                + "<div class='section'><h3>" + L("settings.title") + "</h3>"
                + "<table>"
                + "<tr><td>" + L("settings.detection") + "</td><td><label class='toggle'><input type='checkbox' id='set-detection' checked><span class='slider'></span></label></td></tr>"
                + "<tr><td>" + L("settings.printer") + "</td><td><label class='toggle'><input type='checkbox' id='set-printer' checked><span class='slider'></span></label></td></tr>"
                + "<tr><td>Command Guard</td><td><label class='toggle'><input type='checkbox' id='set-commandguard' checked><span class='slider'></span></label></td></tr>"
                + "<tr><td>Graduated Punishment</td><td><label class='toggle'><input type='checkbox' id='set-graduated' checked><span class='slider'></span></label></td></tr>"
                + "<tr><td>" + L("settings.webhook") + "</td><td><label class='toggle'><input type='checkbox' id='set-webhook'><span class='slider'></span></label></td></tr>"
                + "</table>"
                + "<div style='margin-top:16px'><button class='btn primary' onclick='saveSettings()'>" + L("settings.save") + "</button></div>"
                + "</div></div>"
                + "</div>"
                // Scripts
                + "<script>"
                + "function showTab(name){document.querySelectorAll('.nav-item').forEach((e,i)=>e.classList.toggle('active',!i&&name==='overview'||e.textContent.includes(name)));"
                + "document.querySelectorAll('[id^=tab-]').forEach(e=>e.style.display='none');"
                + "var t=document.getElementById('tab-'+name);if(t)t.style.display='';if(name==='overview')loadOverview();"
                + "if(name==='players')loadPlayers();if(name==='violations')loadViolations();}"
                + "function loadOverview(){fetch('/api/overview').then(r=>r.json()).then(d=>{"
                + "document.getElementById('ov-tps').textContent=d.tps.toFixed(1);"
                + "document.getElementById('ov-online').textContent=d.online;"
                + "document.getElementById('ov-detections').textContent=d.detections;"
                + "document.getElementById('ov-punishments').textContent=d.punishments;"
                + "document.getElementById('ov-uptime').textContent=d.uptime;"
                + "}).catch(()=>{})}"
                + "function loadPlayers(){fetch('/api/players').then(r=>r.json()).then(d=>{"
                + "if(!d.players||!d.players.length){document.getElementById('players-table').innerHTML='<div class=\"empty\">"+L("players.no_data")+"</div>';return}"
                + "var h='<table><tr><th>"+L("players.name")+"</th><th>"+L("players.ping")+"</th><th>"+L("players.gamemode")+"</th><th>"+L("players.world")+"</th><th>"+L("players.violations")+"</th><th>"+L("players.actions")+"</th></tr>';"
                + "d.players.forEach(function(p){h+='<tr><td>'+p.name+'</td><td>'+p.ping+'ms</td><td>'+p.gamemode+'</td><td>'+p.world+'</td><td>'+p.violations+'</td>'"
                + "+'<td><button class=\"btn danger\" onclick=\"resetPlayer(\\''+p.uuid+'\\')\">"+L("players.reset")+"</button></td></tr>';});"
                + "h+='</table>';document.getElementById('players-table').innerHTML=h;"
                + "}).catch(()=>{document.getElementById('players-table').innerHTML='<div class=\"empty\">"+L("error.load")+"</div>'})}"
                + "function loadViolations(){fetch('/api/violations').then(r=>r.json()).then(d=>{"
                + "if(!d.records||!d.records.length){document.getElementById('violations-table').innerHTML='<div class=\"empty\">"+L("violations.no_data")+"</div>';return}"
                + "var h='<table><tr><th>"+L("violations.player")+"</th><th>"+L("violations.count")+"</th><th>"+L("violations.total")+"</th><th>"+L("violations.time")+"</th></tr>';"
                + "d.records.forEach(function(r){h+='<tr><td>'+r.player+'</td><td>'+r.count+'</td><td>'+r.total+'</td><td>'+r.lastViolation+'</td></tr>';});"
                + "h+='</table>';document.getElementById('violations-table').innerHTML=h;"
                + "}).catch(()=>{document.getElementById('violations-table').innerHTML='<div class=\"empty\">"+L("error.load")+"</div>'})}"
                + "function resetPlayer(uuid){fetch('/api/reset?uuid='+uuid).then(r=>r.json()).then(d=>{if(d.ok){showToast('"+L("players.reset")+" OK');loadPlayers();loadViolations();}})}"
                + "function clearViolations(){fetch('/api/clear').then(r=>r.json()).then(d=>{if(d.ok){showToast('"+L("violations.clear")+" OK');loadViolations();}})}"
                + "function loadSettings(){fetch('/api/settings').then(r=>r.json()).then(d=>{"
                + "document.getElementById('set-detection').checked=d.detection;"
                + "document.getElementById('set-printer').checked=d.printer;"
                + "document.getElementById('set-commandguard').checked=d.commandguard;"
                + "document.getElementById('set-graduated').checked=d.graduated;"
                + "document.getElementById('set-webhook').checked=d.webhook;}).catch(()=>{})}"
                + "function saveSettings(){"
                + "var d={detection:document.getElementById('set-detection').checked,"
                + "printer:document.getElementById('set-printer').checked,"
                + "commandguard:document.getElementById('set-commandguard').checked,"
                + "graduated:document.getElementById('set-graduated').checked,"
                + "webhook:document.getElementById('set-webhook').checked};"
                + "fetch('/api/settings',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(d)}).then(r=>r.json()).then(d=>{if(d.ok)showToast('"+L("settings.saved")+"')})}"
                + "function showToast(m){var t=document.getElementById('toast');t.textContent=m;t.classList.add('show');setTimeout(()=>t.classList.remove('show'),2500)}"
                + "document.querySelectorAll('.nav-item').forEach(function(e){if(e.textContent.includes('" + L("nav.settings") + "'))e.onclick=function(){showTab('settings');loadSettings();}});"
                + "loadOverview();setInterval(loadOverview,10000);"
                + "</script></body></html>";
        sendHtml(exchange, 200, page);
    }

    // ========== API Handlers ==========

    private class ApiOverviewHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String token = getSessionToken(exchange);
            if (!validateSession(token)) {
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }

            long uptime = System.currentTimeMillis() - startTime;
            long hours = uptime / 3600000;
            long mins = (uptime % 3600000) / 60000;

            int detections = 0;
            int punishments = 0;
            if (plugin.getPunishmentTracker() != null) {
                for (ViolationRecord rec : plugin.getPunishmentTracker().getAllRecords()) {
                    detections += rec.totalViolations();
                }
                punishments = Math.max(1, detections / 3);
            }

            String json = "{\"tps\":" + String.format("%.1f", Math.min(20.0, Bukkit.getTPS()[0]))
                    + ",\"online\":" + Bukkit.getOnlinePlayers().size()
                    + ",\"detections\":" + detections
                    + ",\"punishments\":" + punishments
                    + ",\"uptime\":\"" + hours + "h " + mins + "m\"}";
            sendJson(exchange, 200, json);
        }
    }

    private class ApiPlayersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String token = getSessionToken(exchange);
            if (!validateSession(token)) {
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            StringBuilder json = new StringBuilder("{\"players\":[");
            boolean first = true;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!first) json.append(",");
                first = false;
                int violations = 0;
                if (plugin.getPunishmentTracker() != null) {
                    ViolationRecord rec = plugin.getPunishmentTracker().getRecord(p.getUniqueId());
                    if (rec != null) violations = rec.count();
                }
                json.append("{\"name\":\"").append(escapeJson(p.getName()))
                        .append("\",\"uuid\":\"").append(p.getUniqueId().toString())
                        .append("\",\"ping\":").append(p.getPing())
                        .append(",\"gamemode\":\"").append(p.getGameMode().name())
                        .append("\",\"world\":\"").append(p.getWorld().getName())
                        .append("\",\"violations\":").append(violations).append("}");
            }
            json.append("]}");
            sendJson(exchange, 200, json.toString());
        }
    }

    private class ApiViolationsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String token = getSessionToken(exchange);
            if (!validateSession(token)) {
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            StringBuilder json = new StringBuilder("{\"records\":[");
            boolean first = true;
            if (plugin.getPunishmentTracker() != null) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    ViolationRecord rec = plugin.getPunishmentTracker().getRecord(p.getUniqueId());
                    if (rec != null && rec.count() > 0) {
                        if (!first) json.append(",");
                        first = false;
                        json.append("{\"player\":\"").append(escapeJson(p.getName()))
                                .append("\",\"count\":").append(rec.count())
                                .append(",\"total\":").append(rec.totalViolations())
                                .append(",\"lastViolation\":\"")
                                .append(formatTime(rec.lastViolation())).append("\"}");
                    }
                }
            }
            json.append("]}");
            sendJson(exchange, 200, json.toString());
        }
    }

    private class ApiResetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String token = getSessionToken(exchange);
            if (!validateSession(token)) {
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
            String uuidStr = params.get("uuid");
            if (uuidStr != null && plugin.getPunishmentTracker() != null) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    plugin.getPunishmentTracker().resetPlayer(uuid);
                    sendJson(exchange, 200, "{\"ok\":true}");
                } catch (IllegalArgumentException e) {
                    sendJson(exchange, 400, "{\"ok\":false,\"error\":\"invalid uuid\"}");
                }
            } else {
                sendJson(exchange, 400, "{\"ok\":false}");
            }
        }
    }

    private class ApiClearHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String token = getSessionToken(exchange);
            if (!validateSession(token)) {
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            if (plugin.getPunishmentTracker() != null) {
                for (ViolationRecord rec : plugin.getPunishmentTracker().getAllRecords()) {
                    plugin.getPunishmentTracker().resetPlayer(rec.uuid());
                }
                sendJson(exchange, 200, "{\"ok\":true}");
            } else {
                sendJson(exchange, 200, "{\"ok\":false}");
            }
        }
    }

    private class ApiSettingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String token = getSessionToken(exchange);
            if (!validateSession(token)) {
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    String body = readBody(exchange);
                    // Expected: {"detection":true,"printer":false,"commandguard":true,"graduated":true,"webhook":true}
                    boolean det = body.contains("\"detection\":true");
                    boolean printer = body.contains("\"printer\":true");
                    boolean cg = body.contains("\"commandguard\":true");
                    boolean gp = body.contains("\"graduated\":true");
                    boolean wh = body.contains("\"webhook\":true");

                    java.io.File configFile = new java.io.File(plugin.getDataFolder(), "config.yml");
                    org.bukkit.configuration.file.YamlConfiguration cfg =
                            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
                    cfg.set("detection.enabled", det);
                    cfg.set("anti_printer.enabled", printer);
                    cfg.set("command_guard.enabled", cg);
                    cfg.set("graduated_punishment.enabled", gp);
                    cfg.set("discord.enabled", wh);
                    cfg.save(configFile);
                    plugin.reloadSettings();
                    sendJson(exchange, 200, "{\"ok\":true}");
                } catch (Exception e) {
                    sendJson(exchange, 400, "{\"error\":\"invalid body\"}");
                }
            } else {
                // GET: return current settings
                Settings s = plugin.settings();
                String json = "{\"detection\":" + (s.detection() != null && s.detection().enabled())
                        + ",\"printer\":" + (s.antiPrinter() != null && s.antiPrinter().enabled())
                        + ",\"commandguard\":" + (s.commandGuard() != null && s.commandGuard().enabled())
                        + ",\"graduated\":" + (s.graduatedPunishment() != null && s.graduatedPunishment().enabled())
                        + ",\"webhook\":" + (s.discord() != null && s.discord().enabled())
                        + "}";
                sendJson(exchange, 200, json);
            }
        }
    }

    private class ApiSwitchLangHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
            String newLocale = params.get("lang");
            if (newLocale != null && !newLocale.isEmpty()) {
                // Redirect back with a cookie to remember preference
                // For now, just redirect to login page
                // In a full implementation, we'd store the preference
            }
            redirect(exchange, "/login");
        }
    }

    private class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String token = getSessionToken(exchange);
            if (token != null) sessions.remove(token);
            exchange.getResponseHeaders().add("Set-Cookie", "session=; HttpOnly; Path=/; Max-Age=0");
            redirect(exchange, "/login");
        }
    }

    private class ThemeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String css = "/* Theme variables - dark theme */\n"
                    + ":root{--bg:#0f0f1a;--surface:rgba(255,255,255,0.03);--border:rgba(255,255,255,0.06);--text:#e0e0e0;--primary:#667eea;--danger:#ff6b6b;--success:#51cf66;--warning:#ffd43b}\n";
            sendResponse(exchange, 200, "text/css", css.getBytes(StandardCharsets.UTF_8));
        }
    }

    // ========== Utility Methods ==========

    private void sendHtml(HttpExchange exchange, int code, String html) throws IOException {
        sendResponse(exchange, code, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
    }

    private void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        sendResponse(exchange, code, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private void sendResponse(HttpExchange exchange, int code, String contentType, byte[] data) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.getResponseHeaders().add("Cache-Control", "no-cache, no-store, must-revalidate");
        exchange.sendResponseHeaders(code, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String val = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                params.put(key, val);
            }
        }
        return params;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String formatTime(long millis) {
        if (millis <= 0) return "-";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd HH:mm");
        return sdf.format(new java.util.Date(millis));
    }

    // ========== Ranking API ==========
    private class ApiRankingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String token = getSessionToken(exchange);
            if (!validateSession(token)) { sendJson(exchange, 401, "{\"error\":\"unauthorized\"}"); return; }
            var tracker = plugin.getPunishmentTracker();
            StringBuilder json = new StringBuilder("{\"ranking\":[");
            if (tracker != null) {
                var records = tracker.getAllRecords();
                records.sort((a, b) -> Integer.compare(b.totalViolations(), a.totalViolations()));
                int limit = Math.min(20, records.size());
                for (int i = 0; i < limit; i++) {
                    var r = records.get(i);
                    if (i > 0) json.append(",");
                    json.append("{\"rank\":").append(i + 1)
                        .append(",\"player\":\"").append(escapeJson(r.playerName())).append("\"")
                        .append(",\"count\":").append(r.count())
                        .append(",\"total\":").append(r.totalViolations())
                        .append(",\"lastSeen\":\"").append(formatTime(r.lastViolation())).append("\"}");
                }
            }
            json.append("]}");
            sendJson(exchange, 200, json.toString());
        }
    }

    // ========== SSE Events (real-time push) ==========
    private class ApiEventsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String token = getSessionToken(exchange);
            if (!validateSession(token)) { sendJson(exchange, 401, "{\"error\":\"unauthorized\"}"); return; }
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.getResponseHeaders().add("Connection", "keep-alive");
            exchange.sendResponseHeaders(200, 0);
            OutputStream os = exchange.getResponseBody();
            try {
                for (int i = 0; i < 60; i++) { // Keep alive for 60 seconds
                    var tracker = plugin.getPunishmentTracker();
                    int total = tracker != null ? tracker.getAllRecords().size() : 0;
                    String data = "data: {\"ts\":" + System.currentTimeMillis()
                            + ",\"online\":" + Bukkit.getOnlinePlayers().size()
                            + ",\"totalViolations\":" + total
                            + ",\"todayDetections\":" + (plugin.getStatsTracker() != null ? plugin.getStatsTracker().getTodayDetections() : 0)
                            + ",\"todayPunishments\":" + (plugin.getStatsTracker() != null ? plugin.getStatsTracker().getTodayPunishments() : 0)
                            + "}\n\n";
                    os.write(data.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                os.close();
            }
        }
    }

    // ========== Audit Log API ==========
    private class ApiAuditHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String token = getSessionToken(exchange);
            if (!validateSession(token)) { sendJson(exchange, 401, "{\"error\":\"unauthorized\"}"); return; }
            java.io.File auditFile = new java.io.File(plugin.getDataFolder(), "audit.log");
            StringBuilder json = new StringBuilder("{\"logs\":[");
            if (auditFile.exists()) {
                var lines = java.nio.file.Files.readAllLines(auditFile.toPath(), StandardCharsets.UTF_8);
                int start = Math.max(0, lines.size() - 50);
                for (int i = start; i < lines.size(); i++) {
                    if (i > start) json.append(",");
                    json.append("{\"msg\":\"").append(escapeJson(lines.get(i))).append("\"}");
                }
            }
            json.append("]}");
            sendJson(exchange, 200, json.toString());
        }
    }

    // ========== Prometheus Metrics ==========
    private class ApiMetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var tracker = plugin.getPunishmentTracker();
            var stats = plugin.getStatsTracker();
            int totalV = tracker != null ? tracker.getAllRecords().stream().mapToInt(ViolationRecord::totalViolations).sum() : 0;
            int online = Bukkit.getOnlinePlayers().size();
            double tps = Math.min(20.0, Bukkit.getTPS()[0]);
            int todayD = stats != null ? stats.getTodayDetections() : 0;
            int todayP = stats != null ? stats.getTodayPunishments() : 0;

            String metrics = "# HELP antilitematica_detections_total Total detections\n"
                    + "# TYPE antilitematica_detections_total counter\n"
                    + "antilitematica_detections_total " + totalV + "\n"
                    + "# HELP antilitematica_players_online Current online players\n"
                    + "# TYPE antilitematica_players_online gauge\n"
                    + "antilitematica_players_online " + online + "\n"
                    + "# HELP antilitematica_tps Current server TPS\n"
                    + "# TYPE antilitematica_tps gauge\n"
                    + "antilitematica_tps " + String.format("%.1f", tps) + "\n"
                    + "# HELP antilitematica_detections_today Today's detections\n"
                    + "# TYPE antilitematica_detections_today gauge\n"
                    + "antilitematica_detections_today " + todayD + "\n"
                    + "# HELP antilitematica_punishments_today Today's punishments\n"
                    + "# TYPE antilitematica_punishments_today gauge\n"
                    + "antilitematica_punishments_today " + todayP + "\n";
            sendResponse(exchange, 200, "text/plain; charset=utf-8", metrics.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static record Session(String token, long expiry) {}
}
