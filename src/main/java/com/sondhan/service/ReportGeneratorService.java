package com.sondhan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sondhan.model.ArticleAnalysisResult;
import com.sondhan.model.ArticleClaimResult;
import com.sondhan.model.FactCheckResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ──────────────────────────────────────────────────────────────────────────────
 * Feature 5: Enhanced Fact-Check Report Generator
 * ──────────────────────────────────────────────────────────────────────────────
 * Generates structured Markdown reports and JSON exports for:
 *   • Single claim results (Text / Image / URL)
 *   • Full article analysis results (multi-claim)
 * ──────────────────────────────────────────────────────────────────────────────
 */
public class ReportGeneratorService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm");

    // ─────────────────────────────────────────────────────────────────────────
    //  Single Claim Report
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a full Markdown report for a single FactCheckResult.
     *
     * @param r              The fact-check result
     * @param verificationId The searches.id row number (−1 if not persisted yet)
     * @return Markdown string
     */
    public static String generateMarkdownReport(FactCheckResult r, int verificationId) {
        if (r == null) return "# No fact-check result available.\n";
        StringBuilder sb = new StringBuilder();

        // ── Header ────────────────────────────────────────────────────────────
        sb.append("# 📋 SONDHAN FACT-CHECK REPORT\n\n");
        sb.append("---\n\n");
        sb.append("| Field            | Value |\n");
        sb.append("|------------------|-------|\n");
        sb.append("| **Date**         | ").append(LocalDateTime.now().format(DATE_FMT)).append(" |\n");
        if (verificationId > 0) {
            sb.append("| **Verification ID** | #").append(verificationId).append(" |\n");
        }
        sb.append("| **Input Type**   | ").append(formatInputType(r.getInputType())).append(" |\n");
        sb.append("| **Engine**       | ").append(safe(r.getAiModel(), "Sondhan AI")).append(" |\n");
        sb.append("\n---\n\n");

        // ── Offline-fallback notice (item 5) ────────────────────────────────────
        if (r.getFallbackReason() != null && !r.getFallbackReason().isBlank()) {
            sb.append("> ⚠ **Offline fallback:** ").append(r.getFallbackReason()).append("\n\n");
        }

        // ── Claim ─────────────────────────────────────────────────────────────
        sb.append("## 🔍 Claim\n\n");
        sb.append("> ").append(safe(r.getClaim(), "N/A")).append("\n\n");

        // ── Source URL (if URL mode) ───────────────────────────────────────────
        if (r.getSourceUrl() != null && !r.getSourceUrl().isBlank()) {
            sb.append("**Source URL:** [").append(r.getSourceUrl()).append("](").append(r.getSourceUrl()).append(")\n\n");
        }

        // ── Verdict & Confidence ──────────────────────────────────────────────
        sb.append("## ✅ Verdict\n\n");
        sb.append("**").append(verdictIcon(r.getVerdict())).append(" ").append(safe(r.getVerdict(), "UNVERIFIED")).append("**  \n");
        sb.append("Confidence: **").append(r.getConfidence()).append("%**\n\n");

        // ── Correction (what's actually true) ─────────────────────────────────
        if (r.getCorrection() != null && !r.getCorrection().isBlank()) {
            sb.append("## 💡 What's Actually True\n\n");
            sb.append(r.getCorrection()).append("\n\n");
        }

        // ── Explanation ───────────────────────────────────────────────────────
        sb.append("## 📝 Factual Rationale\n\n");
        sb.append(safe(r.getExplanation(), "No explanation provided.")).append("\n\n");

        // ── Evidence Balance (Feature 1) ──────────────────────────────────────
        int supCount  = r.getSupportingSources()    != null ? r.getSupportingSources().size()    : 0;
        int conCount  = r.getContradictingSources() != null ? r.getContradictingSources().size() : 0;
        int neuCount  = r.getNeutralSources()       != null ? r.getNeutralSources().size()       : 0;
        if (supCount + conCount + neuCount > 0) {
            sb.append("## ⚖️ Evidence Balance\n\n");
            sb.append("| Type | Count |\n|------|-------|\n");
            sb.append("| ✅ Supporting    | ").append(supCount).append(" |\n");
            sb.append("| ❌ Contradicting | ").append(conCount).append(" |\n");
            sb.append("| ➖ Neutral       | ").append(neuCount).append(" |\n\n");
        }

        // ── Sources ───────────────────────────────────────────────────────────
        sb.append("## 📚 Verified Sources & Citations\n\n");
        appendSourceSection(sb, "Supporting Evidence", r.getSupportingSources(), "✅");
        appendSourceSection(sb, "Contradicting Evidence", r.getContradictingSources(), "❌");
        appendSourceSection(sb, "Neutral / Background", r.getNeutralSources(), "➖");

        // Flat list fallback if no classified sources
        if (supCount + conCount + neuCount == 0 && r.getSources() != null) {
            int i = 1;
            for (FactCheckResult.Source s : r.getSources()) {
                sb.append(i++).append(". **[").append(s.type.toUpperCase()).append("]** ")
                  .append(s.title).append("  \n   → ").append(s.url).append("\n");
            }
            sb.append("\n");
        }

        // Item 4: explicit retrieval-failure notice when nothing was retrieved.
        if (supCount + conCount + neuCount == 0
                && (r.getSources() == null || r.getSources().isEmpty())) {
            sb.append("## ⚠ Source Retrieval\n\n");
            sb.append("No sources could be retrieved or analyzed for this claim. "
                    + "This is a retrieval failure, not an inconclusive verification.\n\n");
        } else if (supCount == 0 && conCount == 0 && neuCount > 0) {
            sb.append("> ⚠ **Note:** no supporting or contradicting evidence was found — "
                    + neuCount + " background source(s) below are context only. The claim remains unverified.\n\n");
        }

        // ── Claim Timeline (Feature 2) ────────────────────────────────────────
        if (r.getTimeline() != null && !r.getTimeline().isEmpty()) {
            sb.append("## 🕐 Claim Timeline\n\n");
            for (FactCheckResult.TimelineEvent ev : r.getTimeline()) {
                sb.append("**").append(ev.date).append("** — ").append(ev.title).append("  \n");
                sb.append(ev.description).append("  \n");
                if (ev.source != null && !ev.source.isBlank()) {
                    sb.append("*Source: ").append(ev.source).append("*\n\n");
                } else {
                    sb.append("\n");
                }
            }
        }

        // ── Technical Information (Image) ─────────────────────────────────────
        if ("image".equalsIgnoreCase(r.getInputType())) {
            sb.append("## 🔬 Technical Information\n\n");
            if (r.getSubmittedImageHash() != null && !r.getSubmittedImageHash().isBlank()) {
                sb.append("- **SHA-256 Hash:** `").append(r.getSubmittedImageHash()).append("`\n");
            }
            if (r.getSubmittedFormat() != null && !r.getSubmittedFormat().isBlank()) {
                sb.append("- **Format:** ").append(r.getSubmittedFormat()).append("\n");
            }
            if (r.getSubmittedDimensions() != null && !r.getSubmittedDimensions().isBlank()) {
                sb.append("- **Dimensions:** ").append(r.getSubmittedDimensions()).append("\n");
            }
            sb.append("\n");
        }

        // ── Footer ────────────────────────────────────────────────────────────
        sb.append("---\n\n");
        sb.append("*Generated by Sondhan v2.5 — AI-Powered Fact-Checking System*  \n");
        sb.append("*Disclaimer: AI fact-checking assists human review but does not replace professional journalism or legal advice.*\n");

        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Article Analysis Report (Feature 3 + Feature 5)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a Markdown report for a full ArticleAnalysisResult.
     */
    public static String generateArticleReport(ArticleAnalysisResult article, int verificationId) {
        if (article == null) return "# No article analysis result available.\n";
        StringBuilder sb = new StringBuilder();

        sb.append("# 📰 SONDHAN ARTICLE ANALYSIS REPORT\n\n");
        sb.append("---\n\n");
        sb.append("| Field           | Value |\n|-----------------|-------|\n");
        sb.append("| **Date**        | ").append(LocalDateTime.now().format(DATE_FMT)).append(" |\n");
        if (verificationId > 0) sb.append("| **Verification ID** | #").append(verificationId).append(" |\n");
        sb.append("| **Article URL** | ").append(safe(article.getUrl(), "N/A")).append(" |\n");
        sb.append("| **Publisher**   | ").append(safe(article.getPublisher(), "Unknown")).append(" |\n");
        if (article.getPublishDate() != null && !article.getPublishDate().isBlank())
            sb.append("| **Published**   | ").append(article.getPublishDate()).append(" |\n");
        sb.append("\n---\n\n");

        sb.append("## 📰 Article\n\n");
        sb.append("**").append(safe(article.getTitle(), "Untitled Article")).append("**\n\n");

        // Overall verdict
        sb.append("## 🏛️ Overall Assessment\n\n");
        sb.append("**").append(articleVerdictIcon(article.getOverallVerdict()))
          .append(" ").append(safe(article.getOverallVerdict(), "UNVERIFIED")).append("**\n\n");
        sb.append(safe(article.getOverallSummary(), "")).append("\n\n");

        // Per-claim breakdown
        sb.append("## 🔍 Detected Claims\n\n");
        if (article.getClaims() != null) {
            int idx = 1;
            for (ArticleClaimResult cr : article.getClaims()) {
                FactCheckResult r = cr.getResult();
                sb.append("### Claim ").append(idx++).append("\n\n");
                sb.append("> ").append(cr.getDetectedClaim()).append("\n\n");
                sb.append("**").append(verdictIcon(cr.getVerdict())).append(" ").append(cr.getVerdict()).append("**");
                if (r != null) sb.append(" — ").append(r.getConfidence()).append("% confidence");
                sb.append("\n\n");
                if (r != null && r.getExplanation() != null) sb.append(r.getExplanation()).append("\n\n");
                if (r != null && r.getCorrection() != null && !r.getCorrection().isBlank()) {
                    sb.append("💡 *").append(r.getCorrection()).append("*\n\n");
                }
                // Evidence sources
                if (r != null && r.getSources() != null && !r.getSources().isEmpty()) {
                    sb.append("**Sources:**\n");
                    for (FactCheckResult.Source s : r.getSources()) {
                        sb.append("- [").append(s.type.toUpperCase()).append("] ").append(s.title)
                          .append(" → ").append(s.url).append("\n");
                    }
                    sb.append("\n");
                }
            }
        }

        sb.append("---\n\n");
        sb.append("*Generated by Sondhan v2.5 — AI-Powered Fact-Checking System*  \n");
        sb.append("*Disclaimer: AI fact-checking assists human review but does not replace professional journalism.*\n");

        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  JSON Export
    // ─────────────────────────────────────────────────────────────────────────

    /** Serializes a FactCheckResult to a pretty-printed JSON string. */
    public static String exportJson(FactCheckResult r) {
        if (r == null) return "{}";
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("claim",       safe(r.getClaim(), ""));
            root.put("verdict",     safe(r.getVerdict(), "UNVERIFIED"));
            root.put("confidence",  r.getConfidence());
            root.put("explanation", safe(r.getExplanation(), ""));
            root.put("correction",  safe(r.getCorrection(), ""));
            root.put("aiModel",     safe(r.getAiModel(), "Sondhan AI"));
            root.put("inputType",   safe(r.getInputType(), "text"));
            root.put("sourceUrl",   safe(r.getSourceUrl(), ""));
            root.put("fallbackReason", safe(r.getFallbackReason(), ""));
            root.put("generatedAt", LocalDateTime.now().format(DATE_FMT));

            // Flat sources
            ArrayNode srcArr = root.putArray("sources");
            if (r.getSources() != null) {
                for (FactCheckResult.Source s : r.getSources()) {
                    ObjectNode sn = srcArr.addObject();
                    sn.put("title", s.title);
                    sn.put("url",   s.url);
                    sn.put("type",  s.type);
                    sn.put("publisher",   s.publisher);
                    sn.put("evidenceType", s.evidenceType.name());
                }
            }

            // Timeline
            ArrayNode tlArr = root.putArray("timeline");
            if (r.getTimeline() != null) {
                for (FactCheckResult.TimelineEvent ev : r.getTimeline()) {
                    ObjectNode en = tlArr.addObject();
                    en.put("date",        ev.date);
                    en.put("title",       ev.title);
                    en.put("description", ev.description);
                    en.put("source",      ev.source != null ? ev.source : "");
                }
            }

            // Image metadata if applicable
            if ("image".equalsIgnoreCase(r.getInputType())) {
                ObjectNode img = root.putObject("imageForensics");
                img.put("submittedHash",       safe(r.getSubmittedImageHash(), ""));
                img.put("submittedFormat",     safe(r.getSubmittedFormat(), ""));
                img.put("submittedDimensions", safe(r.getSubmittedDimensions(), ""));
                img.put("originalHash",        safe(r.getOriginalImageHash(), ""));
            }

            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception ex) {
            return "{\"error\": \"JSON export failed: " + ex.getMessage() + "\"}";
        }
    }

    /** Serializes an ArticleAnalysisResult to a pretty-printed JSON string. */
    public static String exportArticleJson(ArticleAnalysisResult article) {
        if (article == null) return "{}";
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("url",            safe(article.getUrl(), ""));
            root.put("title",          safe(article.getTitle(), ""));
            root.put("publisher",      safe(article.getPublisher(), ""));
            root.put("publishDate",    safe(article.getPublishDate(), ""));
            root.put("overallVerdict", safe(article.getOverallVerdict(), "UNVERIFIED"));
            root.put("overallSummary", safe(article.getOverallSummary(), ""));
            root.put("generatedAt",    LocalDateTime.now().format(DATE_FMT));

            ArrayNode claimsArr = root.putArray("claims");
            if (article.getClaims() != null) {
                for (ArticleClaimResult cr : article.getClaims()) {
                    ObjectNode cn = claimsArr.addObject();
                    cn.put("detectedClaim", cr.getDetectedClaim());
                    cn.put("verdict",       cr.getVerdict());
                    cn.put("confidence",    cr.getConfidence());
                    FactCheckResult r = cr.getResult();
                    if (r != null) {
                        cn.put("explanation", safe(r.getExplanation(), ""));
                        cn.put("correction",  safe(r.getCorrection(), ""));
                    }
                }
            }

            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception ex) {
            return "{\"error\": \"JSON export failed: " + ex.getMessage() + "\"}";
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static void appendSourceSection(StringBuilder sb, String header,
                                            List<FactCheckResult.Source> sources, String icon) {
        if (sources == null || sources.isEmpty()) return;
        sb.append("**").append(icon).append(" ").append(header).append(":**\n");
        int i = 1;
        for (FactCheckResult.Source s : sources) {
            sb.append(i++).append(". [").append(s.type.toUpperCase()).append("] ")
              .append(s.title);
            if (s.publisher != null && !s.publisher.isBlank())
                sb.append(" (").append(s.publisher).append(")");
            sb.append("  \n   → ").append(s.url).append("\n");
        }
        sb.append("\n");
    }

    private static String verdictIcon(String verdict) {
        if (verdict == null) return "?";
        return switch (verdict.toUpperCase()) {
            case "TRUE"                   -> "✅";
            case "FALSE"                  -> "❌";
            case "MISLEADING"             -> "⚠️";
            case "MODIFIED / OUT OF CONTEXT" -> "⚡";
            default                       -> "❓";
        };
    }

    private static String articleVerdictIcon(String verdict) {
        if (verdict == null) return "❓";
        return switch (verdict.toUpperCase()) {
            case "ACCURATE"                -> "✅";
            case "POTENTIALLY MISLEADING"  -> "⚠️";
            case "CONTAINS FALSE CLAIMS"   -> "❌";
            default                        -> "❓";
        };
    }

    private static String formatInputType(String t) {
        if (t == null) return "Text";
        return switch (t.toLowerCase()) {
            case "image" -> "🖼️ Image Analysis";
            case "url"   -> "🔗 URL / Article";
            default      -> "💬 Text Statement";
        };
    }

    private static String safe(String v, String fallback) {
        return (v != null && !v.isBlank()) ? v : fallback;
    }
}
