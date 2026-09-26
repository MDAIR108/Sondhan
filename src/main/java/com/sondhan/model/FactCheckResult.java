package com.sondhan.model;

import java.util.List;

/**
 * Domain model for a single fact-check result.
 * Supports Text Statement, Image Analysis, and URL Check modes.
 */
public class FactCheckResult {

    public enum InputType {
        TEXT,
        IMAGE,
        URL
    }

    /** A verified/cited source reference. */
    public static class Source {
        public final String title;
        public final String url;
        public final String type; // "newspaper", "book", "journal", "government", "website"

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
    private String       verdict;      // TRUE | FALSE | MISLEADING | UNVERIFIED | MODIFIED / OUT OF CONTEXT
    private String       explanation;
    private int          confidence;   // 0–100
    private List<Source> sources;
    private boolean      preloaded;
    private List<String> summary;
    private String       aiModel;      // "ChatGPT" | "Claude" | "Gemini" | "Preloaded"
    private String       inputType;    // "text" | "image" | "url"

    // ── URL & Forensics Extensions ────────────────────────────────────────────
    private String       sourceUrl;           // Scraped webpage URL
    private String       extractedText;       // Scraped article/post text
    private String       originalImageUrl;    // Known authentic original image URL / path
    private String       originalImageSource; // Originating publisher/archive
    private String       originalImageDate;   // Publication date of original
    private String       originalImageHash;   // SHA-256 of authentic original image
    private String       submittedImageUrl;   // Extracted/submitted image path
    private String       submittedImageHash;  // SHA-256 of submitted image
    private String       submittedDimensions; // e.g. "800x600"
    private String       submittedFormat;     // e.g. "JPEG"
    private String       correction;          // Clear "What's actually true" explanation

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

    public InputType    getInputTypeEnum() {
        if ("image".equalsIgnoreCase(inputType)) return InputType.IMAGE;
        if ("url".equalsIgnoreCase(inputType))   return InputType.URL;
        return InputType.TEXT;
    }
    public void         setInputTypeEnum(InputType type) {
        this.inputType = type != null ? type.name().toLowerCase() : "text";
    }

    public String       getSourceUrl()       { return sourceUrl; }
    public void         setSourceUrl(String v) { sourceUrl = v; }
    public String       getExtractedText()   { return extractedText; }
    public void         setExtractedText(String v) { extractedText = v; }
    public String       getOriginalImageUrl() { return originalImageUrl; }
    public void         setOriginalImageUrl(String v) { originalImageUrl = v; }
    public String       getOriginalImageSource() { return originalImageSource; }
    public void         setOriginalImageSource(String v) { originalImageSource = v; }
    public String       getOriginalImageDate() { return originalImageDate; }
    public void         setOriginalImageDate(String v) { originalImageDate = v; }
    public String       getOriginalImageHash() { return originalImageHash; }
    public void         setOriginalImageHash(String v) { originalImageHash = v; }
    public String       getSubmittedImageUrl() { return submittedImageUrl; }
    public void         setSubmittedImageUrl(String v) { submittedImageUrl = v; }
    public String       getSubmittedImageHash() { return submittedImageHash; }
    public void         setSubmittedImageHash(String v) { submittedImageHash = v; }
    public String       getSubmittedDimensions() { return submittedDimensions; }
    public void         setSubmittedDimensions(String v) { submittedDimensions = v; }
    public String       getSubmittedFormat() { return submittedFormat; }
    public void         setSubmittedFormat(String v) { submittedFormat = v; }
    public String       getCorrection()      { return correction; }
    public void         setCorrection(String v) { correction = v; }

    public boolean      hasImageModification() {
        return originalImageUrl != null && !originalImageUrl.isBlank();
    }
}