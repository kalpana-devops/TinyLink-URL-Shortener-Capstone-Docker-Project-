package edu.kucl.docker;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Random;

public class App {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 6;
    private static final Random RNG = new Random();

    private static JedisPool jedisPool;
    private static String logDir;

    public static void main(String[] args) throws Exception {
        String redisHost = getEnv("REDIS_HOST", "localhost");
        int redisPort = Integer.parseInt(getEnv("REDIS_PORT", "6379"));
        int appPort = Integer.parseInt(getEnv("APP_PORT", "8080"));
        logDir = getEnv("LOG_DIR", "/var/log/tinylink");

        new File(logDir).mkdirs();

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(16);
        jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 2000);

        HttpServer server = HttpServer.create(new InetSocketAddress(appPort), 0);
        server.createContext("/health", new HealthHandler());
        server.createContext("/api/shorten", new ShortenHandler());
        server.createContext("/api/resolve", new ResolveHandler());
        server.setExecutor(null);
        server.start();

        System.out.println("Java Backend started on port " + appPort);
        writeLog("system", "INFO", "Backend initialized on port " + appPort);
    }

    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (Jedis jedis = jedisPool.getResource()) {
                String ping = jedis.ping();
                if ("PONG".equalsIgnoreCase(ping)) {
                    respond(exchange, 200, "{\"status\":\"UP\",\"redis\":\"connected\"}");
                } else {
                    respond(exchange, 503, "{\"status\":\"DOWN\",\"redis\":\"unresponsive\"}");
                }
            } catch (Exception e) {
                respond(exchange, 503, "{\"status\":\"DOWN\",\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    static class ShortenHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 451, "{\"error\":\"method not allowed\"}");
                return;
            }

            String body = readBody(exchange);
            String url = extractJsonField(body, "url");

            if (url == null || url.isBlank()) {
                respond(exchange, 400, "{\"error\":\"missing url\"}");
                return;
            }

            String code = generateCode();

            try (Jedis jedis = jedisPool.getResource()) {
                jedis.set("short:" + code, url);
                writeLog("shorten", "INFO", "Shortened " + url + " -> " + code);
                respond(exchange, 201, "{\"code\":\"" + code + "\",\"url\":\"" + escape(url) + "\"}");
            } catch (Exception e) {
                writeLog("shorten", "ERROR", "Failed to shorten: " + e.getMessage());
                respond(exchange, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    static class ResolveHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String code = parseParam(query, "code");

            if (code == null || code.isBlank()) {
                respond(exchange, 400, "{\"error\":\"missing code\"}");
                return;
            }

            try (Jedis jedis = jedisPool.getResource()) {
                String url = jedis.get("short:" + code);
                if (url == null) {
                    respond(exchange, 404, "{\"error\":\"not found\"}");
                } else {
                    respond(exchange, 200, "{\"url\":\"" + escape(url) + "\"}");
                }
            } catch (Exception e) {
                respond(exchange, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    private static String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String extractJsonField(String json, String field) {
        String marker = "\"" + field + "\"";
        int idx = json.indexOf(marker);
        if (idx == -1) return null;
        int colon = json.indexOf(':', idx);
        if (colon == -1) return null;
        int firstQuote = json.indexOf('"', colon + 1);
        int secondQuote = (firstQuote == -1) ? -1 : json.indexOf('"', firstQuote + 1);
        if (firstQuote == -1 || secondQuote == -1) return null;
        return json.substring(firstQuote + 1, secondQuote);
    }

    private static String parseParam(String query, String name) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) return kv[1];
        }
        return null;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\"", "'");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String getEnv(String name, String fallback) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    private static void writeLog(String category, String level, String message) {
        if (logDir == null) return;
        File logFile = new File(logDir, "backend.log");
        String timestamp = Instant.now().toString();
        String entry = String.format("[%s] [%s] [%s] %s%n", timestamp, level, category, message);
        try (FileWriter fw = new FileWriter(logFile, true)) {
            fw.write(entry);
        } catch (IOException ignored) {
        }
    }
}
