package com.redisclone.http;

import com.redisclone.command.CommandRegistry;
import com.redisclone.core.RedisDatabase;
import com.redisclone.protocol.RedisCommand;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * A lightweight HTTP API server (port 8080) that exposes the Redis keyspace
 * over JSON/HTTP. Uses only JDK's built-in com.sun.net.httpserver.
 *
 * Endpoints:
 *   POST /api/command   - body: { "args": ["SET", "key", "value"] }
 *   GET  /api/keys      - returns all current keys
 *   GET  /api/info      - returns server metadata
 */
public class HttpApiServer {
    private static final int HTTP_PORT = 8080;

    private final RedisDatabase db;
    private final CommandRegistry registry;
    private final HttpServer server;

    public HttpApiServer(RedisDatabase db, CommandRegistry registry) throws IOException {
        this.db = db;
        this.registry = registry;
        this.server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);

        server.createContext("/api/command", this::handleCommand);
        server.createContext("/api/keys",    this::handleKeys);
        server.createContext("/api/info",    this::handleInfo);
        server.createContext("/",            this::handleNotFound);

        // A small thread pool so HTTP requests don't block each other
        server.setExecutor(Executors.newFixedThreadPool(4));
    }

    public void start() {
        server.start();
        System.out.println("HTTP API running on http://localhost:" + HTTP_PORT);
    }

    // -------------------------------------------------------------------------
    // POST /api/command
    // Body (JSON): { "args": ["SET", "mykey", "hello"] }
    // -------------------------------------------------------------------------
    private void handleCommand(HttpExchange exchange) throws IOException {
        addCors(exchange);

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            List<String> args = parseJsonArgs(body);

            if (args == null || args.isEmpty()) {
                sendJson(exchange, 400, "{\"error\":\"Missing args array\"}");
                return;
            }

            RedisCommand cmd = new RedisCommand();
            for (String arg : args) {
                cmd.addArg(arg.getBytes(StandardCharsets.UTF_8));
            }

            // Execute on the calling thread (safe: HTTP executor is separate from
            // the NIO workers but both ultimately need to reach CommandExecutor.
            // For the HTTP path we execute inline since we control the thread.)
            byte[] resp = registry.execute(db, cmd);
            String decoded = decodeResp(resp);

            String json = "{\"result\":" + jsonString(decoded) + "}";
            sendJson(exchange, 200, json);

        } catch (Exception e) {
            sendJson(exchange, 500, "{\"error\":" + jsonString(e.getMessage()) + "}");
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/keys
    // -------------------------------------------------------------------------
    private void handleKeys(HttpExchange exchange) throws IOException {
        addCors(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try {
            RedisCommand cmd = new RedisCommand();
            cmd.addArg("KEYS".getBytes());
            cmd.addArg("*".getBytes());
            byte[] resp = registry.execute(db, cmd);
            String decoded = decodeResp(resp);
            sendJson(exchange, 200, "{\"result\":" + jsonString(decoded) + "}");
        } catch (Exception e) {
            sendJson(exchange, 500, "{\"error\":" + jsonString(e.getMessage()) + "}");
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/info
    // -------------------------------------------------------------------------
    private void handleInfo(HttpExchange exchange) throws IOException {
        addCors(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String json = String.format(
            "{\"version\":\"1.0.0\",\"port\":6379,\"httpPort\":8080,\"uptime\":%d}",
            System.currentTimeMillis()
        );
        sendJson(exchange, 200, json);
    }

    private void handleNotFound(HttpExchange exchange) throws IOException {
        addCors(exchange);
        sendJson(exchange, 404, "{\"error\":\"Not found\"}");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void addCors(HttpExchange ex) {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Very minimal JSON array parser: reads { "args": ["A","B","C"] }
     */
    private List<String> parseJsonArgs(String body) {
        try {
            int start = body.indexOf('[');
            int end   = body.lastIndexOf(']');
            if (start == -1 || end == -1) return null;

            String inner = body.substring(start + 1, end).trim();
            if (inner.isEmpty()) return List.of();

            String[] tokens = inner.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            java.util.List<String> result = new java.util.ArrayList<>();
            for (String t : tokens) {
                String trimmed = t.trim();
                // Strip surrounding quotes
                if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                    trimmed = trimmed.substring(1, trimmed.length() - 1);
                }
                // Unescape basic JSON escapes
                trimmed = trimmed.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n");
                result.add(trimmed);
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Decodes a RESP byte[] response into a human-readable string.
     */
    private String decodeResp(byte[] resp) {
        String r = new String(resp, StandardCharsets.UTF_8);

        if (r.startsWith("+")) return r.substring(1).replaceAll("\r\n$", "");
        if (r.startsWith("-")) return "ERROR: " + r.substring(1).replaceAll("\r\n$", "");
        if (r.startsWith(":")) return r.substring(1).replaceAll("\r\n$", "");
        if (r.startsWith("$-1")) return "(nil)";

        if (r.startsWith("$")) {
            // Bulk string: $N\r\nDATA\r\n
            int nl = r.indexOf("\r\n");
            if (nl != -1) {
                String data = r.substring(nl + 2);
                return data.replaceAll("\r\n$", "");
            }
        }

        if (r.startsWith("*-1")) return "(nil)";
        if (r.startsWith("*")) {
            // Array: *N\r\n...
            StringBuilder sb = new StringBuilder();
            String[] lines = r.split("\r\n");
            int idx = 1;
            int itemIndex = 1;
            while (idx < lines.length) {
                String line = lines[idx];
                if (line.startsWith("$") || line.startsWith(":")) {
                    idx++;
                    if (idx < lines.length) {
                        sb.append(itemIndex++).append(") ").append(lines[idx]).append("\n");
                    }
                } else if (!line.isEmpty()) {
                    sb.append(itemIndex++).append(") ").append(line).append("\n");
                }
                idx++;
            }
            return sb.toString().trim();
        }

        return r.replaceAll("\r\n", " ").trim();
    }

    /** Escapes a string for embedding in a JSON string value. */
    private String jsonString(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r")
                       .replace("\t", "\\t") + "\"";
    }
}
