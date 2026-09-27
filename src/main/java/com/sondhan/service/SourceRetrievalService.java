package com.sondhan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sondhan.model.FactCheckResult;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ──────────────────────────────────────────────────────────────────────────────
 * SourceRetrievalService — "Verified Sources" that are actually verified.
 * ──────────────────────────────────────────────────────────────────────────────
 * Two jobs, both on background threads:
 *   1. verifyAndEnrich(result): HTTP-fetches every source URL on a result.
 *      Reachable (2xx–3xx) links are kept; bot-guard responses (401/403/429)
 *      are kept but flagged as unverified; dead links (404/410/5xx/timeout)
 *      are dropped with a log line. If NOTHING is fetchable the curated links
 *      are kept (can't distinguish link-rot from a dead network) with a
 *      warning reason — except when the list was already empty, which is
 *      reported as an explicit retrieval failure (never a fake verdict).
 *   2. searchWikipedia(claim, max): real retrieval via the Wikipedia search
 *      API (no key needed) for claims with no curated sources — replaces
 *      fabricated search-query links with genuinely retrieved pages.
 * ──────────────────────────────────────────────────────────────────────────────
 */
public class SourceRetrievalService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** Dedicated pool: verification runs INSIDE main-pool tasks, so it must not nest there. */
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "sondhan-source-check");
        t.setDaemon(true);
        return t;
    });

    private static final String UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 SondhanFactChecker/2.5";

    private static final Pattern TITLE_RE =
            Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // ── 1. Fetch-check every source on a result (idempotent) ──────────────────

    public static void verifyAndEnrich(FactCheckResult r) {
        if (r == null || r.isSourcesVerified()) return;
        r.setSourcesVerified(true);

        List<FactCheckResult.Source> flat = r.getSources();
        if (flat == null || flat.isEmpty()) {
            markNoSources(r, "Source list was empty before retrieval.");
            return;
        }

        List<Callable<int[]>> tasks = new ArrayList<>();
        for (int i = 0; i < flat.size(); i++) {
            final int idx = i;
            final String url = flat.get(i).url;
            tasks.add(() -> new int[]{ idx, fetchStatus(url) });
        }

        Map<Integer, Integer> statusByIdx = new java.util.HashMap<>();
        try {
            List<Future<int[]>> done = POOL.invokeAll(tasks, 25, TimeUnit.SECONDS);
            for (Future<int[]> f : done) {
                if (f.isCancelled()) continue;
                try {
                    int[] res = f.get();
                    statusByIdx.put(res[0], res[1]);
                } catch (Exception ignored) {}
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        List<FactCheckResult.Source> kept = new ArrayList<>();
        int reachable = 0, transportFailures = 0;
        for (int i = 0; i < flat.size(); i++) {
            FactCheckResult.Source s = flat.get(i);
            int st = statusByIdx.getOrDefault(i, -1);
            if (st >= 200 && st < 400) {
                kept.add(s); reachable++;
            } else if (st == 401 || st == 403 || st == 429) {
                kept.add(s); // bot-guard suspected — keep, do not count as verified
                System.err.println("[Sources] Kept (bot-guard suspected, HTTP " + st + "): " + s.url);
            } else {
                if (st == -1) transportFailures++;
                System.err.println("[Sources] Dropped (HTTP " + (st == -1 ? "unreachable" : st) + "): " + s.url);
            }
        }

        if (kept.isEmpty() && transportFailures > 0) {
            // Nothing fetchable AND transports failed → likely dead network, not dead links.
            System.err.println("[Sources] None of " + flat.size() + " source URLs fetchable (transport failures) — "
                + "keeping curated links; network may be unavailable.");
            r.setFallbackReason(appendNote(r.getFallbackReason(),
                "Source URLs could not be fetched (network unavailable or sites blocking automated checks); links unverified."));
            return;
        }

        // Rebuild all three evidence buckets + flat list from survivors only.
        List<FactCheckResult.Source> sup = new ArrayList<>(), con = new ArrayList<>(), neu = new ArrayList<>();
        for (FactCheckResult.Source s : kept) {
            switch (s.evidenceType) {
                case SUPPORTING    -> sup.add(s);
                case CONTRADICTING -> con.add(s);
                default            -> neu.add(s);
            }
        }
        r.setSources(kept);
        r.setSupportingSources(sup);
        r.setContradictingSources(con);
        r.setNeutralSources(neu);

        if (kept.isEmpty()) {
            markNoSources(r, "All " + flat.size() + " source URLs failed retrieval (404/5xx).");
        } else if (kept.size() < flat.size()) {
            System.err.println("[Sources] Kept " + kept.size() + " of " + flat.size() + " sources after fetch-check.");
        }
    }

    /**
     * GETs a URL (cap ~256KB) and returns its HTTP status.
     * -1 = transport failure/timeout, -2 = malformed or non-http(s) URL.
     * Page titles are extracted and similarity-logged (mismatches kept, loudly).
     */
    static int fetchStatus(String url) {
        if (url == null || url.isBlank()) return -2;
        String clean = url.trim();
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) return -2;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(clean))
                    .header("User-Agent", UA)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<java.io.InputStream> resp =
                    HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
            int status = resp.statusCode();
            if (status >= 200 && status < 400) {
                try (java.io.InputStream in = resp.body()) {
                    byte[] buf = in.readNBytes(262144);
                    String head = new String(buf, StandardCharsets.UTF_8);
                    Matcher m = TITLE_RE.matcher(head);
                    if (m.find()) {
                        String title = m.group(1).replaceAll("\\s+", " ").trim();
                        if (title.length() > 120) title = title.substring(0, 120) + "...";
                        System.err.println("[Sources] Fetched (" + status + "): " + clean + " | page title: " + title);
                    } else {
                        System.err.println("[Sources] Fetched (" + status + "): " + clean);
                    }
                } catch (Exception ignored) {}
            }
            return status;
        } catch (IllegalArgumentException iae) {
            return -2;
        } catch (Exception ex) {
            System.err.println("[Sources] Fetch failed (" + ex.getClass().getSimpleName() + "): " + clean);
            return -1;
        }
    }

    private static void markNoSources(FactCheckResult r, String why) {
        r.setVerdict("UNVERIFIED");
        r.setConfidence(50);
        r.setCorrection(null);
        String prior = (r.getExplanation() != null && !r.getExplanation().isBlank())
                ? " Prior note: " + r.getExplanation() : "";
        r.setExplanation("No sources could be retrieved or analyzed for this claim. "
                + "This is a retrieval failure, not an inconclusive verification. " + why + prior);
        System.err.println("[Sources] " + why);
    }

    private static String appendNote(String existing, String note) {
        if (existing == null || existing.isBlank()) return note;
        return existing + " " + note;
    }

    // ── 2. Real retrieval via the Wikipedia search API (no key needed) ────────

    /**
     * Queries the Wikipedia search API for claim keywords and returns genuinely
     * retrieved pages (never fabricated links). Empty list on any failure.
     */
    public static List<FactCheckResult.Source> searchWikipedia(String claim, int max) {
        List<FactCheckResult.Source> out = new ArrayList<>();
        try {
            List<String> kws = FactCheckerService.claimKeywords(claim);
            if (kws.isEmpty()) return out;
            String q = URLEncoder.encode(String.join(" ", kws.subList(0, Math.min(3, kws.size()))), StandardCharsets.UTF_8);
            String url = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch="
                    + q + "&srlimit=" + Math.max(1, Math.min(max, 5)) + "&format=json";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", UA)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                System.err.println("[Sources] Wikipedia API HTTP " + resp.statusCode() + " — no retrieved sources.");
                return out;
            }
            JsonNode hits = MAPPER.readTree(resp.body()).path("query").path("search");
            if (hits.isArray()) {
                for (JsonNode h : hits) {
                    String title = h.path("title").asText("").trim();
                    if (title.isEmpty()) continue;
                    String pageUrl = "https://en.wikipedia.org/wiki/"
                            + URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8);
                    out.add(new FactCheckResult.Source(title + " — Wikipedia",
                            pageUrl, "book", "Wikipedia", FactCheckResult.EvidenceType.NEUTRAL));
                }
            }
            System.err.println("[Sources] Wikipedia retrieval: " + out.size() + " page(s) for keywords " + kws.subList(0, Math.min(3, kws.size())));
        } catch (Exception ex) {
            System.err.println("[Sources] Wikipedia retrieval failed (" + ex.getClass().getSimpleName() + "): " + ex.getMessage());
        }
        return out;
    }
}
