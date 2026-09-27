package com.sondhan.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain model for a single fact-check result.
 * Supports Text Statement, Image Analysis, and URL Check modes.
 * Extended with evidence classification (Feature 1), claim timeline (Feature 2),
 * article analysis support (Feature 3), and verification ID for reports (Feature 5).
 */
public class FactCheckResult {

    public enum InputType {
        TEXT,
        IMAGE,
        URL
    }

    /** Classification of a source's stance on the claim. */
    public enum EvidenceType {
        SUPPORTING,
        CONTRADICTING,
        NEUTRAL
    }

    /** A verified/cited source reference with evidence classification. */
    public static class Source {
        public final String title;
        public final String url;
        public final String type;       // "newspaper", "book", "journal", "government", "website"
        public final String publisher;  // e.g. "Reuters", "WHO", "Nature"
        public final EvidenceType evidenceType; // SUPPORTING | CONTRADICTING | NEUTRAL

        public Source(String title, String url) {
            this(title, url, "website", null, EvidenceType.NEUTRAL);
        }

        public Source(String title, String url, String type) {
            this(title, url, type, null, EvidenceType.NEUTRAL);
        }

        public Source(String title, String url, String type, String publisher, EvidenceType evidenceType) {
            this.title        = title;
            this.url          = url;
            this.type         = type;
            this.publisher    = publisher != null ? publisher : "";
            this.evidenceType = evidenceType != null ? evidenceType : EvidenceType.NEUTRAL;
        }

        /** Convenience: copy existing source with a given evidenceType. */
        public Source withEvidence(EvidenceType et) {
            return new Source(title, url, type, publisher, et);
        }
    }

    /**
     * A single chronological event related to the claim.
     * Source field is nullable – never fabricate without a source.
     */
    public static class TimelineEvent {
        public final String date;        // e.g. "2023-01" or "July 1969"
        public final String title;       // Short event title
        public final String description; // 1–2 sentence description
        public final String source;      // Nullable – only set when verifiable

        public TimelineEvent(String date, String title, String description, String source) {
            this.date        = date;
            this.title       = title;
            this.description = description;
            this.source      = source;
        }
    }

    // ── Core fields ───────────────────────────────────────────────────────────
    private String       claim;
    private String       verdict;      // TRUE | FALSE | MISLEADING | UNVERIFIED | MODIFIED / OUT OF CONTEXT
    private String       explanation;
    private int          confidence;   // 0–100
    private List<Source> sources;      // flat list (all sources – backward compat)
    private boolean      preloaded;
    private String       aiModel;      // "ChatGPT" | "Claude" | "Gemini" | "Preloaded"
    private String       inputType;    // "text" | "image" | "url"

    // ── Evidence classification (Feature 1) ───────────────────────────────────
    private List<Source> supportingSources    = new ArrayList<>();
    private List<Source> contradictingSources = new ArrayList<>();
    private List<Source> neutralSources       = new ArrayList<>();

    // ── Claim timeline (Feature 2) ────────────────────────────────────────────
    private List<TimelineEvent> timeline = new ArrayList<>();

    // ── Report verification ID (Feature 5) ───────────────────────────────────
    private int verificationId = -1;

    // ── Search-grounding flag (Part 3 correctness): true only when a Live AI
    //    response carried grounding metadata confirming live sources were used.
    private boolean grounded = false;

    // ── Offline-fallback diagnostics ──────────────────────────────────────────
    // fallbackReason: why the offline engine produced this result (null when a
    // live engine or preloaded match succeeded). Surfaced in logs + UI banner.
    private String  fallbackReason  = null;
    // sourcesVerified: SourceRetrievalService already fetch-checked this result.
    // Guards against double verification (and mutation of shared cached objects).
    private boolean sourcesVerified = false;
    // verificationUnavailable: live engines were attempted and failed
    // transiently AFTER retries — the UI must render the unavailable card
    // instead of any verdict/confidence/evidence placeholders.
    private boolean verificationUnavailable = false;
    private String  unavailableReason       = null;

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

    // ── Evidence Classification getters/setters ───────────────────────────────
    public List<Source> getSupportingSources()              { return supportingSources; }
    public void         setSupportingSources(List<Source> v)    { supportingSources = v != null ? v : new ArrayList<>(); }
    public List<Source> getContradictingSources()           { return contradictingSources; }
    public void         setContradictingSources(List<Source> v) { contradictingSources = v != null ? v : new ArrayList<>(); }
    public List<Source> getNeutralSources()                 { return neutralSources; }
    public void         setNeutralSources(List<Source> v)   { neutralSources = v != null ? v : new ArrayList<>(); }

    /** Total count of classified sources across all three buckets. */
    public int getTotalEvidenceCount() {
        return supportingSources.size() + contradictingSources.size() + neutralSources.size();
    }

    // ── Timeline getters/setters ──────────────────────────────────────────────
    public List<TimelineEvent> getTimeline()              { return timeline; }
    public void                setTimeline(List<TimelineEvent> v) { timeline = v != null ? v : new ArrayList<>(); }

    // ── Verification ID ───────────────────────────────────────────────────────
    public int  getVerificationId()     { return verificationId; }
    public void setVerificationId(int v){ verificationId = v; }

    // ── Search-grounding flag ─────────────────────────────────────────────────
    public boolean getGrounded()          { return grounded; }
    public boolean isGrounded()           { return grounded; }
    public void    setGrounded(boolean v) { grounded = v; }

    // ── Offline-fallback diagnostics ──────────────────────────────────────────
    public String  getFallbackReason()         { return fallbackReason; }
    public void    setFallbackReason(String v) { fallbackReason = v; }
    public boolean isSourcesVerified()         { return sourcesVerified; }
    public void    setSourcesVerified(boolean v) { sourcesVerified = v; }
    public boolean isVerificationUnavailable()          { return verificationUnavailable; }
    public void    setVerificationUnavailable(boolean v){ verificationUnavailable = v; }
    public String  getUnavailableReason()         { return unavailableReason; }
    public void    setUnavailableReason(String v) { unavailableReason = v; }

    /** True when at least one source exists in any evidence bucket. */
    public boolean hasAnyEvidence() {
        return getTotalEvidenceCount() > 0
            || (sources != null && !sources.isEmpty());
    }

    /**
     * Deep-ish copy: new lists sharing the immutable Source/TimelineEvent
     * objects. Used before mutating results handed out of shared caches
     * (e.g. PreloadedDatabase) so verification never pollutes the cache.
     */
    public FactCheckResult copy() {
        FactCheckResult c = new FactCheckResult();
        c.claim = claim; c.verdict = verdict; c.explanation = explanation;
        c.confidence = confidence; c.preloaded = preloaded; c.aiModel = aiModel;
        c.inputType = inputType; c.verificationId = verificationId;
        c.grounded = grounded; c.fallbackReason = fallbackReason;
        c.verificationUnavailable = verificationUnavailable;
        c.unavailableReason = unavailableReason;
        c.sources = sources != null ? new ArrayList<>(sources) : null;
        c.supportingSources = new ArrayList<>(supportingSources);
        c.contradictingSources = new ArrayList<>(contradictingSources);
        c.neutralSources = new ArrayList<>(neutralSources);
        c.timeline = new ArrayList<>(timeline);
        c.sourceUrl = sourceUrl; c.extractedText = extractedText;
        c.originalImageUrl = originalImageUrl; c.originalImageSource = originalImageSource;
        c.originalImageDate = originalImageDate; c.originalImageHash = originalImageHash;
        c.submittedImageUrl = submittedImageUrl; c.submittedImageHash = submittedImageHash;
        c.submittedDimensions = submittedDimensions; c.submittedFormat = submittedFormat;
        c.correction = correction;
        c.sourcesVerified = false; // a copy must be (re-)verified, never trusted blind
        return c;
    }

    // ── URL / Forensics getters/setters ──────────────────────────────────────
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

    /**
     * Convenience: populate the three evidence lists from the flat sources list
     * based on each source's evidenceType. Called after parsing AI response.
     */
    public void classifySourcesFromFlat() {
        supportingSources    = new ArrayList<>();
        contradictingSources = new ArrayList<>();
        neutralSources       = new ArrayList<>();
        if (sources == null) return;
        for (Source s : sources) {
            switch (s.evidenceType) {
                case SUPPORTING    -> supportingSources.add(s);
                case CONTRADICTING -> contradictingSources.add(s);
                default            -> neutralSources.add(s);
            }
        }
    }
}