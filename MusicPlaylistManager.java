import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MusicPlaylistManager {
    private static final List<Song> songs = new ArrayList<>();
    private static final AtomicInteger nextId = new AtomicInteger(1);
    private static final Path PUBLIC_DIR = Path.of("public").toAbsolutePath();

    static class Song {
        int id;
        String title, artist;
        Song(int id, String title, String artist) {
            this.id = id; this.title = title; this.artist = artist;
        }
        String json() {
            return "{\"id\":" + id + ",\"title\":\"" + escape(title)
                + "\",\"artist\":\"" + escape(artist) + "\"}";
        }
    }

    public static void main(String[] args) throws Exception {
        songs.add(new Song(nextId.getAndIncrement(), "Midnight City", "M83"));
        songs.add(new Song(nextId.getAndIncrement(), "Blinding Lights", "The Weeknd"));
        songs.add(new Song(nextId.getAndIncrement(), "Yellow", "Coldplay"));

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api/songs", MusicPlaylistManager::handleSongs);
        server.createContext("/", MusicPlaylistManager::serveStatic);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Music Playlist Manager running at http://localhost:8080");
        System.out.println("Press Ctrl+C to stop the server.");
    }

    private static void handleSongs(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1); return;
        }

        String method = exchange.getRequestMethod();
        synchronized (songs) {
            if ("GET".equalsIgnoreCase(method)) {
                send(exchange, 200, songsJson());
            } else if ("POST".equalsIgnoreCase(method)) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String title = field(body, "title");
                String artist = field(body, "artist");
                if (title.isBlank() || artist.isBlank()) {
                    send(exchange, 400, "{\"error\":\"Title and artist are required.\"}");
                    return;
                }
                Song song = new Song(nextId.getAndIncrement(), title, artist);
                songs.add(song);
                send(exchange, 201, song.json());
            } else if ("DELETE".equalsIgnoreCase(method)) {
                String query = exchange.getRequestURI().getRawQuery();
                int id = -1;
                if (query != null && query.startsWith("id=")) {
                    try { id = Integer.parseInt(URLDecoder.decode(query.substring(3), StandardCharsets.UTF_8)); }
                    catch (NumberFormatException ignored) { }
                }
                final int targetId = id;
                boolean removed = songs.removeIf(song -> song.id == targetId);
                send(exchange, removed ? 200 : 404,
                    removed ? "{\"message\":\"Song removed.\"}" : "{\"error\":\"Song not found.\"}");
            } else {
                send(exchange, 405, "{\"error\":\"Method not allowed.\"}");
            }
        }
    }

    private static String songsJson() {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < songs.size(); i++) {
            if (i > 0) result.append(",");
            result.append(songs.get(i).json());
        }
        return result.append("]").toString();
    }

    private static String field(String json, String name) {
        String key = "\"" + name + "\"";
        int keyAt = json.indexOf(key);
        if (keyAt < 0) return "";
        int colon = json.indexOf(':', keyAt + key.length());
        int firstQuote = json.indexOf('"', colon + 1);
        if (colon < 0 || firstQuote < 0) return "";
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = firstQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) { value.append(c); escaped = false; }
            else if (c == '\\') escaped = true;
            else if (c == '"') break;
            else value.append(c);
        }
        return value.toString().trim();
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                   .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static void serveStatic(HttpExchange exchange) throws IOException {
        String requested = exchange.getRequestURI().getPath();
        if (requested.equals("/")) requested = "/index.html";
        Path file = PUBLIC_DIR.resolve(requested.substring(1)).normalize();
        if (!file.startsWith(PUBLIC_DIR) || !Files.exists(file) || Files.isDirectory(file)) {
            send(exchange, 404, "Not found"); return;
        }
        String type = file.toString().endsWith(".css") ? "text/css; charset=UTF-8"
            : file.toString().endsWith(".js") ? "application/javascript; charset=UTF-8"
            : "text/html; charset=UTF-8";
        byte[] data = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream out = exchange.getResponseBody()) { out.write(data); }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream out = exchange.getResponseBody()) { out.write(data); }
    }
}
