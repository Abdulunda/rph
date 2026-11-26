package com.mycompany.routeph1;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Commuter Route Guide - Java back end (HTTP + JSON API + static web files). */
public class WebServer {

    static class ApiException extends RuntimeException {
        final int status;
        ApiException(int status, String msg) { super(msg); this.status = status; }
    }

    static final SecureRandom RNG = new SecureRandom();
    static final Map<String, Integer> SESSIONS = new ConcurrentHashMap<>();
    static final Map<String, Object[]> RESET = new ConcurrentHashMap<>();
    static Path webDir;
    static boolean dbReady = false;

    public static void main(String[] args) throws Exception {
        Properties cfg = new Properties();
        try (InputStream in = new FileInputStream(args.length > 0 ? args[0] : "config.properties")) {
            cfg.load(in);
        }
        Mailer.init(cfg);
        webDir = Paths.get(cfg.getProperty("web.dir", "web")).toAbsolutePath();

        try {
            Db.init(cfg);
            dbReady = true;
            System.out.println("[DB] Connected to MySQL (XAMPP). Tables are ready.");
        } catch (Throwable t) {
            System.out.println("[DB] NOT connected: " + t);
            System.out.println("[DB] Start MySQL in XAMPP and make sure lib/mysql-connector-j-*.jar exists.");
        }
        System.out.println(Mailer.isConfigured()
                ? "[MAIL] Gmail OTP enabled for " + Mailer.user
                : "[MAIL] Gmail not configured in config.properties - OTP codes will be printed here in the console.");

        int port = Integer.parseInt(cfg.getProperty("server.port", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", WebServer::handle);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(16));
        server.start();
        System.out.println("\nOpen the app:  http://localhost:" + port + "\n");
    }

    // ---------------------------------------------------------------- routing
    static void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                Object result = api(ex, path.substring(4), ex.getRequestMethod());
                send(ex, 200, "application/json; charset=utf-8", Json.write(result).getBytes(StandardCharsets.UTF_8));
            } else {
                serveStatic(ex, path);
            }
        } catch (ApiException e) {
            send(ex, e.status, "application/json; charset=utf-8", Json.write(Json.obj("error", e.getMessage())).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
            String msg = e instanceof java.sql.SQLException ? "Database error: " + e.getMessage() : "Server error: " + e;
            send(ex, 500, "application/json; charset=utf-8", Json.write(Json.obj("error", msg)).getBytes(StandardCharsets.UTF_8));
        }
    }

    static Object api(HttpExchange ex, String path, String method) throws Exception {
        Map<String, String> q = query(ex.getRequestURI().getRawQuery());
        String key = method + " " + path;
        switch (key) {
            // ---- public: route data from the original Java program ----
            case "GET /routes": {
                String d = RouteData.canonical(q.get("destination"));
                if (d == null) throw new ApiException(400, "Destination not available. Please enter only Caloocan or Bulacan.");
                return RouteData.list(d);
            }
            case "GET /route": {
                String d = RouteData.canonical(q.get("destination"));
                if (d == null) throw new ApiException(400, "Destination not available.");
                Map<String, Object> r = RouteData.details(d, intOf(q.get("no")));
                if (r == null) throw new ApiException(404, "Invalid route.");
                return r;
            }
        }

        needDb();
        switch (key) {
            // ---- accounts ----
            case "POST /register": return register(body(ex));
            case "POST /login": return login(body(ex));
            case "POST /resend-otp": return resendOtp(body(ex));
            case "POST /verify-otp": return verifyOtp(body(ex));
            case "POST /forgot": return forgot(body(ex));
            case "POST /verify-reset": return verifyReset(body(ex));
            case "POST /reset-password": return resetPassword(body(ex));
            case "POST /logout": SESSIONS.remove(token(ex)); return Json.obj("ok", true);
            case "GET /me": return Db.one("SELECT id,name,email FROM users WHERE id=?", needUser(ex));

            // ---- service advisories ----
            case "GET /advisories": return Db.query("SELECT id,title,body,details,routes,created_at AS at FROM advisories ORDER BY id DESC LIMIT 20");
            case "POST /advisories/read": {
                int uid = needUser(ex);
                Db.update("UPDATE users SET seen_advisory=GREATEST(seen_advisory,?) WHERE id=?", intOf(body(ex).get("id")), uid);
                return Json.obj("ok", true);
            }

            // ---- comments, likes, replies ----
            case "GET /comments": return comments(q, optUser(ex));
            case "POST /history": {
                int uid = needUser(ex);
                Map<String, String> b = body(ex);
                Db.insert("INSERT INTO search_history(user_id,origin,destination,route_no) VALUES(?,?,?,?)",
                        uid, "PUP Sta. Mesa", clip(b.get("destination"), 50), intOf(b.get("route")));
                return Json.obj("ok", true);
            }
            case "POST /comments": {
                int uid = needUser(ex);
                Map<String, String> b = body(ex);
                String d = RouteData.canonical(b.get("destination"));
                String text = clip(b.get("text"), 1000);
                if (d == null || text.isEmpty()) throw new ApiException(400, "No comment entered.");
                long id = Db.insert("INSERT INTO comments(user_id,destination,route_no,text) VALUES(?,?,?,?)", uid, d, intOf(b.get("route")), text);
                return Json.obj("ok", true, "id", id, "message", "Comment posted successfully!");
            }
            case "POST /like": {
                int uid = needUser(ex);
                int cid = intOf(body(ex).get("commentId"));
                if (Db.one("SELECT id FROM comments WHERE id=?", cid) == null) throw new ApiException(404, "Comment not found.");
                boolean liked;
                if (Db.one("SELECT id FROM comment_likes WHERE comment_id=? AND user_id=?", cid, uid) != null) {
                    Db.update("DELETE FROM comment_likes WHERE comment_id=? AND user_id=?", cid, uid);
                    liked = false;
                } else {
                    Db.insert("INSERT INTO comment_likes(comment_id,user_id) VALUES(?,?)", cid, uid);
                    liked = true;
                }
                return Json.obj("liked", liked, "likes", Db.one("SELECT COUNT(*) c FROM comment_likes WHERE comment_id=?", cid).get("c"));
            }
            case "POST /replies": {
                int uid = needUser(ex);
                Map<String, String> b = body(ex);
                String text = clip(b.get("text"), 1000);
                int cid = intOf(b.get("commentId"));
                if (text.isEmpty() || Db.one("SELECT id FROM comments WHERE id=?", cid) == null) throw new ApiException(400, "Reply cannot be empty.");
                Db.insert("INSERT INTO replies(comment_id,user_id,text) VALUES(?,?,?)", cid, uid, text);
                return Json.obj("ok", true);
            }

            // ---- notifications (likes + replies on my comments + advisories) ----
            case "GET /notifications": {
                int uid = needUser(ex);
                return Json.obj("unread", unreadCount(uid), "items", Db.query(
                    "SELECT * FROM (" +
                    " SELECT 'like' AS type, l.id AS id, u.name AS fromName, '' AS text, c.text AS comment, c.destination AS destination, c.route_no AS route, l.created_at AS at, l.is_read AS isRead" +
                    "  FROM comment_likes l JOIN comments c ON c.id=l.comment_id JOIN users u ON u.id=l.user_id WHERE c.user_id=? AND l.user_id<>?" +
                    " UNION ALL" +
                    " SELECT 'reply', r.id, u.name, r.text, c.text, c.destination, c.route_no, r.created_at, r.is_read" +
                    "  FROM replies r JOIN comments c ON c.id=r.comment_id JOIN users u ON u.id=r.user_id WHERE c.user_id=? AND r.user_id<>?" +
                    ") t ORDER BY at DESC LIMIT 50", uid, uid, uid, uid),
                    "advisories", Db.query("SELECT a.id, a.title, a.body, a.details, a.routes, a.created_at AS at, (a.id > u.seen_advisory) AS unread FROM advisories a, users u WHERE u.id=? ORDER BY a.id DESC LIMIT 10", uid));
            }
            case "POST /notifications/read": {
                int uid = needUser(ex);
                Db.update("UPDATE replies r JOIN comments c ON c.id=r.comment_id SET r.is_read=1 WHERE c.user_id=?", uid);
                Db.update("UPDATE comment_likes l JOIN comments c ON c.id=l.comment_id SET l.is_read=1 WHERE c.user_id=?", uid);
                Db.update("UPDATE users SET seen_advisory=(SELECT COALESCE(MAX(id),0) FROM advisories) WHERE id=?", uid);
                return Json.obj("ok", true);
            }
        }
        throw new ApiException(404, "Not found");
    }

    static long unreadCount(int uid) throws Exception {
        long n = ((Number) Db.one("SELECT COUNT(*) c FROM replies r JOIN comments c ON c.id=r.comment_id WHERE c.user_id=? AND r.user_id<>? AND r.is_read=0", uid, uid).get("c")).longValue();
        n += ((Number) Db.one("SELECT COUNT(*) c FROM comment_likes l JOIN comments c ON c.id=l.comment_id WHERE c.user_id=? AND l.user_id<>? AND l.is_read=0", uid, uid).get("c")).longValue();
        n += ((Number) Db.one("SELECT COUNT(*) c FROM advisories a, users u WHERE u.id=? AND a.id>u.seen_advisory", uid).get("c")).longValue();
        return n;
    }

    // ------------------------------------------------------------------ auth
    static Map<String, Object> startSession(String email) throws Exception {
        Map<String, Object> u = Db.one("SELECT id,name,email FROM users WHERE email=?", email);
        String token = hex(randomBytes(24));
        SESSIONS.put(token, ((Number) u.get("id")).intValue());
        return Json.obj("token", token, "user", u);
    }

    static Object register(Map<String, String> b) throws Exception {
        String name = clip(b.get("name"), 100), email = emailOf(b.get("email")), pw = b.getOrDefault("password", "");
        if (name.isEmpty()) throw new ApiException(400, "Please enter a username.");
        if (pw.length() < 8) throw new ApiException(400, "Password must be at least 8 characters.");
        Map<String, Object> u = Db.one("SELECT id,verified FROM users WHERE email=?", email);
        String salt = hex(randomBytes(16)), hash = hashPw(pw, salt);
        if (u != null && truthy(u.get("verified"))) throw new ApiException(409, "That email is already registered. Please log in.");
        if (u != null) Db.update("UPDATE users SET name=?,password_hash=?,salt=? WHERE email=?", name, hash, salt, email);
        else Db.insert("INSERT INTO users(name,email,password_hash,salt) VALUES(?,?,?,?)", name, email, hash, salt);
        return sendOtp(email);
    }

    /** Log in with email + password. Unverified accounts are asked for an email OTP first. */
    static Object login(Map<String, String> b) throws Exception {
        String email = emailOf(b.get("email"));
        Map<String, Object> u = Db.one("SELECT password_hash,salt,verified FROM users WHERE email=?", email);
        if (u == null || !hashPw(b.getOrDefault("password", ""), (String) u.get("salt")).equals(u.get("password_hash")))
            throw new ApiException(401, "Wrong email or password.");
        if (!truthy(u.get("verified"))) {
            Map<String, Object> r = new LinkedHashMap<>(sendOtp(email));
            r.put("needVerify", true);
            return r;
        }
        return startSession(email);
    }

    static Object resendOtp(Map<String, String> b) throws Exception {
        String email = emailOf(b.get("email"));
        if (Db.one("SELECT id FROM users WHERE email=?", email) == null) return Json.obj("otpSent", true, "email", email);
        return sendOtp(email);
    }

    static Map<String, Object> sendOtp(String email) throws Exception {
        Map<String, Object> last = Db.one("SELECT TIMESTAMPDIFF(SECOND, created_at, NOW()) AS s FROM otp_codes WHERE email=? ORDER BY id DESC LIMIT 1", email);
        if (last != null && ((Number) last.get("s")).intValue() < 30)
            throw new ApiException(429, "Please wait 30 seconds before requesting another code.");
        String code = String.format("%06d", RNG.nextInt(1_000_000));
        Db.update("DELETE FROM otp_codes WHERE email=?", email);
        Db.insert("INSERT INTO otp_codes(email,code_hash,expires_at) VALUES(?,?,DATE_ADD(NOW(), INTERVAL 5 MINUTE))", email, sha256(code + email));
        if (Mailer.isConfigured()) {
            try {
                Mailer.send(email, "Your RoutePH code: " + code,
                    "<div style='font-family:Arial,sans-serif;max-width:420px;margin:auto;padding:24px;border-radius:14px;background:#eef0f3'>"
                    + "<h2 style='margin:0 0 8px;color:#213a9b'>RoutePH</h2>"
                    + "<p style='color:#141a2b'>Use this 6-digit code to confirm it is you:</p>"
                    + "<p style='font-size:34px;letter-spacing:8px;font-weight:bold;background:#fff;color:#213a9b;padding:14px;text-align:center;border-radius:10px'>" + code + "</p>"
                    + "<p style='color:#6b7385;font-size:13px'>It expires in 5 minutes. If you did not request it, you can ignore this email.</p></div>");
            } catch (IOException e) {
                System.out.println("[MAIL] Failed: " + e);
                Db.update("DELETE FROM otp_codes WHERE email=?", email);   // so you can retry right away
                throw new ApiException(502, "Could not send the OTP email. Check mail.user and mail.appPassword in config.properties (see the Java console for the reason).");
            }
        } else {
            System.out.println("\n[DEV OTP] " + email + " -> " + code + "\n");
        }
        return Json.obj("otpSent", true, "email", email, "emailSent", Mailer.isConfigured());
    }

    /** Checks the OTP. Throws if wrong/expired; deletes it when correct. */
    static void checkOtp(String email, String code) throws Exception {
        Map<String, Object> o = Db.one("SELECT id,code_hash,attempts,(expires_at < NOW()) AS expired FROM otp_codes WHERE email=? ORDER BY id DESC LIMIT 1", email);
        if (o == null) throw new ApiException(400, "No code requested. Please request a new one.");
        if (truthy(o.get("expired"))) {
            Db.update("DELETE FROM otp_codes WHERE email=?", email);
            throw new ApiException(400, "That code expired. Please request a new one.");
        }
        if (((Number) o.get("attempts")).intValue() >= 5) {
            Db.update("DELETE FROM otp_codes WHERE email=?", email);
            throw new ApiException(429, "Too many wrong attempts. Please request a new code.");
        }
        if (!sha256(code.trim() + email).equals(o.get("code_hash"))) {
            Db.update("UPDATE otp_codes SET attempts=attempts+1 WHERE id=?", o.get("id"));
            throw new ApiException(400, "Incorrect code.");
        }
        Db.update("DELETE FROM otp_codes WHERE email=?", email);
    }

    /** OTP for e-mail verification after registering. */
    static Object verifyOtp(Map<String, String> b) throws Exception {
        String email = emailOf(b.get("email"));
        checkOtp(email, b.getOrDefault("code", ""));
        Db.update("UPDATE users SET verified=1 WHERE email=?", email);
        return startSession(email);
    }

    // ---- forgot password: step 1 email -> step 2 code -> step 3 new password ----
    static Object forgot(Map<String, String> b) throws Exception {
        String email = emailOf(b.get("email"));
        if (Db.one("SELECT id FROM users WHERE email=?", email) == null)
            throw new ApiException(404, "We could not find an account with that email.");
        return sendOtp(email);
    }

    static Object verifyReset(Map<String, String> b) throws Exception {
        String email = emailOf(b.get("email"));
        checkOtp(email, b.getOrDefault("code", ""));
        String t = hex(randomBytes(24));
        RESET.put(t, new Object[]{email, System.currentTimeMillis() + 10 * 60 * 1000});
        return Json.obj("resetToken", t);
    }

    static Object resetPassword(Map<String, String> b) throws Exception {
        Object[] r = RESET.get(b.getOrDefault("resetToken", ""));
        if (r == null || (Long) r[1] < System.currentTimeMillis()) throw new ApiException(400, "Reset session expired. Please start again.");
        String pw = b.getOrDefault("password", "");
        if (pw.length() < 8) throw new ApiException(400, "Password must be at least 8 characters.");
        String salt = hex(randomBytes(16));
        Db.update("UPDATE users SET password_hash=?,salt=?,verified=1 WHERE email=?", hashPw(pw, salt), salt, r[0]);
        RESET.remove(b.get("resetToken"));
        return Json.obj("ok", true);
    }

    // -------------------------------------------------------------- comments
    static Object comments(Map<String, String> q, int me) throws Exception {
        String d = RouteData.canonical(q.get("destination"));
        if (d == null) return new ArrayList<>();
        List<Map<String, Object>> cs = Db.query(
            "SELECT c.id, c.text, c.created_at AS at, u.name AS user, " +
            " (SELECT COUNT(*) FROM comment_likes l WHERE l.comment_id=c.id) AS likes, " +
            " (SELECT COUNT(*) FROM comment_likes l WHERE l.comment_id=c.id AND l.user_id=?) AS liked " +
            "FROM comments c JOIN users u ON u.id=c.user_id WHERE c.destination=? AND c.route_no=? ORDER BY c.id DESC LIMIT 50",
            me, d, intOf(q.get("route")));
        for (Map<String, Object> c : cs) {
            c.put("liked", ((Number) c.get("liked")).intValue() > 0);
            c.put("replies", Db.query("SELECT r.text, r.created_at AS at, u.name AS user FROM replies r JOIN users u ON u.id=r.user_id WHERE r.comment_id=? ORDER BY r.id", c.get("id")));
        }
        return cs;
    }

    // --------------------------------------------------------------- helpers
    static void needDb() {
        if (!dbReady) throw new ApiException(503, "Database is offline. Start MySQL in XAMPP, then restart the Java server.");
    }

    static String token(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        return h != null && h.startsWith("Bearer ") ? h.substring(7).trim() : "";
    }

    static boolean truthy(Object o) {
        return o instanceof Boolean ? (Boolean) o : o instanceof Number && ((Number) o).intValue() != 0;
    }

    static int optUser(HttpExchange ex) {
        Integer id = SESSIONS.get(token(ex));
        return id == null ? 0 : id;
    }

    static int needUser(HttpExchange ex) {
        Integer id = SESSIONS.get(token(ex));
        if (id == null) throw new ApiException(401, "Please log in.");
        return id;
    }

    static Map<String, String> body(HttpExchange ex) throws IOException {
        return Json.parse(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    static Map<String, String> query(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null) return m;
        for (String p : raw.split("&")) {
            int i = p.indexOf('=');
            if (i > 0) m.put(URLDecoder.decode(p.substring(0, i), StandardCharsets.UTF_8), URLDecoder.decode(p.substring(i + 1), StandardCharsets.UTF_8));
        }
        return m;
    }

    static int intOf(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    static String clip(String s, int max) {
        s = s == null ? "" : s.trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    static String emailOf(String s) {
        s = s == null ? "" : s.trim().toLowerCase();
        if (!s.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new ApiException(400, "Please enter a valid email address.");
        return s;
    }

    static byte[] randomBytes(int n) { byte[] b = new byte[n]; RNG.nextBytes(b); return b; }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static String sha256(String s) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    static String hashPw(String pw, String salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pw.toCharArray(), salt.getBytes(StandardCharsets.UTF_8), 65536, 256);
        return hex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
    }

    // ----------------------------------------------------------------- static
    static void serveStatic(HttpExchange ex, String path) throws IOException {
        if (path.equals("/")) path = "/index.html";
        Path f = webDir.resolve(path.substring(1)).normalize();
        if (!f.startsWith(webDir) || !Files.isRegularFile(f)) {
            send(ex, 404, "text/plain", "Not found".getBytes());
            return;
        }
        String n = f.getFileName().toString();
        String type = n.endsWith(".html") ? "text/html; charset=utf-8" : n.endsWith(".css") ? "text/css; charset=utf-8"
                : n.endsWith(".js") ? "application/javascript; charset=utf-8" : n.endsWith(".svg") ? "image/svg+xml"
                : n.endsWith(".png") ? "image/png" : "application/octet-stream";
        send(ex, 200, type, Files.readAllBytes(f));
    }

    static void send(HttpExchange ex, int status, String type, byte[] data) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }
}
