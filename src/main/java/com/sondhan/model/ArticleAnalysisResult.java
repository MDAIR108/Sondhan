package com.sondhan.model;

import java.util.List;

/**
 * Result of a full URL/article analysis (Feature 3).
 * Contains the article metadata, per-claim fact-check results,
 * and an aggregated overall assessment.
 */
public class ArticleAnalysisResult {

    private String title;
    private String publisher;
    private String author;
    private String publishDate;
    private String url;

    private List<ArticleClaimResult> claims;

    /** Aggregated overall verdict: "ACCURATE" | "POTENTIALLY MISLEADING" | "CONTAINS FALSE CLAIMS" */
    private String overallVerdict;
    private String overallSummary;

    /** DB row ID of the parent searches record. */
    private int searchId = -1;

    // ── Getters / Setters ─────────────────────────────────────────────────────
    public String getTitle()        { return title; }
    public void   setTitle(String v){ title = v; }

    public String getPublisher()        { return publisher; }
    public void   setPublisher(String v){ publisher = v; }

    public String getAuthor()        { return author; }
    public void   setAuthor(String v){ author = v; }

    public String getPublishDate()        { return publishDate; }
    public void   setPublishDate(String v){ publishDate = v; }

    public String getUrl()        { return url; }
    public void   setUrl(String v){ url = v; }

    public List<ArticleClaimResult> getClaims()        { return claims; }
    public void                     setClaims(List<ArticleClaimResult> v){ claims = v; }

    public String getOverallVerdict()        { return overallVerdict; }
    public void   setOverallVerdict(String v){ overallVerdict = v; }

    public String getOverallSummary()        { return overallSummary; }
    public void   setOverallSummary(String v){ overallSummary = v; }

    public int  getSearchId()     { return searchId; }
    public void setSearchId(int v){ searchId = v; }
}
