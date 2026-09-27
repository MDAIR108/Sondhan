package com.sondhan.service;

import com.sondhan.model.ArticleAnalysisResult;
import com.sondhan.model.ArticleClaimResult;
import com.sondhan.model.FactCheckResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * ──────────────────────────────────────────────────────────────────────────────
 * Feature 3: Full URL / News Article Multi-Claim Verification
 * ──────────────────────────────────────────────────────────────────────────────
 * Pipeline:
 *   1. Fetch URL via UrlContentExtractor (already implemented)
 *   2. Detect 2–6 discrete checkable claims from article text (AI or offline)
 *   3. Verify each claim independently via FactCheckerService.checkTextClaim()
 *   4. Aggregate overall article verdict
 * ──────────────────────────────────────────────────────────────────────────────
 */
public class ArticleAnalysisService {

    /**
     * Full article analysis pipeline.
     *
     * @param model            Selected AI model name
     * @param urlStr           Article URL to fetch and analyze
     * @param progressCallback Receives progress status messages for UI display
     * @return ArticleAnalysisResult with all claims verified
     */
    public static ArticleAnalysisResult analyzeArticle(String model, String urlStr,
                                                        Consumer<String> progressCallback) throws Exception {
        ArticleAnalysisResult articleResult = new ArticleAnalysisResult();
        articleResult.setUrl(urlStr);

        // ── Stage 1: Fetch page ───────────────────────────────────────────────
        progress(progressCallback, "🌐 Fetching article from URL...");
        UrlContentExtractor.ExtractedPage page = UrlContentExtractor.extract(urlStr);

        articleResult.setTitle(page.title != null ? page.title : "Article");
        articleResult.setPublisher(extractPublisher(urlStr));
        articleResult.setPublishDate(page.publishDate != null ? page.publishDate : "");

        String articleText = page.text != null ? page.text : "";
        if (articleText.isBlank()) {
            articleResult.setOverallVerdict("UNVERIFIED");
            articleResult.setOverallSummary("Could not extract readable text from the provided URL.");
            articleResult.setClaims(List.of());
            return articleResult;
        }

        // ── Stage 2: Detect claims ────────────────────────────────────────────
        progress(progressCallback, "🔍 Detecting key factual claims in article...");
        List<String> detectedClaims = FactCheckerService.detectClaimsFromArticle(model, articleText);

        if (detectedClaims.isEmpty()) {
            // Fallback: treat article title as single claim
            detectedClaims = List.of(page.title != null && !page.title.isBlank()
                    ? page.title : "Article claim from " + urlStr);
        }

        // ── Stage 3: Verify each claim ────────────────────────────────────────
        List<ArticleClaimResult> claimResults = new ArrayList<>();
        for (int i = 0; i < detectedClaims.size(); i++) {
            String claimText = detectedClaims.get(i);
            progress(progressCallback, "⚖️ Verifying claim " + (i + 1) + " of " + detectedClaims.size() + "...");
            try {
                FactCheckResult fcr = FactCheckerService.checkTextClaim(model, claimText);
                fcr.setInputType("url");
                fcr.setSourceUrl(urlStr);
                claimResults.add(new ArticleClaimResult(claimText, fcr));
            } catch (Exception ex) {
                // Don't abort whole pipeline — add UNVERIFIED placeholder
                FactCheckResult placeholder = new FactCheckResult();
                placeholder.setClaim(claimText);
                placeholder.setVerdict("UNVERIFIED");
                placeholder.setConfidence(50);
                placeholder.setExplanation("Could not verify this claim: " + ex.getMessage());
                placeholder.setSources(List.of());
                placeholder.setFallbackReason("Article claim verification failed: " + ex.getMessage());
                claimResults.add(new ArticleClaimResult(claimText, placeholder));
            }
        }
        articleResult.setClaims(claimResults);

        // ── Stage 4: Aggregate verdict ────────────────────────────────────────
        progress(progressCallback, "📊 Aggregating article assessment...");
        articleResult.setOverallVerdict(aggregateVerdict(claimResults));
        articleResult.setOverallSummary(buildOverallSummary(articleResult));

        return articleResult;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void progress(Consumer<String> cb, String msg) {
        if (cb != null) cb.accept(msg);
    }

    /**
     * Aggregates claim verdicts into one article-level verdict.
     * Any FALSE claim → CONTAINS FALSE CLAIMS
     * Any MISLEADING → POTENTIALLY MISLEADING
     * All TRUE/UNVERIFIED → ACCURATE
     */
    private static String aggregateVerdict(List<ArticleClaimResult> claims) {
        boolean anyFalse      = false;
        boolean anyMisleading = false;
        for (ArticleClaimResult cr : claims) {
            String v = cr.getVerdict();
            if ("FALSE".equalsIgnoreCase(v) || "MODIFIED / OUT OF CONTEXT".equalsIgnoreCase(v)) anyFalse = true;
            if ("MISLEADING".equalsIgnoreCase(v)) anyMisleading = true;
        }
        if (anyFalse) return "CONTAINS FALSE CLAIMS";
        if (anyMisleading) return "POTENTIALLY MISLEADING";
        return "ACCURATE";
    }

    private static String buildOverallSummary(ArticleAnalysisResult result) {
        List<ArticleClaimResult> claims = result.getClaims();
        if (claims == null || claims.isEmpty()) return "No claims were extracted for verification.";

        long trueCount  = claims.stream().filter(c -> "TRUE".equalsIgnoreCase(c.getVerdict())).count();
        long falseCount = claims.stream().filter(c -> "FALSE".equalsIgnoreCase(c.getVerdict())).count();
        long misCount   = claims.stream().filter(c -> "MISLEADING".equalsIgnoreCase(c.getVerdict())).count();
        long unvCount   = claims.stream().filter(c -> "UNVERIFIED".equalsIgnoreCase(c.getVerdict())).count();

        return String.format(
            "Sondhan analyzed %d claims extracted from \"%s\". " +
            "Verdict breakdown: %d TRUE, %d FALSE, %d MISLEADING, %d UNVERIFIED. " +
            "Overall assessment: %s.",
            claims.size(), result.getTitle() != null ? result.getTitle() : "article",
            trueCount, falseCount, misCount, unvCount,
            result.getOverallVerdict()
        );
    }

    /** Attempts to extract publisher name from URL domain. */
    private static String extractPublisher(String url) {
        if (url == null) return "";
        try {
            String host = new java.net.URI(url).getHost();
            if (host == null) return "";
            host = host.startsWith("www.") ? host.substring(4) : host;
            String[] parts = host.split("\\.");
            return parts.length >= 2 ? capitalize(parts[parts.length - 2]) : host;
        } catch (Exception e) {
            return "";
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
