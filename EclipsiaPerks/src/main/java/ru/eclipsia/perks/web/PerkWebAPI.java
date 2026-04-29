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
        server.createContext("/api/perks/auth",       new AuthHandler());
        server.createContext("/api/perks/player",     new PlayerHandler());
        server.createContext("/api/perks/allocate",   new AllocateHandler(true));
        server.createContext("/api/perks/deallocate", new AllocateHandler(false));
        server.setExecutor(Executors.newFixedThreadPool(4));
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
            byte[] bytes = indexHtml().getBytes(StandardCharsets.UTF_8);
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
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name != null) out.addProperty("name", name);

            JsonArray allocated = new JsonArray();
            for (String id : data.getAllocatedNodes()) allocated.add(id);
            out.add("allocatedNodes", allocated);

            respond(ex, 200, GSON.toJson(out));
        }
    }

    /**
     * POST /api/perks/auth body {nick, code}
     * Возвращает {ok:true, uuid, name, availablePoints, allocatedNodes} если код совпадает.
     * Резолв ника — через Bukkit#getOfflinePlayer (включая историю и кэш).
     */
    private final class AuthHandler implements HttpHandler {
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
            if (req == null || !req.has("nick") || !req.has("code")) {
                respond(ex, 400, error(400, "Missing nick or code"));
                return;
            }
            String nick = req.get("nick").getAsString().trim();
            int code;
            try {
                code = Integer.parseInt(req.get("code").getAsString().trim());
            } catch (NumberFormatException e) {
                respond(ex, 400, error(400, "Invalid code"));
                return;
            }
            if (nick.isEmpty()) {
                respond(ex, 400, error(400, "Empty nick"));
                return;
            }

            // Ник → UUID. Bukkit.getOfflinePlayer кэширует, но всё равно на main thread безопаснее.
            UUID uuid;
            try {
                uuid = sync(() -> {
                    var off = Bukkit.getOfflinePlayer(nick);
                    return off == null ? null : off.getUniqueId();
                });
            } catch (Exception e) {
                respond(ex, 500, error(500, "Lookup failed: " + e.getMessage()));
                return;
            }
            if (uuid == null) {
                respond(ex, 404, error(404, "Игрок с таким ником не найден"));
                return;
            }
            if (!PerkAuthCodes.verify(uuid, code)) {
                respond(ex, 401, error(401, "Неверный код. Введи /perkscode в игре, чтобы получить новый."));
                return;
            }

            PlayerPerkData data = playerManager.getPlayerData(uuid);
            if (data == null) data = playerManager.getPlayerData(uuid); // ensure load
            JsonObject out = new JsonObject();
            out.addProperty("ok", true);
            out.addProperty("uuid", uuid.toString());
            out.addProperty("name", nick);
            out.addProperty("availablePoints", data == null ? 0 : data.getAvailablePoints());
            JsonArray allocated = new JsonArray();
            if (data != null) for (String id : data.getAllocatedNodes()) allocated.add(id);
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
    // FRONTEND HTML — ленивый кэш из resources/perks_tree.html
    // =========================================================================

    private static volatile String INDEX_HTML_CACHED;

    private static String indexHtml() {
        String cached = INDEX_HTML_CACHED;
        if (cached != null) return cached;
        try (java.io.InputStream is = PerkWebAPI.class.getResourceAsStream("/perks_tree.html")) {
            if (is == null) return "<h1>perks_tree.html не найден</h1>";
            byte[] bytes = is.readAllBytes();
            cached = new String(bytes, StandardCharsets.UTF_8);
            INDEX_HTML_CACHED = cached;
            return cached;
        } catch (java.io.IOException e) {
            return "<h1>Ошибка загрузки страницы: " + e.getMessage() + "</h1>";
        }
    }
}
