package com.sondhan.service;

import com.sondhan.model.User;

/**
 * In-memory session state: current user + API keys (Gemini, ChatGPT & Claude).
 * Singleton. In-memory storage for user privacy and security.
 *
 * Supported API Keys:
 *   1. Google Gemini (Free / AI Studio): SessionManager.setGeminiKey("AIzaSy...")
 *   2. OpenAI ChatGPT: SessionManager.setOpenAiKey("sk-...")
 *   3. Anthropic Claude: SessionManager.setClaudeKey("sk-ant-...")
 *   4. Google Vision (reverse image search, optional): VISION_API_KEY env var
 *      or SessionManager.setVisionKey(...); falls back to the Gemini key when
 *      the Vision API is enabled on the same Google Cloud project.
 */
public class SessionManager {

    private static User   currentUser;
    private static String geminiKey  = ""; // Google Gemini (Free Tier available)
    private static String openAiKey  = ""; // ChatGPT (GPT-4o)
    private static String claudeKey  = ""; // Anthropic Claude

    private SessionManager() {}

    // ── User ──────────────────────────────────────────────────────────────────
    public static void   setCurrentUser(User u) { currentUser = u; }
    public static User   getCurrentUser()        { return currentUser; }
    public static boolean isGuest()              { return currentUser == null; }

    // ── Google Gemini (Free API support) ──────────────────────────────────────
    public static void    setGeminiKey(String k) { geminiKey = (k == null ? "" : k.trim()); }
    public static String  getGeminiKey()         { return geminiKey; }
    public static boolean hasGeminiKey()         { return geminiKey != null && !geminiKey.isBlank(); }

    // ── OpenAI / ChatGPT ──────────────────────────────────────────────────────
    public static void    setOpenAiKey(String k) { openAiKey = (k == null ? "" : k.trim()); }
    public static String  getOpenAiKey()         { return openAiKey; }
    public static boolean hasOpenAiKey()         { return openAiKey != null && !openAiKey.isBlank(); }

    // ── Anthropic / Claude ────────────────────────────────────────────────────
    public static void    setClaudeKey(String k) { claudeKey = (k == null ? "" : k.trim()); }
    public static String  getClaudeKey()         { return claudeKey; }
    public static boolean hasClaudeKey()         { return claudeKey != null && !claudeKey.isBlank(); }

    // ── Google Vision (reverse image search) ──────────────────────────────────
    // Dedicated key optional: falls back to the Gemini key, which works when
    // the Vision API is enabled on the same Google Cloud project.
    private static String visionKey = "";

    public static void    setVisionKey(String k) { visionKey = (k == null ? "" : k.trim()); }
    /** Explicit vision key, else the Gemini key as documented fallback ("" if none). */
    public static String  getVisionKey() {
        if (visionKey != null && !visionKey.isBlank()) return visionKey;
        return getGeminiKey();
    }
    public static boolean hasVisionKey() {
        return (visionKey != null && !visionKey.isBlank()) || hasGeminiKey();
    }

    /** Returns true if at least one AI key is configured. */
    public static boolean hasAnyKey() {
        return hasGeminiKey() || hasOpenAiKey() || hasClaudeKey();
    }

    /**
     * Loads API keys from environment variables at startup (runtime, not build
     * time). Dialog-entered keys take precedence — env values only fill blanks.
     * Supported: GEMINI_API_KEY, OPENAI_API_KEY, ANTHROPIC_API_KEY.
     * Key values are NEVER logged; only masked source/length diagnostics.
     */
    public static void initFromEnv() {
        loadEnvKey("GEMINI_API_KEY", SessionManager::hasGeminiKey, SessionManager::setGeminiKey, "Gemini");
        loadEnvKey("OPENAI_API_KEY", SessionManager::hasOpenAiKey, SessionManager::setOpenAiKey, "ChatGPT");
        loadEnvKey("ANTHROPIC_API_KEY", SessionManager::hasClaudeKey, SessionManager::setClaudeKey, "Claude");
        loadEnvKey("VISION_API_KEY", () -> visionKey != null && !visionKey.isBlank(), SessionManager::setVisionKey, "Vision");
        if (!hasAnyKey()) {
            System.err.println("[Keys] No Live AI keys found (env GEMINI_API_KEY / OPENAI_API_KEY / ANTHROPIC_API_KEY, or Settings dialog). Using offline engine.");
        }
    }

    private static void loadEnvKey(String envVar, java.util.function.BooleanSupplier has,
                                   java.util.function.Consumer<String> set, String label) {
        if (has.getAsBoolean()) return;
        String v = System.getenv(envVar);
        if (v != null && !v.isBlank()) {
            set.accept(v);
            System.err.println("[Keys] " + label + " key loaded from env " + envVar
                + " (len=" + v.trim().length() + ", tail=..." + maskTail(v.trim()) + ")");
        }
    }

    private static String maskTail(String k) {
        return k.substring(Math.max(0, k.length() - 4));
    }

    /**
     * Helper – returns whichever key is available (Gemini -> Claude -> OpenAI).
     */
    public static String getApiKey() {
        if (hasGeminiKey()) return geminiKey;
        if (hasClaudeKey()) return claudeKey;
        return openAiKey;
    }

    @Deprecated
    public static void setApiKey(String k) { geminiKey = (k == null ? "" : k.trim()); }

    @Deprecated
    public static boolean hasApiKey() { return hasAnyKey(); }
}