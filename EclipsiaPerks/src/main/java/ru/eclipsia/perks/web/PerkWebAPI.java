package ru.eclipsia.perks.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import ru.eclipsia.perks.node.PerkNode;
import ru.eclipsia.perks.player.PlayerPerkData;
import ru.eclipsia.perks.player.PlayerPerkManager;
import ru.eclipsia.perks.tree.PerkTreeManager;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Встроенный HTTP REST API для дерева перков.
 *
 * <p>Использует исключительно {@link com.sun.net.httpserver.HttpServer} из JDK
 * — никаких внешних зависимостей. Подходит для внешнего web-фронтенда,
 * который рисует дерево 2000×2000 на Canvas (вне ванильного 54-слотового GUI
 * Minecraft).
 *
 * <p>Эндпоинты:
 * <ul>
 *   <li>{@code GET  /api/perks/tree}             — все узлы (canvas, nodes, edges);</li>
 *   <li>{@code GET  /api/perks/player?uuid=...}  — данные игрока;</li>
 *   <li>{@code POST /api/perks/allocate}         — изучить узел (json: uuid, nodeId);</li>
 *   <li>{@code POST /api/perks/deallocate}       — сбросить узел (json: uuid, nodeId).</li>
 * </ul>
 *
 * <p>CORS: {@code Access-Control-Allow-Origin: *} на всех ответах,
 * preflight OPTIONS поддержан. Все ответы — UTF-8 JSON.
 *
 * <p><b>ВАЖНО:</b> мутирующие операции (allocate/deallocate) перед записью
 * запланируют синхронный таск в основной поток сервера, чтобы не словить race
 * с GUI/командами. Чтение дерева — read-only, безопасно из любого потока.
 */
public final class PerkWebAPI {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final PerkTreeManager treeManager;
    private final PlayerPerkManager playerManager;
    private final org.bukkit.plugin.Plugin plugin;
    private HttpServer server;

    public PerkWebAPI(org.bukkit.plugin.Plugin plugin,
                      PerkTreeManager treeManager,
                      PlayerPerkManager playerManager) {
        this.plugin = plugin;
        this.treeManager = treeManager;
        this.playerManager = playerManager;
    }

    /** Запустить сервер на указанном порту. */
    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/",                     new IndexHandler());
        server.createContext("/api/perks/tree",       new TreeHandler());
        server.createContext("/api/perks/player",     new PlayerHandler());
        server.createContext("/api/perks/allocate",   new AllocateHandler(true));
        server.createContext("/api/perks/deallocate", new AllocateHandler(false));
        server.setExecutor(Executors.newFixedThreadPool(2));
        server.start();
        plugin.getLogger().info("PerkWebAPI запущен на порту " + port);
    }

    /** Корректно остановить сервер. */
    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("PerkWebAPI остановлен");
        }
    }

    // =========================================================================
    // ОБЩИЕ УТИЛИТЫ
    // =========================================================================

    /** CORS + UTF-8 заголовки + ответ. */
    private static void respond(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private static String error(int code, String message) {
        JsonObject j = new JsonObject();
        j.addProperty("ok", false);
        j.addProperty("code", code);
        j.addProperty("error", message);
        return GSON.toJson(j);
    }

    /** OPTIONS preflight. */
    private static boolean handleOptions(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 204, "");
            return true;
        }
        return false;
    }

    /** Запустить таск в основном потоке и вернуть результат. */
    private <T> T sync(java.util.concurrent.Callable<T> task) throws Exception {
        if (Bukkit.isPrimaryThread()) {
            return task.call();
        }
        java.util.concurrent.CompletableFuture<T> fut = new java.util.concurrent.CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                fut.complete(task.call());
            } catch (Throwable t) {
                fut.completeExceptionally(t);
            }
        });
        return fut.get(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> out = new HashMap<>();
        if (query == null || query.isEmpty()) return out;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx < 0) {
                out.put(pair, "");
            } else {
                out.put(pair.substring(0, idx), java.net.URLDecoder.decode(
                        pair.substring(idx + 1), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    // =========================================================================
    // HANDLERS
    // =========================================================================

    /** GET / — встроенный HTML-фронтенд (canvas-рендер дерева). */
    private final class IndexHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            if (!"/".equals(ex.getRequestURI().getPath())) {
                respond(ex, 404, error(404, "Not found"));
                return;
            }
            byte[] bytes = INDEX_HTML.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        }
    }

    /** GET /api/perks/tree — всё дерево (canvas + nodes + edges). */
    private final class TreeHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                respond(ex, 405, error(405, "Method not allowed"));
                return;
            }

            JsonObject root = new JsonObject();
            JsonObject canvas = new JsonObject();
            canvas.addProperty("width", 2000);
            canvas.addProperty("height", 2000);
            root.add("canvas", canvas);

            JsonArray nodes = new JsonArray();
            JsonArray edges = new JsonArray();
            java.util.Set<String> edgeKeys = new java.util.HashSet<>();

            for (PerkNode n : treeManager.getAllNodes()) {
                JsonObject jn = new JsonObject();
                jn.addProperty("id", n.getId());
                jn.addProperty("type", n.getType().name());
                jn.addProperty("name", n.getName());
                jn.addProperty("x", n.getX());
                jn.addProperty("y", n.getY());
                jn.addProperty("cost", n.getCost());

                JsonObject stats = new JsonObject();
                for (Map.Entry<String, Integer> e : n.getStats().entrySet()) {
                    stats.addProperty(e.getKey(), e.getValue());
                }
                jn.add("stats", stats);
                nodes.add(jn);

                for (String to : n.getConnections()) {
                    String key = n.getId().compareTo(to) < 0
                            ? n.getId() + "::" + to
                            : to + "::" + n.getId();
                    if (edgeKeys.add(key)) {
                        JsonObject e = new JsonObject();
                        e.addProperty("from", n.getId());
                        e.addProperty("to", to);
                        edges.add(e);
                    }
                }
            }
            root.add("nodes", nodes);
            root.add("edges", edges);
            respond(ex, 200, GSON.toJson(root));
        }
    }

    /** GET /api/perks/player?uuid=... — данные игрока. */
    private final class PlayerHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                respond(ex, 405, error(405, "Method not allowed"));
                return;
            }
            Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
            String rawUuid = q.get("uuid");
            if (rawUuid == null) {
                respond(ex, 400, error(400, "Missing uuid"));
                return;
            }
            UUID uuid;
            try {
                uuid = UUID.fromString(rawUuid);
            } catch (IllegalArgumentException e) {
                respond(ex, 400, error(400, "Invalid uuid"));
                return;
            }

            PlayerPerkData data = playerManager.getPlayerData(uuid);
            JsonObject out = new JsonObject();
            out.addProperty("uuid", uuid.toString());
            out.addProperty("availablePoints", data.getAvailablePoints());

            JsonArray allocated = new JsonArray();
            for (String id : data.getAllocatedNodes()) allocated.add(id);
            out.add("allocatedNodes", allocated);

            respond(ex, 200, GSON.toJson(out));
        }
    }

    /** POST /api/perks/(de)allocate — мутации идут через основной поток. */
    private final class AllocateHandler implements HttpHandler {
        private final boolean allocate;
        AllocateHandler(boolean allocate) { this.allocate = allocate; }

        @Override public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                respond(ex, 405, error(405, "Method not allowed"));
                return;
            }

            String body;
            try (InputStream is = ex.getRequestBody()) {
                body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonObject req;
            try {
                req = GSON.fromJson(body, JsonObject.class);
            } catch (Exception e) {
                respond(ex, 400, error(400, "Invalid JSON"));
                return;
            }
            if (req == null || !req.has("uuid") || !req.has("nodeId")) {
                respond(ex, 400, error(400, "Missing uuid or nodeId"));
                return;
            }

            UUID uuid;
            try {
                uuid = UUID.fromString(req.get("uuid").getAsString());
            } catch (IllegalArgumentException e) {
                respond(ex, 400, error(400, "Invalid uuid"));
                return;
            }
            String nodeId = req.get("nodeId").getAsString();

            try {
                JsonObject result = sync(() -> mutate(uuid, nodeId));
                respond(ex, result.get("ok").getAsBoolean() ? 200 : 400, GSON.toJson(result));
            } catch (Exception e) {
                respond(ex, 500, error(500, "Internal error: " + e.getMessage()));
            }
        }

        /** Должен вызываться в основном потоке. */
        private JsonObject mutate(UUID uuid, String nodeId) {
            JsonObject out = new JsonObject();
            PerkNode node = treeManager.getNode(nodeId);
            if (node == null) {
                out.addProperty("ok", false);
                out.addProperty("error", "Unknown node: " + nodeId);
                return out;
            }

            PlayerPerkData data = playerManager.getPlayerData(uuid);

            if (allocate) {
                if (!treeManager.canAllocateNode(nodeId, data.getAllocatedNodes())) {
                    out.addProperty("ok", false);
                    out.addProperty("error", "Node not connected to allocated tree");
                    return out;
                }
                if (!data.allocateNode(nodeId, node.getCost())) {
                    out.addProperty("ok", false);
                    out.addProperty("error", "Not enough points or already allocated");
                    return out;
                }
            } else {
                if (!data.deallocateNode(nodeId, node.getCost())) {
                    out.addProperty("ok", false);
                    out.addProperty("error", "Node not allocated");
                    return out;
                }
            }
            playerManager.savePlayerData(uuid);

            out.addProperty("ok", true);
            out.addProperty("availablePoints", data.getAvailablePoints());
            JsonArray arr = new JsonArray();
            for (String id : data.getAllocatedNodes()) arr.add(id);
            out.add("allocatedNodes", arr);
            return out;
        }
    }

    // =========================================================================
    // ВСТРОЕННЫЙ HTML-ФРОНТЕНД (canvas-рендер дерева перков)
    // =========================================================================

    private static final String INDEX_HTML = """
            <!doctype html>
            <html lang="ru">
            <head>
            <meta charset="utf-8">
            <title>Eclipsia — Дерево перков</title>
            <style>
              html,body { margin:0; height:100%; background:#0d0d12; color:#eee;
                          font-family: 'Segoe UI', sans-serif; overflow:hidden; }
              #ui { position:fixed; top:0; left:0; right:0; padding:8px 12px;
                    background:rgba(0,0,0,0.6); z-index:10; display:flex;
                    gap:12px; align-items:center; flex-wrap:wrap; }
              #ui label { font-size:12px; opacity:0.7; }
              #ui input { background:#222; color:#fff; border:1px solid #444;
                          padding:4px 8px; font-family:monospace; }
              #ui button { background:#4a3a8a; color:#fff; border:0;
                           padding:4px 10px; cursor:pointer; }
              #ui button:hover { background:#5a4aaa; }
              #info { position:fixed; bottom:12px; left:12px; background:rgba(0,0,0,0.75);
                      padding:10px 14px; border-radius:6px; font-size:13px; max-width:280px;
                      display:none; z-index:10; }
              #info h3 { margin:0 0 4px 0; }
              canvas { display:block; cursor:grab; }
              canvas:active { cursor:grabbing; }
              .pts { color:#dd66ff; font-weight:bold; }
            </style>
            </head>
            <body>
            <div id="ui">
              <label>UUID игрока: <input id="uuid" size="36" placeholder="00000000-0000-0000-0000-000000000000"></label>
              <button onclick="loadPlayer()">Загрузить</button>
              <span>Очков: <span id="pts" class="pts">—</span></span>
              <span>Всего узлов: <span id="cnt">—</span></span>
              <span style="opacity:0.6;font-size:12px;">ЛКМ — изучить · ПКМ — сбросить · колесо — zoom</span>
            </div>
            <canvas id="cv"></canvas>
            <div id="info"></div>
            <script>
            const cv = document.getElementById('cv');
            const ctx = cv.getContext('2d');
            const info = document.getElementById('info');
            let tree = null;
            let allocated = new Set();
            let availablePoints = 0;
            let scale = 0.5, ox = 0, oy = 0;
            let dragging = false, dragX = 0, dragY = 0;
            let hover = null;
            let uuid = localStorage.getItem('eclipsiaUuid') || '';
            document.getElementById('uuid').value = uuid;

            const COLORS = {
              START: '#ffffff', SMALL: '#888', MEDIUM: '#5dc55d',
              NOTABLE: '#e8a93c', KEYSTONE: '#d23c3c'
            };
            const SIZES = { START: 16, SMALL: 8, MEDIUM: 12, NOTABLE: 16, KEYSTONE: 22 };

            function resize() { cv.width = innerWidth; cv.height = innerHeight; draw(); }
            addEventListener('resize', resize);

            async function loadTree() {
              const r = await fetch('/api/perks/tree');
              tree = await r.json();
              document.getElementById('cnt').textContent = tree.nodes.length;
              // центрируем
              ox = innerWidth/2 - tree.canvas.width*scale/2;
              oy = innerHeight/2 - tree.canvas.height*scale/2;
              draw();
            }
            async function loadPlayer() {
              uuid = document.getElementById('uuid').value.trim();
              if (!uuid) return;
              localStorage.setItem('eclipsiaUuid', uuid);
              const r = await fetch('/api/perks/player?uuid=' + uuid);
              const d = await r.json();
              if (d.error) { alert(d.error); return; }
              allocated = new Set(d.allocatedNodes);
              availablePoints = d.availablePoints;
              document.getElementById('pts').textContent = availablePoints;
              draw();
            }

            function draw() {
              if (!tree) return;
              ctx.fillStyle = '#0d0d12';
              ctx.fillRect(0, 0, cv.width, cv.height);
              ctx.save();
              ctx.translate(ox, oy);
              ctx.scale(scale, scale);
              // edges
              ctx.lineWidth = 2;
              for (const e of tree.edges) {
                const a = nodeById(e.from), b = nodeById(e.to);
                if (!a || !b) continue;
                const both = allocated.has(a.id) && allocated.has(b.id);
                ctx.strokeStyle = both ? '#a7d4ff' : '#3a3a44';
                ctx.beginPath();
                ctx.moveTo(a.x, a.y);
                ctx.lineTo(b.x, b.y);
                ctx.stroke();
              }
              // nodes
              for (const n of tree.nodes) {
                const r = SIZES[n.type] || 8;
                const isAlloc = allocated.has(n.id);
                ctx.fillStyle = isAlloc ? COLORS[n.type] : '#222';
                ctx.strokeStyle = COLORS[n.type] || '#888';
                ctx.lineWidth = 2;
                ctx.beginPath();
                ctx.arc(n.x, n.y, r, 0, Math.PI*2);
                ctx.fill();
                ctx.stroke();
              }
              ctx.restore();
            }
            function nodeById(id) { return tree.nodes.find(n => n.id === id); }

            function worldXY(ev) {
              const rect = cv.getBoundingClientRect();
              return {
                x: (ev.clientX - rect.left - ox) / scale,
                y: (ev.clientY - rect.top - oy) / scale
              };
            }
            function nodeAt(ev) {
              if (!tree) return null;
              const w = worldXY(ev);
              for (const n of tree.nodes) {
                const r = SIZES[n.type] || 8;
                const dx = n.x - w.x, dy = n.y - w.y;
                if (dx*dx + dy*dy <= r*r) return n;
              }
              return null;
            }

            cv.addEventListener('mousedown', ev => {
              if (ev.button === 0 || ev.button === 2) {
                const n = nodeAt(ev);
                if (n) {
                  if (!uuid) { alert('Введи UUID игрока'); return; }
                  const path = ev.button === 0 ? '/api/perks/allocate' : '/api/perks/deallocate';
                  fetch(path, {method:'POST', headers:{'Content-Type':'application/json'},
                                body: JSON.stringify({uuid, nodeId: n.id})})
                    .then(r => r.json()).then(d => {
                      if (!d.ok) { alert(d.error); return; }
                      allocated = new Set(d.allocatedNodes);
                      availablePoints = d.availablePoints;
                      document.getElementById('pts').textContent = availablePoints;
                      draw();
                    });
                  return;
                }
              }
              dragging = true; dragX = ev.clientX; dragY = ev.clientY;
            });
            cv.addEventListener('contextmenu', ev => ev.preventDefault());
            addEventListener('mousemove', ev => {
              if (dragging) {
                ox += ev.clientX - dragX; oy += ev.clientY - dragY;
                dragX = ev.clientX; dragY = ev.clientY;
                draw();
                return;
              }
              const n = nodeAt(ev);
              if (n !== hover) {
                hover = n;
                if (n) {
                  let stats = '';
                  for (const k in n.stats) stats += `<div>${k}: <b>${n.stats[k] > 0 ? '+' : ''}${n.stats[k]}</b></div>`;
                  info.innerHTML = `<h3 style="color:${COLORS[n.type]}">${n.name}</h3>
                    <div style="opacity:0.6">${n.type} · стоимость: ${n.cost}</div>${stats}`;
                  info.style.display = 'block';
                  info.style.left = (ev.clientX + 14) + 'px';
                  info.style.top = (ev.clientY + 14) + 'px';
                } else info.style.display = 'none';
              } else if (n) {
                info.style.left = (ev.clientX + 14) + 'px';
                info.style.top = (ev.clientY + 14) + 'px';
              }
            });
            addEventListener('mouseup', () => dragging = false);
            cv.addEventListener('wheel', ev => {
              ev.preventDefault();
              const z = ev.deltaY < 0 ? 1.1 : 0.9;
              const w = worldXY(ev);
              scale *= z;
              ox -= w.x * (scale - scale/z);
              oy -= w.y * (scale - scale/z);
              draw();
            }, {passive:false});

            resize();
            loadTree().then(() => { if (uuid) loadPlayer(); });
            </script>
            </body>
            </html>
            """;
}
