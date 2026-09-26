package com.sondhan.model;

import java.util.List;

/**
 * Domain model for a single fact-check result.
 * Immutable Source inner class carries title + clickable URL.
 */
public class FactCheckResult {

    /** A verified/cited source reference. */
    public static class Source {
        public final String title;
        public final String url;
        public final String type; // "newspaper", "book", "website", "ai"

        public Source(String title, String url) {
            this(title, url, "website");
        }

        public Source(String title, String url, String type) {
            this.title = title;
            this.url   = url;
            this.type  = type;
        }
    }

    private String       claim;
    private String       verdict;      // TRUE | FALSE | MISLEADING | UNVERIFIED
    private String       explanation;
    private int          confidence;   // 0–100
    private List<Source> sources;
    private boolean      preloaded;
    private List<String> summary;
    private String       aiModel;      // "ChatGPT" | "Claude" | "Preloaded"
    private String       inputType;    // "text" | "image"

    // ── Getters / Setters ─────────────────────────────────────────────────────
    public String       getClaim()           { return claim; }
    public void         setClaim(String v)   { claim = v; }
    public String       getVerdict()         { return verdict; }
    public void         setVerdict(String v) { verdict = v; }
    public String       getExplanation()     { return explanation; }
    public void         setExplanation(String v) { explanation = v; }
    public int          getConfidence()      { return confidence; }
    public void         setConfidence(int v) { confidence = v; }
    public List<Source> getSources()         { return sources; }
    public void         setSources(List<Source> v) { sources = v; }
    public boolean      isPreloaded()        { return preloaded; }
    public void         setPreloaded(boolean v) { preloaded = v; }
    public List<String> getSummary()         { return summary; }
    public void         setSummary(List<String> v) { summary = v; }
    public String       getAiModel()         { return aiModel; }
    public void         setAiModel(String v) { aiModel = v; }
    public String       getInputType()       { return inputType; }
    public void         setInputType(String v) { inputType = v; }
}