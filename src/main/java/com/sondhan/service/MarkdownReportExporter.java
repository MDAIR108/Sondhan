package com.sondhan.service;

import com.sondhan.model.ArticleAnalysisResult;
import com.sondhan.model.FactCheckResult;

/** Markdown (.md) report exporter. */
public class MarkdownReportExporter extends BaseReportExporter {

    @Override
    public String fileExtension() {
        return "md";
    }
}
