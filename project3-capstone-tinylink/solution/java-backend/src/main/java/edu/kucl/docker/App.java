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
