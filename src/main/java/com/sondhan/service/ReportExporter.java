package com.sondhan.service;

import com.sondhan.model.ArticleAnalysisResult;
import com.sondhan.model.FactCheckResult;

/**
 * Strategy interface for fact-check report exports.
 * Implemented by every export format (Markdown, JSON, ...).
 */
public interface ReportExporter {

    /** Serializes a single-claim result for copy/save. */
    String exportClaim(FactCheckResult result, int verificationId) throws Exception;

    /** Serializes a multi-claim article result for copy/save. */
    String exportArticle(ArticleAnalysisResult article, int verificationId) throws Exception;

    /** File extension used by the save dialog (without dot). */
    String fileExtension();
}
