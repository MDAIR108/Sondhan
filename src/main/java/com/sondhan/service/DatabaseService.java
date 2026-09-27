package com.sondhan.service;

import com.sondhan.model.SearchHistory;
import com.sondhan.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ──────────────────────────────────────────────────────────────────────────────
 * Topic 3 – SQLite Relational Database via JDBC
 * ──────────────────────────────────────────────────────────────────────────────
 * Demonstrates:
 *   • JDBC connection lifecycle (open / close)
 *   • DDL: CREATE TABLE IF NOT EXISTS
 *   • DML: INSERT (with generated keys), SELECT, DELETE
 *   • PreparedStatement (prevents SQL injection)
 *   • Singleton pattern for shared DB access across the app
 * ──────────────────────────────────────────────────────────────────────────────
 * Extended (v2.5) with:
 *   • supporting_evidence / contradicting_evidence / neutral_evidence JSON blobs (Feature 1)
 *   • timeline JSON blob (Feature 2)
 *   • article_claims table with per-claim fact-check records (Feature 3)
 * ──────────────────────────────────────────────────────────────────────────────
 */
public class DatabaseService {

    private static DatabaseService instance;
    private Connection connection;

    private DatabaseService() {}

    public static synchronized DatabaseService getInstance() {
        if (instance == null) instance = new DatabaseService();
        return instance;
    }

    // ── Initialization ────────────────────────────────────────────────────────

    public void initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:sondhan.db");
            // Enable WAL for better concurrent read performance
            try (Statement s = connection.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL;");
                s.execute("PRAGMA foreign_keys=ON;");
            }
            createTables();
            System.out.println("[DB] SQLite ready: sondhan.db");
        } catch (Exception e) {
            throw new RuntimeException("DB init failed: " + e.getMessage(), e);
        }
    }

    private void createTables() throws SQLException {
        try (Statement s = connection.createStatement()) {
            // Users table
            s.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    name          TEXT    NOT NULL,
                    email         TEXT    UNIQUE NOT NULL,
                    password_hash TEXT    NOT NULL,
                    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP
                )""");

            // Searches / fact-check history table (core)
            s.execute("""
                CREATE TABLE IF NOT EXISTS searches (
                    id             INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id        INTEGER,
                    input_type     TEXT,
                    original_input TEXT,
                    claim          TEXT,
                    verdict        TEXT,
                    confidence     INTEGER,
                    explanation    TEXT,
                    sources_json   TEXT,
                    preloaded      INTEGER DEFAULT 0,
                    ai_model       TEXT    DEFAULT 'Unknown',
                    source_url     TEXT,
                    correction     TEXT,
                    supporting_evidence   TEXT,
                    contradicting_evidence TEXT,
                    neutral_evidence      TEXT,
                    timeline       TEXT,
                    created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users(id)
                )""");

            // article_claims – one row per claim detected inside an article URL (Feature 3)
            s.execute("""
                CREATE TABLE IF NOT EXISTS article_claims (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    search_id   INTEGER REFERENCES searches(id) ON DELETE CASCADE,
                    claim_text  TEXT,
                    verdict     TEXT,
                    confidence  INTEGER,
                    evidence_json TEXT,
                    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
                )""");

            // ── Auto-migrations: add columns if they don't exist in older DBs ──────
            String[] alterStatements = {
                "ALTER TABLE searches ADD COLUMN ai_model TEXT DEFAULT 'Unknown'",
                "ALTER TABLE searches ADD COLUMN source_url TEXT",
                "ALTER TABLE searches ADD COLUMN correction TEXT",
                "ALTER TABLE searches ADD COLUMN supporting_evidence TEXT",
                "ALTER TABLE searches ADD COLUMN contradicting_evidence TEXT",
                "ALTER TABLE searches ADD COLUMN neutral_evidence TEXT",
                "ALTER TABLE searches ADD COLUMN timeline TEXT",
            };
            for (String sql : alterStatements) {
                try { s.execute(sql); } catch (SQLException ignored) {}
            }
        }
    }

    // ── User Auth ─────────────────────────────────────────────────────────────

    public User registerUser(String name, String email, String password) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, email.toLowerCase());
            ps.setString(3, sha256(password));
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) return new User(rs.getInt(1), name, email);
        }
        return null;
    }

    public User loginUser(String email, String password) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, name, email FROM users WHERE email = ? AND password_hash = ?")) {
            ps.setString(1, email.toLowerCase());
            ps.setString(2, sha256(password));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return new User(rs.getInt("id"), rs.getString("name"), rs.getString("email"));
        }
        return null;
    }

    // ── Searches ──────────────────────────────────────────────────────────────

    /**
     * Full signature including evidence classification and timeline JSON blobs.
     * Returns the generated row ID (used as verificationId in reports).
     */
    public int saveSearch(int uid, String type, String orig, String claim,
                          String verdict, int conf, String expl,
                          String srcJson, boolean pre, String aiModel,
                          String sourceUrl, String correction,
                          String supportingJson, String contradictingJson,
                          String neutralJson, String timelineJson) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO searches (user_id,input_type,original_input,claim,verdict," +
                "confidence,explanation,sources_json,preloaded,ai_model,source_url,correction," +
                "supporting_evidence,contradicting_evidence,neutral_evidence,timeline) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, uid);
            ps.setString(2, type);
            ps.setString(3, orig);
            ps.setString(4, claim);
            ps.setString(5, verdict);
            ps.setInt(6, conf);
            ps.setString(7, expl);
            ps.setString(8, srcJson);
            ps.setInt(9, pre ? 1 : 0);
            ps.setString(10, aiModel != null ? aiModel : "Unknown");
            ps.setString(11, sourceUrl);
            ps.setString(12, correction);
            ps.setString(13, supportingJson);
            ps.setString(14, contradictingJson);
            ps.setString(15, neutralJson);
            ps.setString(16, timelineJson);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        }
        return -1;
    }

    /** Backward-compat: without evidence/timeline blobs. */
    public int saveSearch(int uid, String type, String orig, String claim,
                          String verdict, int conf, String expl,
                          String srcJson, boolean pre, String aiModel,
                          String sourceUrl, String correction) throws SQLException {
        return saveSearch(uid, type, orig, claim, verdict, conf, expl, srcJson, pre,
                          aiModel, sourceUrl, correction, null, null, null, null);
    }

    /** Backward-compat: without sourceUrl / correction. */
    public int saveSearch(int uid, String type, String orig, String claim,
                          String verdict, int conf, String expl,
                          String srcJson, boolean pre, String aiModel) throws SQLException {
        return saveSearch(uid, type, orig, claim, verdict, conf, expl, srcJson, pre, aiModel, null, null);
    }

    /** Backward-compat overload without aiModel. */
    public int saveSearch(int uid, String type, String orig, String claim,
                          String verdict, int conf, String expl,
                          String srcJson, boolean pre) throws SQLException {
        return saveSearch(uid, type, orig, claim, verdict, conf, expl, srcJson, pre, "Unknown", null, null);
    }

    // ── Article Claims (Feature 3) ────────────────────────────────────────────

    public void saveArticleClaim(int searchId, String claimText, String verdict,
                                  int confidence, String evidenceJson) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO article_claims (search_id,claim_text,verdict,confidence,evidence_json) " +
                "VALUES (?,?,?,?,?)")) {
            ps.setInt(1, searchId);
            ps.setString(2, claimText);
            ps.setString(3, verdict);
            ps.setInt(4, confidence);
            ps.setString(5, evidenceJson);
            ps.executeUpdate();
        }
    }

    /** Returns article_claims rows for a given searches.id. */
    public List<String[]> getArticleClaims(int searchId) throws SQLException {
        List<String[]> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT claim_text, verdict, confidence FROM article_claims WHERE search_id = ? ORDER BY id")) {
            ps.setInt(1, searchId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                    rs.getString("claim_text"),
                    rs.getString("verdict"),
                    String.valueOf(rs.getInt("confidence"))
                });
            }
        }
        return list;
    }

    // ── History Query ─────────────────────────────────────────────────────────

    public List<SearchHistory> getSearchHistory(int userId) throws SQLException {
        List<SearchHistory> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM searches WHERE user_id = ? ORDER BY created_at DESC")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                SearchHistory h = new SearchHistory();
                h.setId(rs.getInt("id"));
                h.setUserId(rs.getInt("user_id"));
                h.setInputType(rs.getString("input_type"));
                h.setOriginalInput(rs.getString("original_input"));
                h.setClaim(rs.getString("claim"));
                h.setVerdict(rs.getString("verdict"));
                h.setConfidence(rs.getInt("confidence"));
                h.setExplanation(rs.getString("explanation"));
                h.setSourcesJson(rs.getString("sources_json"));
                h.setPreloaded(rs.getInt("preloaded") == 1);
                h.setAiModel(rs.getString("ai_model"));
                try { h.setSourceUrl(rs.getString("source_url")); } catch (Exception ignored) {}
                try { h.setCorrection(rs.getString("correction")); } catch (Exception ignored) {}
                try { h.setSupportingEvidenceJson(rs.getString("supporting_evidence")); } catch (Exception ignored) {}
                try { h.setContradictingEvidenceJson(rs.getString("contradicting_evidence")); } catch (Exception ignored) {}
                try { h.setNeutralEvidenceJson(rs.getString("neutral_evidence")); } catch (Exception ignored) {}
                try { h.setTimelineJson(rs.getString("timeline")); } catch (Exception ignored) {}
                String ts = rs.getString("created_at");
                if (ts != null) {
                    try { h.setCreatedAt(LocalDateTime.parse(ts.replace(" ", "T"))); }
                    catch (Exception ignored) {}
                }
                list.add(h);
            }
        }
        return list;
    }

    public void deleteSearch(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM searches WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public void clearHistory(int userId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM searches WHERE user_id = ?")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    // ── Analytics Helpers (Feature 4) ─────────────────────────────────────────

    /** Returns counts grouped by verdict for the given user. */
    public List<String[]> getVerdictDistribution(int userId) throws SQLException {
        List<String[]> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT verdict, COUNT(*) AS cnt FROM searches WHERE user_id = ? GROUP BY verdict ORDER BY cnt DESC")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new String[]{ rs.getString("verdict"), String.valueOf(rs.getInt("cnt")) });
            }
        }
        return result;
    }

    /** Returns counts grouped by input_type for the given user. */
    public List<String[]> getMethodDistribution(int userId) throws SQLException {
        List<String[]> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT input_type, COUNT(*) AS cnt FROM searches WHERE user_id = ? GROUP BY input_type ORDER BY cnt DESC")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new String[]{ rs.getString("input_type"), String.valueOf(rs.getInt("cnt")) });
            }
        }
        return result;
    }

    /**
     * Feature 4: Returns counts grouped by source category (newspaper/book/journal/government).
     * Parses JSON source columns per row since sources are stored as JSON blobs.
     * Should be called on a background thread since it scans all history.
     */
    public List<String[]> getSourceDistribution(int userId) throws SQLException {
        Map<String, Integer> counts = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT sources_json, supporting_evidence, contradicting_evidence, neutral_evidence " +
                "FROM searches WHERE user_id = ?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                countSourcesInJson(rs.getString("sources_json"), counts);
                countSourcesInJson(rs.getString("supporting_evidence"), counts);
                countSourcesInJson(rs.getString("contradicting_evidence"), counts);
                countSourcesInJson(rs.getString("neutral_evidence"), counts);
            }
        }
        List<String[]> result = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            result.add(new String[]{ e.getKey(), String.valueOf(e.getValue()) });
        }
        return result;
    }

    /** Parses a JSON array of source objects and increments counts by type. */
    private void countSourcesInJson(String json, Map<String, Integer> counts) {
        if (json == null || json.isBlank()) return;
        try {
            JsonNode arr = new ObjectMapper().readTree(json);
            if (arr.isArray()) {
                for (JsonNode src : arr) {
                    String type = src.path("type").asText("website");
                    counts.merge(type, 1, Integer::sum);
                }
            }
        } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
            System.out.println("[DB] Connection closed.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}