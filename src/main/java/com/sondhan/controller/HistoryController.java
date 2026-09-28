package com.sondhan.controller;

import com.sondhan.Main;
import com.sondhan.model.FactCheckResult;
import com.sondhan.model.SearchHistory;
import com.sondhan.service.DatabaseService;
import com.sondhan.service.FactCheckerService;
import com.sondhan.service.SessionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * ──────────────────────────────────────────────────────────────────────────────
 * HistoryController – SQLite Search Archive Management & Analytics
 * ──────────────────────────────────────────────────────────────────────────────
 * Course Project Demonstrations:
 *   • Topic 1: TableView with custom cell factories, live filtering, detail panels.
 *   • Topic 2: Multithreading: Background SQLite loading & deletion via Tasks.
 *   • Topic 3: SQLite SELECT and DELETE with foreign keys and parameterized queries.
 *   • Topic 4: JSON Parsing: Deserializing `sources_json` into clickable links.
 * ──────────────────────────────────────────────────────────────────────────────
 */
public class HistoryController {

    @FXML private Label userNameLabel;

    // ── Analytics Stats Labels ────────────────────────────────────────────────
    @FXML private Label totalCountLabel;
    @FXML private Label trueCountLabel;
    @FXML private Label falseCountLabel;
    @FXML private Label avgConfLabel;

    // ── Search & Filter Controls ──────────────────────────────────────────────
    @FXML private TextField searchFilterField;
    @FXML private ComboBox<String> verdictFilterCombo;

    // ── Table & Columns ───────────────────────────────────────────────────────
    @FXML private TableView<SearchHistory>            historyTable;
    @FXML private TableColumn<SearchHistory, String>  dateCol, typeCol, claimCol, verdictCol, modelCol;
    @FXML private TableColumn<SearchHistory, Integer> confCol;
    @FXML private ProgressIndicator loadingSpinner;

    // ── Detail Panel Controls ─────────────────────────────────────────────────
    @FXML private VBox  detailPanel;
    @FXML private Label detailAiModelLabel;
    @FXML private Label detailClaimLabel;
    @FXML private Button editClaimButton;
    @FXML private Label detailVerdictLabel;
    @FXML private Label detailConfLabel;
    @FXML private Label detailExplanationLabel;
    @FXML private VBox  detailSourcesBox;
    @FXML private VBox  detailSourceUrlBox;
    @FXML private Hyperlink detailSourceUrlLink;
    @FXML private VBox  detailCorrectionBox;
    @FXML private Label detailCorrectionLabel;

    // ── Analytics Dashboard (Feature 4) ───────────────────────────────────────
    @FXML private VBox  analyticsSection;
    @FXML private VBox  verdictDistributionBox;
    @FXML private HBox  verificationMethodsBox;
    @FXML private VBox  sourceDistributionBox;
    @FXML private VBox  recentActivityBox;
    @FXML private ProgressIndicator analyticsSpinner;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private final ObservableList<SearchHistory> masterData = FXCollections.observableArrayList();
    private FilteredList<SearchHistory> filteredData;

    @FXML public void initialize() {
        userNameLabel.setText(SessionManager.isGuest()
            ? "Guest Mode (Sign in to permanently archive results)"
            : SessionManager.getCurrentUser().getName() + "'s Verified Records");

        // 1. Setup Filter ComboBox
        verdictFilterCombo.setItems(FXCollections.observableArrayList(
            "All Verdicts", "TRUE", "FALSE", "MISLEADING", "MODIFIED / OUT OF CONTEXT", "UNVERIFIED", "RESTRICTED"
        ));
        verdictFilterCombo.getSelectionModel().select(0);

        // 2. Table Column Cell Value Factories
        dateCol.setCellValueFactory(c -> {
            String d = c.getValue().getCreatedAt() != null ? c.getValue().getCreatedAt().format(FMT) : "-";
            return new javafx.beans.property.SimpleStringProperty(d);
        });

        typeCol.setCellValueFactory(c -> {
            String inputType = c.getValue().getInputType();
            String t = "url".equalsIgnoreCase(inputType) ? "🔗 URL"
                     : ("image".equalsIgnoreCase(inputType) ? "🖼️ Image" : "💬 Text");
            return new javafx.beans.property.SimpleStringProperty(t);
        });

        claimCol.setCellValueFactory(new PropertyValueFactory<>("claim"));
        verdictCol.setCellValueFactory(new PropertyValueFactory<>("verdict"));
        confCol.setCellValueFactory(new PropertyValueFactory<>("confidence"));

        modelCol.setCellValueFactory(c -> {
            String m = c.getValue().getAiModel();
            return new javafx.beans.property.SimpleStringProperty(m != null && !m.isBlank() ? m : "Sondhan AI");
        });

        // 3. Custom Cell Renderers (Colored Verdict Badges)
        verdictCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label badge = new Label(item);
                badge.setStyle(switch (item) {
                    case "TRUE"       -> "-fx-background-color:#065f46;-fx-text-fill:#34d399;-fx-font-weight:bold;-fx-padding:3 8;-fx-background-radius:10;";
                    case "FALSE"      -> "-fx-background-color:#991b1b;-fx-text-fill:#f87171;-fx-font-weight:bold;-fx-padding:3 8;-fx-background-radius:10;";
                    case "MISLEADING" -> "-fx-background-color:#92400e;-fx-text-fill:#fbbf24;-fx-font-weight:bold;-fx-padding:3 8;-fx-background-radius:10;";
                    case "MODIFIED / OUT OF CONTEXT" -> "-fx-background-color:#c2410c;-fx-text-fill:#fed7aa;-fx-font-weight:bold;-fx-padding:3 8;-fx-background-radius:10;";
                    case "RESTRICTED" -> "-fx-background-color:#5b21b6;-fx-text-fill:#c4b5fd;-fx-font-weight:bold;-fx-padding:3 8;-fx-background-radius:10;";
                    default           -> "-fx-background-color:#334155;-fx-text-fill:#cbd5e1;-fx-font-weight:bold;-fx-padding:3 8;-fx-background-radius:10;";
                });
                setGraphic(badge);
                setText(null);
            }
        });

        confCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer conf, boolean empty) {
                super.updateItem(conf, empty);
                if (empty || conf == null) {
                    setText(null);
                    return;
                }
                setText(conf + "%");
                setStyle("-fx-text-fill:#38bdf8;-fx-font-weight:600;");
            }
        });

        // 4. Filter Wrapping
        filteredData = new FilteredList<>(masterData, p -> true);
        historyTable.setItems(filteredData);

        // 5. Filter Listeners
        searchFilterField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        verdictFilterCombo.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        // 6. Selection Listener for Detail Panel
        historyTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) showDetail(newSel);
        });

        // 7. Load Data Asynchronously (Topic 2: Multithreading)
        // Feature 4: Analytics are loaded AFTER history data arrives (see loadHistoryData callback)
        loadHistoryData();
    }

    private void applyFilters() {
        String query = searchFilterField.getText() == null ? "" : searchFilterField.getText().trim().toLowerCase();
        String verdictFilter = verdictFilterCombo.getValue();

        filteredData.setPredicate(item -> {
            // Text search match
            boolean matchesSearch = query.isEmpty() ||
                (item.getClaim() != null && item.getClaim().toLowerCase().contains(query)) ||
                (item.getExplanation() != null && item.getExplanation().toLowerCase().contains(query));

            // Verdict match
            boolean matchesVerdict = verdictFilter == null ||
                "All Verdicts".equals(verdictFilter) ||
                (item.getVerdict() != null && item.getVerdict().equalsIgnoreCase(verdictFilter));

            return matchesSearch && matchesVerdict;
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Background SQLite Data Loading (Topic 2 & 3)
    // ═════════════════════════════════════════════════════════════════════════

    private void loadHistoryData() {
        loadingSpinner.setVisible(true);

        if (SessionManager.isGuest()) {
            loadingSpinner.setVisible(false);
            updateStats(List.of());
            return;
        }

        int userId = SessionManager.getCurrentUser().getId();

        Task<List<SearchHistory>> loadTask = new Task<>() {
            @Override protected List<SearchHistory> call() throws Exception {
                // Topic 3: SQLite SELECT query via DatabaseService
                return DatabaseService.getInstance().getSearchHistory(userId);
            }
        };

        loadTask.setOnSucceeded(e -> Platform.runLater(() -> {
            loadingSpinner.setVisible(false);
            List<SearchHistory> results = loadTask.getValue();
            masterData.setAll(results);
            updateStats(results);
            applyFilters();
            // Feature 4: Now that masterData is populated, load analytics
            loadAnalytics();
        }));

        loadTask.setOnFailed(e -> Platform.runLater(() -> {
            loadingSpinner.setVisible(false);
            System.err.println("[History Error] " + loadTask.getException().getMessage());
        }));

        new Thread(loadTask, "sondhan-history-load").start();
    }

    private void updateStats(List<SearchHistory> list) {
        int total = list.size();
        int trueCount = 0;
        int falseCount = 0;
        int totalConf = 0;

        for (SearchHistory h : list) {
            String v = h.getVerdict() != null ? h.getVerdict().toUpperCase() : "";
            if ("TRUE".equals(v)) trueCount++;
            else if ("FALSE".equals(v)) falseCount++;
            totalConf += h.getConfidence();
        }

        int avgConf = total > 0 ? (totalConf / total) : 0;

        totalCountLabel.setText(String.valueOf(total));
        trueCountLabel.setText(String.valueOf(trueCount));
        falseCountLabel.setText(String.valueOf(falseCount));
        avgConfLabel.setText(avgConf + "%");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Feature 4: Verification Dashboard Analytics
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Loads all analytics data on a background thread and updates the dashboard UI.
     * Runs aggregate SQL queries + JSON parsing without blocking the UI thread.
     */
    private void loadAnalytics() {
        if (SessionManager.isGuest()) {
            analyticsSection.setVisible(false);
            analyticsSection.setManaged(false);
            return;
        }

        analyticsSpinner.setVisible(true);
        int userId = SessionManager.getCurrentUser().getId();

        Task<Void> analyticsTask = new Task<>() {
            @Override protected Void call() throws Exception {
                // 1. Verdict distribution (SQL aggregate)
                List<String[]> verdictDist = DatabaseService.getInstance().getVerdictDistribution(userId);

                // 2. Verification methods (SQL aggregate)
                List<String[]> methodDist = DatabaseService.getInstance().getMethodDistribution(userId);

                // 3. Source distribution (JSON parse per row – background thread)
                List<String[]> sourceDist = DatabaseService.getInstance().getSourceDistribution(userId);

                // 4. Recent activity (last 5 from already-loaded history)
                List<SearchHistory> recent = masterData.size() > 5
                    ? masterData.subList(0, 5) : masterData;

                Platform.runLater(() -> {
                    buildVerdictDistributionChart(verdictDist);
                    buildVerificationMethodsDisplay(methodDist);
                    buildSourceDistributionDisplay(sourceDist);
                    buildRecentActivityList(recent);
                    analyticsSpinner.setVisible(false);
                });
                return null;
            }
        };

        analyticsTask.setOnFailed(e -> Platform.runLater(() -> {
            analyticsSpinner.setVisible(false);
            System.err.println("[Analytics Error] " + analyticsTask.getException().getMessage());
        }));

        FactCheckerService.getExecutor().submit(analyticsTask);
    }

    /**
     * Builds the VERDICT DISTRIBUTION horizontal bar chart.
     */
    private void buildVerdictDistributionChart(List<String[]> verdictDist) {
        verdictDistributionBox.getChildren().clear();
        if (verdictDist.isEmpty()) {
            Label lbl = new Label("No data yet");
            lbl.getStyleClass().add("ev-count");
            lbl.setStyle("-fx-font-size: 11px;");
            verdictDistributionBox.getChildren().add(lbl);
            return;
        }

        int maxCount = verdictDist.stream().mapToInt(a -> Integer.parseInt(a[1])).max().orElse(1);

        for (String[] entry : verdictDist) {
            String verdict = entry[0];
            int count = Integer.parseInt(entry[1]);

            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);

            Label nameLabel = new Label(verdict);
            nameLabel.getStyleClass().add("ev-label");
            nameLabel.setStyle("-fx-font-size: 11px; -fx-min-width: 120px;");
            nameLabel.setWrapText(true);

            // Bar track
            HBox trackBg = new HBox();
            trackBg.setStyle("-fx-background-color: #1E293B; -fx-background-radius: 4; -fx-min-height: 12;");
            HBox.setHgrow(trackBg, Priority.ALWAYS);

            double pct = (double) count / maxCount;
            HBox fill = new HBox();
            fill.setStyle("-fx-background-color: " + getVerdictBarColor(verdict) + "; -fx-background-radius: 4; -fx-min-height: 12;");
            fill.setPrefWidth(200 * pct);
            trackBg.getChildren().add(fill);

            Label countLabel = new Label(String.valueOf(count));
            countLabel.getStyleClass().add("ev-count");
            countLabel.setStyle("-fx-font-size: 11px; -fx-min-width: 25px;");

            row.getChildren().addAll(nameLabel, trackBg, countLabel);
            verdictDistributionBox.getChildren().add(row);
        }
    }

    /**
     * Builds the VERIFICATION METHODS display (Text/Image/URL counts).
     */
    private void buildVerificationMethodsDisplay(List<String[]> methodDist) {
        verificationMethodsBox.getChildren().clear();
        if (methodDist.isEmpty()) {
            Label lbl = new Label("No data yet");
            lbl.getStyleClass().add("ev-count");
            lbl.setStyle("-fx-font-size: 11px;");
            verificationMethodsBox.getChildren().add(lbl);
            return;
        }

        for (String[] entry : methodDist) {
            String method = entry[0];
            int count = Integer.parseInt(entry[1]);

            String icon = switch (method != null ? method.toLowerCase() : "") {
                case "image" -> "🖼️";
                case "url"   -> "🔗";
                default      -> "💬";
            };
            String label = switch (method != null ? method.toLowerCase() : "") {
                case "image" -> "Image";
                case "url"   -> "URL";
                default      -> "Text";
            };

            VBox methodBox = new VBox(4);
            methodBox.setAlignment(Pos.CENTER);
            methodBox.setStyle("-fx-background-color: #0f1c32; -fx-background-radius: 8; -fx-padding: 10 14; -fx-min-width: 80;");

            Label iconLabel = new Label(icon);
            iconLabel.setStyle("-fx-font-size: 18px;");
            Label countLabel = new Label(String.valueOf(count));
            countLabel.getStyleClass().add("src-url");
            countLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: 800;");
            Label nameLabel = new Label(label);
            nameLabel.getStyleClass().add("text-muted");
            nameLabel.setStyle("-fx-font-size: 10px;");

            methodBox.getChildren().addAll(iconLabel, countLabel, nameLabel);
            verificationMethodsBox.getChildren().add(methodBox);
        }
    }

    /**
     * Builds the SOURCE DISTRIBUTION display (Newspaper/Book/Journal/Government counts).
     */
    private void buildSourceDistributionDisplay(List<String[]> sourceDist) {
        sourceDistributionBox.getChildren().clear();
        if (sourceDist.isEmpty()) {
            Label lbl = new Label("No data yet");
            lbl.getStyleClass().add("ev-count");
            lbl.setStyle("-fx-font-size: 11px;");
            sourceDistributionBox.getChildren().add(lbl);
            return;
        }

        for (String[] entry : sourceDist) {
            String type = entry[0];
            int count = Integer.parseInt(entry[1]);

            String icon = switch (type != null ? type.toLowerCase() : "") {
                case "newspaper"  -> "📰";
                case "book"       -> "📚";
                case "journal"    -> "🔬";
                case "government" -> "🏛️";
                default           -> "🌐";
            };

            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);

            Label iconLabel = new Label(icon);
            iconLabel.setStyle("-fx-font-size: 14px;");

            Label nameLabel = new Label(capitalize(type));
            nameLabel.getStyleClass().add("ev-label");
            nameLabel.setStyle("-fx-font-size: 11px; -fx-min-width: 100px;");

            Label countLabel = new Label(String.valueOf(count));
            countLabel.getStyleClass().add("corr-inline");
            countLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 700;");

            row.getChildren().addAll(iconLabel, nameLabel, countLabel);
            sourceDistributionBox.getChildren().add(row);
        }
    }

    /**
     * Builds the RECENT ACTIVITY list (last 5 searches with relative time).
     */
    private void buildRecentActivityList(List<SearchHistory> recent) {
        recentActivityBox.getChildren().clear();
        if (recent.isEmpty()) {
            Label lbl = new Label("No recent activity");
            lbl.getStyleClass().add("ev-count");
            lbl.setStyle("-fx-font-size: 11px;");
            recentActivityBox.getChildren().add(lbl);
            return;
        }

        for (SearchHistory h : recent) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-cursor: hand; -fx-padding: 4 0;");
            row.setUserData(h.getId()); // Mark as clickable

            // Verdict icon
            String v = h.getVerdict() != null ? h.getVerdict().toUpperCase() : "";
            Label iconLabel = new Label(switch (v) {
                case "TRUE"                   -> "✅";
                case "FALSE"                  -> "✕";
                case "MISLEADING"             -> "⚠";
                case "MODIFIED / OUT OF CONTEXT" -> "⚡";
                case "RESTRICTED"             -> "⛔";
                default                        -> "?";
            });
            iconLabel.setStyle("-fx-font-size: 13px;");

            // Claim snippet
            String claimSnippet = h.getClaim() != null ? h.getClaim() : "";
            if (claimSnippet.length() > 50) claimSnippet = claimSnippet.substring(0, 50) + "…";
            Label claimLabel = new Label(claimSnippet);
            claimLabel.getStyleClass().add("ev-label");
            claimLabel.setStyle("-fx-font-size: 12px;");
            HBox.setHgrow(claimLabel, Priority.ALWAYS);

            // Relative time
            Label timeLabel = new Label(getRelativeTime(h.getCreatedAt()));
            timeLabel.getStyleClass().add("ev-count");
            timeLabel.setStyle("-fx-font-size: 11px;");

            row.getChildren().addAll(iconLabel, claimLabel, timeLabel);

            // Click to open detail
            row.setOnMouseClicked(ev -> {
                ev.consume();
                // Select in table and show detail
                historyTable.getSelectionModel().select(h);
                showDetail(h);
            });

            recentActivityBox.getChildren().add(row);
        }
    }

    private String getVerdictBarColor(String verdict) {
        if (verdict == null) return "#475569";
        return switch (verdict.toUpperCase()) {
            case "TRUE"                   -> "#10B981";
            case "FALSE"                  -> "#EF4444";
            case "MISLEADING"             -> "#F59E0B";
            case "MODIFIED / OUT OF CONTEXT" -> "#EA580C";
            case "RESTRICTED"             -> "#8B5CF6";
            default                        -> "#475569";
        };
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String getRelativeTime(LocalDateTime time) {
        if (time == null) return "";
        LocalDateTime now = LocalDateTime.now();
        long minutes = ChronoUnit.MINUTES.between(time, now);
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + " min ago";
        long hours = ChronoUnit.HOURS.between(time, now);
        if (hours < 24) return hours + " hr ago";
        long days = ChronoUnit.DAYS.between(time, now);
        if (days < 7) return days + " day" + (days > 1 ? "s" : "") + " ago";
        return time.format(DateTimeFormatter.ofPattern("dd MMM"));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Detail Panel & JSON Source Deserialization (Topic 4)
    // ═════════════════════════════════════════════════════════════════════════

    private void showDetail(SearchHistory h) {
        detailAiModelLabel.setText(h.getAiModel() != null ? h.getAiModel() : "Sondhan Engine");
        detailClaimLabel.setText(h.getClaim() != null ? h.getClaim() : "-");

        String verdict = h.getVerdict() != null ? h.getVerdict().toUpperCase() : "UNVERIFIED";
        detailVerdictLabel.setText(verdict);
        detailVerdictLabel.getStyleClass().removeAll("verdict-true", "verdict-false", "verdict-misleading", "verdict-unverified", "verdict-modified", "verdict-restricted");
        detailVerdictLabel.getStyleClass().add(switch (verdict) {
            case "TRUE"       -> "verdict-true";
            case "FALSE"      -> "verdict-false";
            case "MISLEADING" -> "verdict-misleading";
            case "MODIFIED / OUT OF CONTEXT" -> "verdict-modified";
            case "RESTRICTED" -> "verdict-restricted";
            default           -> "verdict-unverified";
        });

        detailConfLabel.setText(h.getConfidence() + "% Confidence");

        // Display Source URL if this search was a URL check
        if (h.getSourceUrl() != null && !h.getSourceUrl().isBlank()) {
            detailSourceUrlLink.setText(h.getSourceUrl());
            detailSourceUrlLink.setOnAction(ev -> openUrl(h.getSourceUrl()));
            detailSourceUrlBox.setVisible(true);
            detailSourceUrlBox.setManaged(true);
        } else {
            detailSourceUrlBox.setVisible(false);
            detailSourceUrlBox.setManaged(false);
        }

        // Display authoritative correction if available
        if (h.getCorrection() != null && !h.getCorrection().isBlank()) {
            detailCorrectionLabel.setText(h.getCorrection());
            detailCorrectionBox.setVisible(true);
            detailCorrectionBox.setManaged(true);
        } else {
            detailCorrectionBox.setVisible(false);
            detailCorrectionBox.setManaged(false);
        }

        detailExplanationLabel.setText(h.getExplanation() != null ? h.getExplanation() : "No explanation archived.");

        // Topic 4: Parse JSON Array of sources stored in SQLite searches table using Jackson
        detailSourcesBox.getChildren().clear();
        String jsonStr = h.getSourcesJson();
        if (jsonStr != null && !jsonStr.isBlank()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode arr = mapper.readTree(jsonStr);
                if (arr.isArray()) {
                    for (JsonNode obj : arr) {
                        String title = obj.path("title").asText("Source");
                        String url   = obj.path("url").asText("#");
                        String type  = obj.path("type").asText("website");

                        HBox card = new HBox(8);
                        card.setAlignment(Pos.CENTER_LEFT);
                        card.getStyleClass().add("source-card");

                        Label badge = new Label(formatSourceType(type));
                        badge.getStyleClass().add(getSourceBadgeClass(type));

                        Hyperlink link = new Hyperlink(title + " ↗");
                        link.getStyleClass().add("source-link");
                        link.setOnAction(ev -> openUrl(url));

                        card.getChildren().addAll(badge, link);
                        detailSourcesBox.getChildren().add(card);
                    }
                }
            } catch (Exception ex) {
                Label raw = new Label("Sources: " + jsonStr);
                raw.getStyleClass().add("text-muted");
                detailSourcesBox.getChildren().add(raw);
            }
        } else {
            Label noSrc = new Label("Standard academic and literature references.");
            noSrc.getStyleClass().add("text-muted");
            detailSourcesBox.getChildren().add(noSrc);
        }

        // Article sub-claims (Feature 3): read lazily off the FX thread so the
        // article_claims table is actually displayed, not just written.
        if ("url".equalsIgnoreCase(h.getInputType())) {
            loadArticleClaimsIntoDetail(h);
        }

        detailPanel.setVisible(true);
        detailPanel.setManaged(true);
    }

    /** Background load of per-claim rows for URL/article records into the detail panel. */
    private void loadArticleClaimsIntoDetail(SearchHistory h) {
        final int recordId = h.getId();
        Task<List<String[]>> claimsTask = new Task<>() {
            @Override protected List<String[]> call() throws Exception {
                return DatabaseService.getInstance().getArticleClaims(recordId);
            }
        };
        claimsTask.setOnSucceeded(e -> Platform.runLater(() -> {
            SearchHistory sel = historyTable.getSelectionModel().getSelectedItem();
            if (sel == null || sel.getId() != recordId) return; // selection moved on
            List<String[]> rows = claimsTask.getValue();
            if (rows == null || rows.isEmpty()) return;
            Label header = new Label("ARTICLE CLAIMS (" + rows.size() + ")");
            header.getStyleClass().add("section-label");
            detailSourcesBox.getChildren().add(header);
            int i = 1;
            for (String[] row : rows) {
                Label line = new Label(i++ + ". " + row[0] + "  [" + row[1] + ", " + row[2] + "%]");
                line.setWrapText(true);
                line.getStyleClass().add("text-muted");
                detailSourcesBox.getChildren().add(line);
            }
        }));
        claimsTask.setOnFailed(e -> Platform.runLater(() ->
            System.err.println("[History] article-claims load failed: " + claimsTask.getException().getMessage())));
        new Thread(claimsTask, "sondhan-claims-load").start();
    }

    private String formatSourceType(String t) {
        return switch (t.toLowerCase()) {
            case "newspaper" -> "📰 NEWSPAPER";
            case "book"      -> "📚 BOOK";
            case "journal"   -> "🔬 JOURNAL";
            case "government"-> "🏛️ GOVT";
            default          -> "🌐 WEB";
        };
    }

    private String getSourceBadgeClass(String t) {
        return switch (t.toLowerCase()) {
            case "newspaper" -> "badge-source-newspaper";
            case "book"      -> "badge-source-book";
            case "journal"   -> "badge-source-journal";
            case "government"-> "badge-source-government";
            default          -> "badge-source-newspaper";
        };
    }

    private void openUrl(String url) {
        if (url == null || url.trim().isEmpty() || "#".equals(url.trim())) return;
        Main.openBrowser(url);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Actions: Delete & Clear History (Topic 3: SQLite DML)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Update path for history records: edit the archived claim text via dialog,
     * persist with UPDATE on a background thread, refresh the table + detail.
     */
    @FXML private void handleEditClaim() {
        SearchHistory sel = historyTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("No Selection");
            alert.setHeaderText(null);
            alert.setContentText("Please select a record from the table to edit.");
            alert.showAndWait();
            return;
        }
        TextInputDialog dialog = new TextInputDialog(sel.getClaim() != null ? sel.getClaim() : "");
        dialog.setTitle("Edit Claim");
        dialog.setHeaderText("Update the archived claim text");
        dialog.setContentText("Claim:");
        dialog.showAndWait().ifPresent(next -> {
            String trimmed = next == null ? "" : next.trim();
            if (trimmed.isEmpty()) {
                Alert warn = new Alert(Alert.AlertType.WARNING);
                warn.setTitle("Invalid Claim");
                warn.setHeaderText(null);
                warn.setContentText("Claim text cannot be empty.");
                warn.showAndWait();
                return;
            }
            if (trimmed.equals(sel.getClaim())) return;
            Task<Integer> updateTask = new Task<>() {
                @Override protected Integer call() throws Exception {
                    return DatabaseService.getInstance().updateSearchClaim(sel.getId(), trimmed);
                }
            };
            updateTask.setOnSucceeded(e -> Platform.runLater(() -> {
                if (updateTask.getValue() != null && updateTask.getValue() > 0) {
                    sel.setClaim(trimmed);
                    historyTable.refresh();
                    detailClaimLabel.setText(trimmed);
                    applyFilters();
                } else {
                    Alert warn = new Alert(Alert.AlertType.WARNING);
                    warn.setTitle("Update Failed");
                    warn.setHeaderText(null);
                    warn.setContentText("That record no longer exists in the archive.");
                    warn.showAndWait();
                }
            }));
            updateTask.setOnFailed(e -> Platform.runLater(() -> {
                Alert err = new Alert(Alert.AlertType.ERROR);
                err.setTitle("Update Failed");
                err.setHeaderText(null);
                err.setContentText("Could not update claim: " + updateTask.getException().getMessage());
                err.showAndWait();
            }));
            new Thread(updateTask, "sondhan-update-thread").start();
        });
    }

    @FXML private void handleDeleteSelected() {        SearchHistory sel = historyTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("No Selection");
            alert.setHeaderText(null);
            alert.setContentText("Please select a record from the table to delete.");
            alert.showAndWait();
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Deletion");
        confirm.setHeaderText("Delete Fact-Check Record");
        confirm.setContentText("Are you sure you want to delete this record from SQLite?");

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                // Topic 2: Asynchronous delete from SQLite on background thread
                Task<Void> delTask = new Task<>() {
                    @Override protected Void call() throws Exception {
                        DatabaseService.getInstance().deleteSearch(sel.getId());
                        return null;
                    }
                };
                delTask.setOnSucceeded(e -> Platform.runLater(() -> {
                    masterData.remove(sel);
                    updateStats(masterData);
                    detailPanel.setVisible(false);
                    detailPanel.setManaged(false);
                    loadAnalytics();
                }));
                new Thread(delTask, "sondhan-delete-thread").start();
            }
        });
    }

    @FXML private void handleExportHistory() {
        if (masterData.isEmpty()) {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("History Empty");
            a.setHeaderText(null);
            a.setContentText("No fact-check records to export.");
            a.showAndWait();
            return;
        }
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Export Search History");
        fc.setInitialFileName("sondhan_history_" + System.currentTimeMillis() + ".json");
        fc.getExtensionFilters().addAll(
            new javafx.stage.FileChooser.ExtensionFilter("JSON Archive (*.json)", "*.json"),
            new javafx.stage.FileChooser.ExtensionFilter("CSV Spreadsheet (*.csv)", "*.csv")
        );
        java.io.File f = fc.showSaveDialog(Main.getPrimaryStage());
        if (f != null) {
            try {
                if (f.getName().endsWith(".csv")) {
                    StringBuilder sb = new StringBuilder("ID,Date,InputType,Verdict,Confidence,AIModel,Claim\n");
                    for (SearchHistory h : masterData) {
                        sb.append(h.getId()).append(",")
                          .append("\"").append(h.getCreatedAt()).append("\",")
                          .append("\"").append(h.getInputType()).append("\",")
                          .append("\"").append(h.getVerdict()).append("\",")
                          .append(h.getConfidence()).append(",")
                          .append("\"").append(h.getAiModel()).append("\",")
                          .append("\"").append(h.getClaim() != null ? h.getClaim().replace("\"", "\"\"") : "").append("\"\n");
                    }
                    java.nio.file.Files.writeString(f.toPath(), sb.toString());
                } else {
                    ObjectMapper mapper = new ObjectMapper();
                    ArrayNode arr = mapper.createArrayNode();
                    for (SearchHistory h : masterData) {
                        ObjectNode obj = mapper.createObjectNode();
                        obj.put("id", h.getId());
                        obj.put("createdAt", h.getCreatedAt() != null ? h.getCreatedAt().toString() : "");
                        obj.put("inputType", h.getInputType());
                        obj.put("claim", h.getClaim());
                        obj.put("verdict", h.getVerdict());
                        obj.put("confidence", h.getConfidence());
                        obj.put("explanation", h.getExplanation());
                        obj.put("aiModel", h.getAiModel());
                        if (h.getSourcesJson() != null) {
                            try { obj.set("sources", mapper.readTree(h.getSourcesJson())); }
                            catch (Exception ignored) { obj.put("sources", h.getSourcesJson()); }
                        }
                        arr.add(obj);
                    }
                    java.nio.file.Files.writeString(f.toPath(), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arr));
                }
                Alert info = new Alert(Alert.AlertType.INFORMATION);
                info.setTitle("Export Completed");
                info.setHeaderText(null);
                info.setContentText("History exported successfully to:\n" + f.getAbsolutePath());
                info.showAndWait();
            } catch (Exception ex) {
                Alert err = new Alert(Alert.AlertType.ERROR);
                err.setTitle("Export Failed");
                err.setContentText("Failed to export: " + ex.getMessage());
                err.showAndWait();
            }
        }
    }

    @FXML private void handleClearAll() {
        if (masterData.isEmpty()) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Clear All History");
        confirm.setHeaderText("Purge Archive Records");
        confirm.setContentText("This will permanently remove all fact-check records for this user from SQLite.");

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                int uid = SessionManager.isGuest() ? -1 : SessionManager.getCurrentUser().getId();
                Task<Void> clearTask = new Task<>() {
                    @Override protected Void call() throws Exception {
                        if (uid != -1) DatabaseService.getInstance().clearHistory(uid);
                        return null;
                    }
                };
                clearTask.setOnSucceeded(e -> Platform.runLater(() -> {
                    masterData.clear();
                    updateStats(masterData);
                    detailPanel.setVisible(false);
                    detailPanel.setManaged(false);
                    loadAnalytics();
                }));
                new Thread(clearTask, "sondhan-clear-thread").start();
            }
        });
    }

    @FXML private void handleRefresh() {
        loadHistoryData();
        // Analytics are reloaded in loadHistoryData's onSucceeded callback
    }

    @FXML private void handleBack() {
        try {
            Main.navigateTo("home.fxml");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}