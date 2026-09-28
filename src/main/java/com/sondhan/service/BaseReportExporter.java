package com.sondhan.service;

import com.sondhan.model.ArticleAnalysisResult;
import com.sondhan.model.FactCheckResult;

/**
 * Abstract base for report exporters. Shared serialization lives here via
 * ReportGeneratorService; each format supplies only its file extension.
 */
public abstract class BaseReportExporter implements ReportExporter {

    @Override
    public String exportClaim(FactCheckResult result, int verificationId) throws Exception {
        return ReportGeneratorService.generateMarkdownReport(result, verificationId);
    }

    @Override
    public String exportArticle(ArticleAnalysisResult article, int verificationId) throws Exception {
        return ReportGeneratorService.generateArticleReport(article, verificationId);
    }

    @Override
    public abstract String fileExtension();

    /** Default save-dialog filename, e.g. FactCheck_1690000000000.md */
    public String initialFileName() {
        return "FactCheck_" + System.currentTimeMillis() + "." + fileExtension();
    }
}
