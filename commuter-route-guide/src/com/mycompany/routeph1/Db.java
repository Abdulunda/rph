package com.mycompany.routeph1;

import java.sql.*;
import java.util.*;

/** XAMPP MySQL access through JDBC. */
public class Db {

    static String url, user, pass;

    public static void init(Properties cfg) throws Exception {
        url = cfg.getProperty("db.url");
        user = cfg.getProperty("db.user", "root");
        pass = cfg.getProperty("db.password", "");
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection c = get(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS users (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, email VARCHAR(150) NOT NULL UNIQUE, password_hash VARCHAR(128) NOT NULL, salt VARCHAR(64) NOT NULL, verified TINYINT(1) NOT NULL DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            st.execute("CREATE TABLE IF NOT EXISTS otp_codes (id INT AUTO_INCREMENT PRIMARY KEY, email VARCHAR(150) NOT NULL, code_hash VARCHAR(64) NOT NULL, expires_at DATETIME NOT NULL, attempts INT NOT NULL DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, INDEX (email))");
            st.execute("CREATE TABLE IF NOT EXISTS comments (id INT AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL, destination VARCHAR(50) NOT NULL, route_no INT NOT NULL, text TEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE)");
            st.execute("CREATE TABLE IF NOT EXISTS replies (id INT AUTO_INCREMENT PRIMARY KEY, comment_id INT NOT NULL, user_id INT NOT NULL, text TEXT NOT NULL, is_read TINYINT(1) NOT NULL DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE CASCADE, FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE)");
            st.execute("CREATE TABLE IF NOT EXISTS comment_likes (id INT AUTO_INCREMENT PRIMARY KEY, comment_id INT NOT NULL, user_id INT NOT NULL, is_read TINYINT NOT NULL DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uq_like (comment_id, user_id), FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE CASCADE, FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE)");
            st.execute("CREATE TABLE IF NOT EXISTS advisories (id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(150) NOT NULL, body TEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            try { st.execute("ALTER TABLE users ADD COLUMN seen_advisory INT NOT NULL DEFAULT 0"); } catch (SQLException ignore) { /* already exists */ }
            try { st.execute("ALTER TABLE advisories ADD COLUMN details TEXT NULL"); } catch (SQLException ignore) { /* already exists */ }
            try { st.execute("ALTER TABLE advisories ADD COLUMN routes VARCHAR(255) NULL"); } catch (SQLException ignore) { /* already exists */ }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM advisories")) {
                if (rs.next() && rs.getInt(1) == 0)
                    st.execute("INSERT INTO advisories(title, body, details, routes) VALUES ('Road Closure Advisory', 'Route Closure Advisory: Road Closure and Alternative Routes Due to Ongoing Road Repairs', 'Attention, users Please be advised that Route 1 in Meycauayan, Bulacan, will be temporarily unavailable due to ongoing road repairs. Drivers are advised to use alternative routes until further notice.', 'Bulacan:1')");
            }
            st.execute("UPDATE advisories SET details='Attention, users Please be advised that Route 1 in Meycauayan, Bulacan, will be temporarily unavailable due to ongoing road repairs. Drivers are advised to use alternative routes until further notice.', routes='Bulacan:1' WHERE title='Road Closure Advisory' AND details IS NULL");
            st.execute("CREATE TABLE IF NOT EXISTS search_history (id INT AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL, origin VARCHAR(255), destination VARCHAR(50) NOT NULL, route_no INT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE)");
        }
    }

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(url, user, pass);
    }

    private static void bind(PreparedStatement ps, Object[] p) throws SQLException {
        for (int i = 0; i < p.length; i++) ps.setObject(i + 1, p[i]);
    }

    public static List<Map<String, Object>> query(String sql, Object... p) throws SQLException {
        try (Connection c = get(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, p);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) row.put(md.getColumnLabel(i), rs.getObject(i));
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    public static Map<String, Object> one(String sql, Object... p) throws SQLException {
        List<Map<String, Object>> r = query(sql, p);
        return r.isEmpty() ? null : r.get(0);
    }

    public static int update(String sql, Object... p) throws SQLException {
        try (Connection c = get(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, p);
            return ps.executeUpdate();
        }
    }

    public static long insert(String sql, Object... p) throws SQLException {
        try (Connection c = get(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, p);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                return k.next() ? k.getLong(1) : -1;
            }
        }
    }
}
