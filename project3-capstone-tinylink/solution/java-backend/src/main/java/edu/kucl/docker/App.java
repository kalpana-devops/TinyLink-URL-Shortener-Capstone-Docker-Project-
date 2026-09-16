package edu.kucl.docker;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.concurrent.Executors;
/**
* Mini Project 3 (capstone) — TinyLink backend.
*
* POST /api/shorten body: {"url":"https://..."} -> {"code":"aZ3kD1","url":"..."}
* GET /api/resolve?code=XXXX -> {"url":"https://..."} or 404
* GET /health
*
* Also appends a line to a shared log file on every successful shorten.
* That log file lives on the "shared-logs" NFS volume, which is mounted
* into BOTH this container and the PHP frontend container — proof that
* NFS storage (unlike a local named volume) can genuinely be written to
* and read from by more than one independent container at once.
*/
public class App {
private static JedisPool jedisPool;
private static final String ALPHABET =
"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
private static final SecureRandom RNG = new SecureRandom();
private static final int CODE_LENGTH = 6;
  System.out.println("TinyLink API listening on " + appPort
+ " (Redis at " + redisHost + ":" + redisPort + ")");
server.start();
}
/** POST /api/shorten body: {"url":"https://..."} -> {"code":"aZ3kD1"} */
static class ShortenHandler implements HttpHandler {
@Override
public void handle(HttpExchange exchange) throws IOException {
if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
respond(exchange, 405, "{\"error\":\"use POST\"}");
return;
}
String body = readBody(exchange);
String url = extractJsonField(body, "url");
if (url == null || url.isBlank()) {
respond(exchange, 400, "{\"error\":\"missing url\"}");
return;
}
try (Jedis jedis = jedisPool.getResource()) {
String code = generateUniqueCode(jedis);
jedis.set("short:" + code, url);
appendToSharedLog("[java-backend] created short:" + code + " -> " + url);
respond(exchange, 200,
"{\"code\":\"" + code + "\",\"url\":\"" + escape(url) + "\"}");
} catch (Exception e) {
respond(exchange, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
}
}
private String generateUniqueCode(Jedis jedis) {
String code;
do {
code = generateCode();
} while (jedis.exists("short:" + code)); // avoid the rare collision
return code;
}
}
/** GET /api/resolve?code=XXXX -> {"url":"https://..."} */
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
/**
* Minimal, dependency-free extraction of a single string field from a
* flat {"key":"value"} JSON body.
* NOTE: deliberately simple for teaching purposes — a real project
* should use a proper JSON library instead of string scanning.
*/
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
private static void respond(HttpExchange exchange, int status, String body) throws IOException
{
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
/**
* Appends one line to access.log on the shared NFS volume. Both this
* Java container and the PHP frontend container write to the SAME
* underlying file here, over NFS — the point of this method existing
* at all is to demonstrate that, not to be a serious logging solution.
*/
private static void appendToSharedLog(String line) {
String path = logDir + "/access.log";
try (FileWriter fw = new FileWriter(path, true)) {
fw.write(Instant.now() + " " + line + System.lineSeparator());
} catch (IOException e) {
// Don't fail the request just because logging failed.
System.err.println("Could not write shared log at " + path + ": " + e.getMessage());
}
}
}
private static String logDir;
public static void main(String[] args) throws IOException {
String redisHost = getEnv("REDIS_HOST", "redis");
int redisPort = Integer.parseInt(getEnv("REDIS_PORT", "6379"));
int appPort = Integer.parseInt(getEnv("APP_PORT", "8080"));
logDir = getEnv("LOG_DIR", "/var/log/tinylink");
JedisPoolConfig poolConfig = new JedisPoolConfig();
poolConfig.setMaxTotal(10);
jedisPool = new JedisPool(poolConfig, redisHost, redisPort);
HttpServer server = HttpServer.create(new InetSocketAddress(appPort), 0);
server.createContext("/api/shorten", new ShortenHandler());
server.createContext("/api/resolve", new ResolveHandler());
server.createContext("/health", exchange -> respond(exchange, 200,
"{\"status\":\"OK\"}"));
server.setExecutor(Executors.newFixedThreadPool(8));
