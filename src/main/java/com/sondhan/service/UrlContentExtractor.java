package com.sondhan.service;

import com.sondhan.util.ImageHashUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;

/**
 * ──────────────────────────────────────────────────────────────────────────────
 * UrlContentExtractor (Topic 5: Web & HTML Content Extraction)
 * ──────────────────────────────────────────────────────────────────────────────
 * Fetches webpages via HttpClient, parses HTML using Jsoup, extracts clean
 * article text, headlines, publication dates, and embedded primary media.
 * ──────────────────────────────────────────────────────────────────────────────
 */
public class UrlContentExtractor {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Per-request timeout for page fetches: fail fast and descriptively, never hang. */
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(10);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 SondhanFactChecker/3.0";
    // NOTE on bot ethics: this is a plain unauthenticated GET with a standard
    // fetch — no login simulation, no cookies/session injection, no CAPTCHA or
    // bot-check bypass of any kind. Platforms that gate content behind login
    // are handled via metadata-only graceful degradation (see below).

    /**
     * URL-type detector: classifies the host BEFORE any fetch is attempted so
     * login-walled / bot-protected platforms get the restricted path instead
     * of a doomed full scrape.
     */
    public enum SiteKind {
        FACEBOOK, INSTAGRAM, TWITTER_X, LINKEDIN, YOUTUBE, TIKTOK, GENERIC
    }

    /** Returns the SiteKind for a URL string (never throws — unknown → GENERIC). */
    public static SiteKind classifyUrl(String urlString) {
        String host = hostOf(urlString);
        if (host.endsWith("facebook.com") || host.endsWith("fb.com") || host.endsWith("fb.watch")) return SiteKind.FACEBOOK;
        if (host.endsWith("instagram.com"))  return SiteKind.INSTAGRAM;
        if (host.endsWith("twitter.com")  || host.endsWith("x.com")) return SiteKind.TWITTER_X;
        if (host.endsWith("linkedin.com"))   return SiteKind.LINKEDIN;
        if (host.endsWith("youtube.com")  || host.endsWith("youtu.be")) return SiteKind.YOUTUBE;
        if (host.endsWith("tiktok.com"))     return SiteKind.TIKTOK;
        return SiteKind.GENERIC;
    }

    /** Human-readable platform name ("" for generic sites). */
    public static String platformName(SiteKind kind) {
        return switch (kind) {
            case FACEBOOK  -> "Facebook";
            case INSTAGRAM -> "Instagram";
            case TWITTER_X -> "X (Twitter)";
            case LINKEDIN  -> "LinkedIn";
            case YOUTUBE   -> "YouTube";
            case TIKTOK    -> "TikTok";
            case GENERIC   -> "";
        };
    }

    /** True for platforms known to block scraping or require login. */
    public static boolean isRestricted(SiteKind kind) {
        return kind != SiteKind.GENERIC;
    }

    private static String hostOf(String urlString) {
        if (urlString == null) return "";
        try {
            String u = urlString.trim();
            String low = u.toLowerCase();
            if (!low.startsWith("http://") && !low.startsWith("https://")) u = "https://" + u;
            String host = new URI(u).getHost();
            if (host == null) return "";
            host = host.toLowerCase();
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * DTO containing all extracted webpage artifacts.
     * For restricted platforms only public metadata fields may be populated
     * (title/ogDescription/imageUrl); text stays empty and restricted=true
     * with a human-readable restrictionNotice.
     */
    public static class ExtractedPage {
        public final String url;
        public final String title;
        public final String text;
        public final String publishDate;
        public final String imageUrl;
        public final File downloadedImage;
        public final ImageHashUtil.ImageInfo imageInfo;
        // Restricted-platform fields
        public final boolean restricted;
        public final SiteKind siteKind;
        public final String restrictionNotice; // null when not restricted
        public final String ogDescription;     // public og:description, may be ""

        public ExtractedPage(String url, String title, String text, String publishDate,
                             String imageUrl, File downloadedImage, ImageHashUtil.ImageInfo imageInfo) {
            this(url, title, text, publishDate, imageUrl, downloadedImage, imageInfo,
                 false, SiteKind.GENERIC, null, "");
        }

        public ExtractedPage(String url, String title, String text, String publishDate,
                             String imageUrl, File downloadedImage, ImageHashUtil.ImageInfo imageInfo,
                             boolean restricted, SiteKind siteKind, String restrictionNotice, String ogDescription) {
            this.url             = url;
            this.title           = title;
            this.text            = text;
            this.publishDate     = publishDate;
            this.imageUrl        = imageUrl;
            this.downloadedImage = downloadedImage;
            this.imageInfo       = imageInfo;
            this.restricted      = restricted;
            this.siteKind        = siteKind != null ? siteKind : SiteKind.GENERIC;
            this.restrictionNotice = restrictionNotice;
            this.ogDescription   = ogDescription != null ? ogDescription : "";
        }

        public boolean hasImage() {
            return downloadedImage != null && downloadedImage.exists() && downloadedImage.length() > 0;
        }

        /** True when at least a title or description was publicly readable. */
        public boolean hasPublicMetadata() {
            return (title != null && !title.isBlank()) || !ogDescription.isBlank();
        }
    }

    /**
     * Connects to the URL, validates HTML response, extracts text and primary image.
     * Restricted platforms (login-walled / bot-protected) go through the
     * metadata-only path: public Open Graph / Twitter Card tags only, never a
     * full scrape. Generic pages use the full fetch → parse pipeline.
     * Every failure throws a descriptive, stage-specific message (never silent).
     */
    public static ExtractedPage extract(String urlString) throws Exception {
        if (urlString == null || urlString.isBlank()) {
            throw new IllegalArgumentException("URL cannot be empty.");
        }

        String targetUrl = urlString.trim();
        String targetLow = targetUrl.toLowerCase();
        if (!targetLow.startsWith("http://") && !targetLow.startsWith("https://")) {
            targetUrl = "https://" + targetUrl;
        }

        SiteKind kind = classifyUrl(targetUrl);
        if (isRestricted(kind)) {
            System.err.println("[UrlExtractor] " + hostOf(targetUrl) + " classified as "
                + platformName(kind) + " — restricted platform, metadata-only fetch.");
            return extractMetadataOnly(targetUrl, kind);
        }

        URI uri = URI.create(targetUrl);

        // 1. Fetch HTML (10s budget — fail fast, never hang)
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9,bn;q=0.8")
                .timeout(FETCH_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response;
        long startNs = System.nanoTime();
        try {
            response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.http.HttpTimeoutException te) {
            throw new RuntimeException("Fetching " + uri.getHost() + " timed out after "
                + FETCH_TIMEOUT.getSeconds() + "s. The site may be slow or unreachable — please try again.");
        } catch (java.net.UnknownHostException uhe) {
            throw new RuntimeException("Could not resolve host " + uri.getHost() + ". Check the URL and your connection.");
        } catch (java.io.IOException ioe) {
            throw new RuntimeException("Network error fetching " + uri.getHost() + ": " + ioe.getMessage());
        }
        System.err.println("[UrlExtractor] GET " + uri.getHost() + " → HTTP " + response.statusCode()
            + " in " + ((System.nanoTime() - startNs) / 1_000_000) + "ms");

        int statusCode = response.statusCode();
        if (statusCode >= 400) {
            throw new RuntimeException("HTTP " + statusCode + " error returned by host " + uri.getHost()
                + ". The page may be private, deleted, or blocking automated access.");
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (!contentType.toLowerCase().contains("text/html") && !contentType.toLowerCase().contains("xhtml")) {
            throw new RuntimeException("Target URL returned non-HTML content type (" + contentType + "). Only HTML web pages can be verified.");
        }

        String html = response.body();
        if (html == null || html.isBlank()) {
            throw new RuntimeException("Webpage body was empty.");
        }

        // 2. Parse with Jsoup
        Document doc = Jsoup.parse(html, targetUrl);

        // Remove noise tags
        doc.select("script, style, noscript, nav, header, footer, iframe, form, button, .ads, .comment, .social-share").remove();

        // Title extraction: og:title -> twitter:title -> <title> -> <h1>
        String title = "";
        Element ogTitle = doc.selectFirst("meta[property=og:title], meta[name=twitter:title]");
        if (ogTitle != null && !ogTitle.attr("content").isBlank()) {
            title = ogTitle.attr("content").trim();
        } else if (!doc.title().isBlank()) {
            title = doc.title().trim();
        } else {
            Element h1 = doc.selectFirst("h1");
            if (h1 != null) title = h1.text().trim();
        }

        // Publish Date extraction
        String publishDate = "";
        Element dateMeta = doc.selectFirst("meta[property=article:published_time], meta[name=publication_date], meta[name=date], meta[name=parsely-pub-date], time");
        if (dateMeta != null) {
            if (dateMeta.hasAttr("content") && !dateMeta.attr("content").isBlank()) {
                publishDate = dateMeta.attr("content").trim();
            } else if (dateMeta.hasAttr("datetime") && !dateMeta.attr("datetime").isBlank()) {
                publishDate = dateMeta.attr("datetime").trim();
            } else {
                publishDate = dateMeta.text().trim();
            }
        }

        // Article Body Text extraction
        StringBuilder sb = new StringBuilder();
        Elements mainBlocks = doc.select("article, [itemprop=articleBody], .article-body, .story-body, .post-content, .entry-content, main");
        if (!mainBlocks.isEmpty()) {
            for (Element p : mainBlocks.select("p")) {
                String pt = p.text().trim();
                if (pt.length() > 20) {
                    sb.append(pt).append("\n\n");
                }
            }
        }

        // Fallback to all paragraph tags
        if (sb.length() < 80) {
            for (Element p : doc.select("p")) {
                String pt = p.text().trim();
                if (pt.length() > 20) {
                    sb.append(pt).append("\n\n");
                }
            }
        }

        String extractedText = sb.toString().trim();
        if (extractedText.isEmpty() && !title.isEmpty()) {
            extractedText = title;
        }

        // 3. Primary Image extraction
        String imageUrl = "";
        Element ogImage = doc.selectFirst("meta[property=og:image], meta[name=twitter:image], meta[property=og:image:secure_url]");
        if (ogImage != null && !ogImage.attr("content").isBlank()) {
            imageUrl = ogImage.attr("abs:content").trim();
            if (imageUrl.isEmpty()) imageUrl = ogImage.attr("content").trim();
        }

        if (imageUrl.isEmpty()) {
            // Find largest image in article
            Elements imgs = doc.select("article img, main img, img[src]");
            for (Element img : imgs) {
                String src = img.absUrl("src");
                if (src.isEmpty()) src = img.attr("src");
                if (src.startsWith("http://") || src.startsWith("https://")) {
                    imageUrl = src;
                    break;
                }
            }
        }

        File downloadedImg = null;
        ImageHashUtil.ImageInfo imageInfo = null;

        if (!imageUrl.isEmpty()) {
            try {
                downloadedImg = downloadTempImage(imageUrl);
                if (downloadedImg != null && downloadedImg.exists()) {
                    imageInfo = ImageHashUtil.inspectImage(downloadedImg);
                }
            } catch (Exception ex) {
                System.err.println("[UrlExtractor] Could not download image " + imageUrl + ": " + ex.getMessage());
            }
        }

        if (extractedText.isEmpty() && (downloadedImg == null || downloadedImg.length() == 0)) {
            throw new RuntimeException("Could not extract any readable article text or media from the URL. Please verify the link is public.");
        }

        return new ExtractedPage(targetUrl, title, extractedText, publishDate, imageUrl, downloadedImg, imageInfo);
    }

    /**
     * Metadata-only fetch for restricted platforms: a single plain,
     * unauthenticated GET parsed for public Open Graph / Twitter Card tags
     * (og:title, og:description, og:image, og:url). No login simulation, no
     * cookies, no CAPTCHA/bot-check bypass — if the platform restricts even
     * this, the returned page carries hasPublicMetadata()==false and callers
     * show "No public metadata could be retrieved."
     * Images from restricted CDNs are NEVER downloaded here (see
     * ReverseImageSearchService for the legitimate image-origin path).
     */
    public static ExtractedPage extractMetadataOnly(String targetUrl, SiteKind kind) {
        String platform = platformName(kind);
        String limitedNotice = "This platform restricts automated access. Full verification isn't available for "
            + platform + " links — showing limited public metadata only.";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .timeout(FETCH_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            System.err.println("[UrlExtractor] metadata GET " + platform + " → HTTP " + response.statusCode());
            if (response.statusCode() >= 400) {
                return new ExtractedPage(targetUrl, "", "", "", "", null, null,
                    true, kind, "No public metadata could be retrieved from " + platform
                        + " (HTTP " + response.statusCode() + " — the platform blocks automated access).", "");
            }
            Document doc = Jsoup.parse(response.body() != null ? response.body() : "", targetUrl);
            String title = metaContent(doc, "meta[property=og:title], meta[name=twitter:title]");
            if (title.isBlank()) title = doc.title().trim();
            String desc = metaContent(doc, "meta[property=og:description], meta[name=twitter:description], meta[name=description]");
            String img = metaContent(doc, "meta[property=og:image], meta[name=twitter:image], meta[property=og:image:secure_url]");
            if (!img.isBlank() && !img.startsWith("http://") && !img.startsWith("https://")) img = "";
            if (title.isBlank() && desc.isBlank()) {
                return new ExtractedPage(targetUrl, "", "", "", "", null, null,
                    true, kind, "No public metadata could be retrieved from " + platform
                        + " (no public Open Graph tags found).", "");
            }
            return new ExtractedPage(targetUrl, title, "", "", img, null, null,
                true, kind, limitedNotice, desc);
        } catch (java.net.http.HttpTimeoutException te) {
            return new ExtractedPage(targetUrl, "", "", "", "", null, null,
                true, kind, "No public metadata could be retrieved from " + platform
                    + " (request timed out after " + FETCH_TIMEOUT.getSeconds() + "s).", "");
        } catch (Exception ex) {
            System.err.println("[UrlExtractor] metadata fetch failed for " + platform + ": " + ex.getMessage());
            return new ExtractedPage(targetUrl, "", "", "", "", null, null,
                true, kind, "No public metadata could be retrieved from " + platform + ".", "");
        }
    }

    private static String metaContent(Document doc, String selector) {
        Element el = doc.selectFirst(selector);
        return (el != null && !el.attr("content").isBlank()) ? el.attr("content").trim() : "";
    }

    /**
     * Downloads an image URL to a local temporary file for SHA-256 and perceptual hashing.
     */
    private static File downloadTempImage(String imgUrl) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(imgUrl))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<InputStream> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() == 200) {
                File tmpDir = new File(System.getProperty("java.io.tmpdir"), "sondhan_downloads");
                if (!tmpDir.exists()) tmpDir.mkdirs();

                String ext = ".jpg";
                if (imgUrl.toLowerCase().contains(".png")) ext = ".png";
                else if (imgUrl.toLowerCase().contains(".webp")) ext = ".webp";

                File tmpFile = File.createTempFile("sondhan_url_", ext, tmpDir);
                tmpFile.deleteOnExit();

                try (InputStream in = resp.body(); FileOutputStream out = new FileOutputStream(tmpFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                }
                return tmpFile;
            }
        } catch (Exception e) {
            System.err.println("[UrlExtractor] Image download failed: " + e.getMessage());
        }
        return null;
    }
}
