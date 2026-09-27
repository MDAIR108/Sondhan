package com.sondhan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sondhan.model.FactCheckResult;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * ──────────────────────────────────────────────────────────────────────────────
 * ReverseImageSearchService — legitimate image-origin lookup.
 * ──────────────────────────────────────────────────────────────────────────────
 * Uses Google Cloud Vision API web detection (a proper paid/free-tier API,
 * never page scraping) to find where an image appears on the public web.
 * This is the sanctioned path for images from login-walled platforms
 * (Facebook/Instagram/X CDN links are never scraped — the image BYTES the
 * user already has are submitted to the API instead).
 *
 * Availability: requires a vision-capable key (dedicated VISION_API_KEY env
 * var, else falls back to the Gemini key — both work if the Vision API is
 * enabled on the Google Cloud project). Without a key the service reports
 * not-configured and callers skip it silently (logged, no fake results).
 * ──────────────────────────────────────────────────────────────────────────────
 */
public class ReverseImageSearchService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** A public page the image was found on. */
    public static class PageMatch {
        public final String url;
        public final String title;
        public PageMatch(String url, String title) {
            this.url   = url != null ? url : "";
            this.title = (title != null && !title.isBlank()) ? title : this.url;
        }
    }

    /** Web-detection outcome: origin pages + best-guess labels. */
    public static class WebDetectionResult {
        public final List<PageMatch> pages;
        public final List<String> bestGuessLabels;
        public WebDetectionResult(List<PageMatch> pages, List<String> labels) {
            this.pages           = pages != null ? pages : List.of();
            this.bestGuessLabels = labels != null ? labels : List.of();
        }
        public boolean hasMatches() { return !pages.isEmpty(); }
    }

    /** True when any usable key is present (dedicated vision key or Gemini fallback). */
    public static boolean isConfigured() {
        return SessionManager.hasVisionKey();
    }

    /**
     * Submits image bytes to Cloud Vision web detection.
     * @throws IllegalStateException when no key is configured (callers skip).
     * @throws RuntimeException on API/transport failure (callers fall through).
     */
    public static WebDetectionResult searchByImage(File imageFile) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Reverse image search not configured — set VISION_API_KEY (or a Gemini key with Vision API enabled).");
        }
        byte[] bytes = Files.readAllBytes(imageFile.toPath());
        if (bytes.length == 0) throw new IllegalArgumentException("Image file is empty.");
        if (bytes.length > 15 * 1024 * 1024) {
            throw new IllegalArgumentException("Image exceeds the 15MB Vision API limit.");
        }
        String b64 = Base64.getEncoder().encodeToString(bytes);

        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode requests = root.putArray("requests");
        ObjectNode req = requests.addObject();
        req.putObject("image").put("content", b64);
        ArrayNode features = req.putArray("features");
        ObjectNode feat = features.addObject();
        feat.put("type", "WEB_DETECTION");
        feat.put("maxResults", 10);

        String key = SessionManager.getVisionKey();
        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create("https://vision.googleapis.com/v1/images:annotate?key=" + key))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(25))
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(root)))
                .build();
        HttpResponse<String> resp = HTTP.send(httpReq, HttpResponse.BodyHandlers.ofString());
        System.err.println("[Vision] web detection HTTP " + resp.statusCode());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Vision API error (" + resp.statusCode() + "): " + resp.body());
        }

        JsonNode web = MAPPER.readTree(resp.body()).path("responses").path(0).path("webDetection");
        List<PageMatch> pages = new ArrayList<>();
        JsonNode arr = web.path("pagesWithMatchingImages");
        if (arr.isArray()) {
            for (JsonNode p : arr) {
                String url = p.path("url").asText("");
                if (url.isBlank()) continue;
                pages.add(new PageMatch(url, p.path("pageTitle").asText("")));
                if (pages.size() >= 8) break;
            }
        }
        List<String> labels = new ArrayList<>();
        JsonNode lab = web.path("bestGuessLabels");
        if (lab.isArray()) {
            for (JsonNode l : lab) {
                String t = l.path("label").asText("");
                if (!t.isBlank()) labels.add(t);
            }
        }
        System.err.println("[Vision] web detection: " + pages.size() + " page(s), "
            + labels.size() + " best-guess label(s).");
        return new WebDetectionResult(pages, labels);
    }

    /**
     * Builds a FactCheckResult from web-detection output. Verdict stays
     * UNVERIFIED — origin pages are context for the image's provenance, not a
     * truth verdict on any claim. Pages become real neutral sources.
     */
    public static FactCheckResult toFactCheckResult(File imageFile, WebDetectionResult det) {
        FactCheckResult r = new FactCheckResult();
        r.setClaim("Visual web-origin check: \"" + imageFile.getName() + "\"");
        r.setVerdict("UNVERIFIED");
        r.setConfidence(65);
        StringBuilder expl = new StringBuilder();
        if (!det.bestGuessLabels.isEmpty()) {
            expl.append("Reverse image search best-guess labels: ")
                .append(String.join(", ", det.bestGuessLabels)).append(". ");
        }
        expl.append("This image was found on ").append(det.pages.size())
            .append(" public page(s) — review the origins below to judge its context. "
            + "Web detection establishes provenance, not truth; the depicted claim still needs verification.");
        r.setExplanation(expl.toString());
        r.setCorrection(null);
        List<FactCheckResult.Source> neut = new ArrayList<>();
        for (PageMatch p : det.pages) {
            String host = hostOf(p.url);
            neut.add(new FactCheckResult.Source(p.title, p.url, "website", host,
                FactCheckResult.EvidenceType.NEUTRAL));
        }
        r.setSupportingSources(List.of());
        r.setContradictingSources(List.of());
        r.setNeutralSources(neut);
        r.setSources(new ArrayList<>(neut));
        r.setPreloaded(false);
        r.setAiModel("Google Vision Web Detection [Live API]");
        r.setInputType("image");
        r.setSubmittedImageUrl(imageFile.toURI().toString());
        try {
            com.sondhan.util.ImageHashUtil.ImageInfo info =
                com.sondhan.util.ImageHashUtil.inspectImage(imageFile);
            r.setSubmittedImageHash(info.hash);
            r.setSubmittedDimensions(info.width + "x" + info.height);
            r.setSubmittedFormat(info.format);
        } catch (Exception ignored) {}
        return r;
    }

    private static String hostOf(String url) {
        try {
            String h = new URI(url).getHost();
            if (h == null) return "";
            return h.startsWith("www.") ? h.substring(4) : h;
        } catch (Exception e) {
            return "";
        }
    }
}
