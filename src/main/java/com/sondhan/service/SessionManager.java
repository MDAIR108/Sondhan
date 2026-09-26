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

    /** Returns true if at least one AI key is configured. */
    public static boolean hasAnyKey() {
        return hasGeminiKey() || hasOpenAiKey() || hasClaudeKey();
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