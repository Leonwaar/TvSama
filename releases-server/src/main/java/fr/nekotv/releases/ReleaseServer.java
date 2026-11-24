package fr.nekotv.releases;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.Executors;

/** Dependency-free HTTP facade exposing GET /home/releases. */
public final class ReleaseServer {
    @FunctionalInterface
    public interface ReleaseProvider {
        String json(String region, String language) throws Exception;
    }

    private final int port;
    private final ReleaseProvider provider;
    private HttpServer server;

    public ReleaseServer(int port, ReleaseProvider provider) {
        this.port = port;
        this.provider = provider;
    }

    public synchronized void start() throws IOException {
        if (server != null) throw new IllegalStateException("Release server already started");
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/home/releases", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public synchronized void stop() {
        if (server != null) server.stop(0);
        server = null;
    }

    public synchronized int port() {
        return server == null ? port : server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        HashMap<String, String> query = parse(exchange.getRequestURI().getRawQuery());
        String region = query.getOrDefault("region", "FR").toUpperCase();
        String language = query.getOrDefault("lang", "fr").toLowerCase();
        try {
            String json = provider.json(region, language);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=300, stale-while-revalidate=3600");
            send(exchange, 200, json);
        } catch (Exception error) {
            send(exchange, 502, "{\"error\":\"release_source_unavailable\"}");
        }
    }

    private static HashMap<String, String> parse(String raw) {
        HashMap<String, String> values = new HashMap<>();
        if (raw == null) return values;
        for (String item : raw.split("&")) {
            String[] pair = item.split("=", 2);
            values.put(decode(pair[0]), pair.length == 2 ? decode(pair[1]) : "");
        }
        return values;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
