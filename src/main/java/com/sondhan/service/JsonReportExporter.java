package com.sondhan.service;

import com.sondhan.model.ArticleAnalysisResult;
import com.sondhan.model.FactCheckResult;

/** Structured JSON (.json) report exporter. */
public class JsonReportExporter extends BaseReportExporter {

    @Override
    public String exportClaim(FactCheckResult result, int verificationId) throws Exception {
        return ReportGeneratorService.exportJson(result);
    }

    @Override
    public String exportArticle(ArticleAnalysisResult article, int verificationId) throws Exception {
        return ReportGeneratorService.exportArticleJson(article);
    }

    @Override
    public String fileExtension() {
        return "json";
    }
}
