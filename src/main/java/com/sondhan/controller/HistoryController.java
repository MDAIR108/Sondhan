package com.sondhan.controller;

import com.sondhan.Main;
import com.sondhan.model.SearchHistory;
import com.sondhan.service.DatabaseService;
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
import javafx.scene.layout.VBox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
    @FXML private Label detailVerdictLabel;
    @FXML private Label detailConfLabel;
    @FXML private Label detailExplanationLabel;
    @FXML private VBox  detailSourcesBox;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private final ObservableList<SearchHistory> masterData = FXCollections.observableArrayList();
    private FilteredList<SearchHistory> filteredData;

    @FXML public void initialize() {
        userNameLabel.setText(SessionManager.isGuest()
            ? "Guest Mode (Sign in to permanently archive results)"
            : SessionManager.getCurrentUser().getName() + "'s Verified Records");

        // 1. Setup Filter ComboBox
        verdictFilterCombo.setItems(FXCollections.observableArrayList(
            "All Verdicts", "TRUE", "FALSE", "MISLEADING", "UNVERIFIED"
        ));
        verdictFilterCombo.getSelectionModel().select(0);

        // 2. Table Column Cell Value Factories
        dateCol.setCellValueFactory(c -> {
            String d = c.getValue().getCreatedAt() != null ? c.getValue().getCreatedAt().format(FMT) : "-";
            return new javafx.beans.property.SimpleStringProperty(d);
        });

        typeCol.setCellValueFactory(c -> {
            String t = "image".equalsIgnoreCase(c.getValue().getInputType()) ? "🖼️ Image" : "💬 Text";
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
    //  Detail Panel & JSON Source Deserialization (Topic 4)
    // ═════════════════════════════════════════════════════════════════════════

    private void showDetail(SearchHistory h) {
        detailAiModelLabel.setText(h.getAiModel() != null ? h.getAiModel() : "Sondhan Engine");
        detailClaimLabel.setText(h.getClaim() != null ? h.getClaim() : "-");

        String verdict = h.getVerdict() != null ? h.getVerdict().toUpperCase() : "UNVERIFIED";
        detailVerdictLabel.setText(verdict);
        detailVerdictLabel.getStyleClass().removeAll("verdict-true", "verdict-false", "verdict-misleading", "verdict-unverified");
        detailVerdictLabel.getStyleClass().add(switch (verdict) {
            case "TRUE"       -> "verdict-true";
            case "FALSE"      -> "verdict-false";
            case "MISLEADING" -> "verdict-misleading";
            default           -> "verdict-unverified";
        });

        detailConfLabel.setText(h.getConfidence() + "% Confidence");
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

        detailPanel.setVisible(true);
        detailPanel.setManaged(true);
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

    @FXML private void handleDeleteSelected() {
        SearchHistory sel = historyTable.getSelectionModel().getSelectedItem();
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
                }));
                new Thread(clearTask, "sondhan-clear-thread").start();
            }
        });
    }

    @FXML private void handleRefresh() {
        loadHistoryData();
    }

    @FXML private void handleBack() {
        try {
            Main.navigateTo("home.fxml");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}