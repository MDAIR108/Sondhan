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
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 SondhanFactChecker/3.0";

    /**
     * DTO containing all extracted webpage artifacts.
     */
    public static class ExtractedPage {
        public final String url;
        public final String title;
        public final String text;
        public final String publishDate;
        public final String imageUrl;
        public final File downloadedImage;
        public final ImageHashUtil.ImageInfo imageInfo;

        public ExtractedPage(String url, String title, String text, String publishDate,
                             String imageUrl, File downloadedImage, ImageHashUtil.ImageInfo imageInfo) {
            this.url             = url;
            this.title           = title;
            this.text            = text;
            this.publishDate     = publishDate;
            this.imageUrl        = imageUrl;
            this.downloadedImage = downloadedImage;
            this.imageInfo       = imageInfo;
        }

        public boolean hasImage() {
            return downloadedImage != null && downloadedImage.exists() && downloadedImage.length() > 0;
        }
    }

    /**
     * Connects to the URL, validates HTML response, extracts text and primary image.
     */
    public static ExtractedPage extract(String urlString) throws Exception {
        if (urlString == null || urlString.isBlank()) {
            throw new IllegalArgumentException("URL cannot be empty.");
        }

        String targetUrl = urlString.trim();
        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            targetUrl = "https://" + targetUrl;
        }

        URI uri = URI.create(targetUrl);

        // 1. Fetch HTML
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9,bn;q=0.8")
                .timeout(Duration.ofSeconds(25))
                .GET()
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        int statusCode = response.statusCode();
        if (statusCode >= 400) {
            throw new RuntimeException("HTTP " + statusCode + " error returned by host: " + uri.getHost());
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
     * Downloads an image URL to a local temporary file for SHA-256 and perceptual hashing.
     */
    private static File downloadTempImage(String imgUrl) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(imgUrl))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(15))
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
