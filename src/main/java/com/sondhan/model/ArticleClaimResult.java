package com.sondhan.model;

/**
 * A single claim extracted from an article and its FactCheckResult.
 * Used by ArticleAnalysisResult (Feature 3).
 */
public class ArticleClaimResult {

    private String detectedClaim;     // The claim text as detected by AI
    private FactCheckResult result;   // Full fact-check result for this claim

    public ArticleClaimResult(String detectedClaim, FactCheckResult result) {
        this.detectedClaim = detectedClaim;
        this.result        = result;
    }

    public String          getDetectedClaim()        { return detectedClaim; }
    public void            setDetectedClaim(String v){ detectedClaim = v; }
    public FactCheckResult getResult()               { return result; }
    public void            setResult(FactCheckResult v){ result = v; }

    /** Convenience delegate. */
    public String getVerdict()    { return result != null ? result.getVerdict()    : "UNVERIFIED"; }
    public int    getConfidence() { return result != null ? result.getConfidence() : 0; }
}
