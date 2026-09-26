package com.sondhan.controller;

import com.sondhan.Main;
import com.sondhan.model.FactCheckResult;
import com.sondhan.service.*;
import com.sondhan.util.ImageHashUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.util.List;

/**
 * ──────────────────────────────────────────────────────────────────────────────
 * HomeController – Master Coordinator for Sondhan Fact-Checking
 * ──────────────────────────────────────────────────────────────────────────────
 * Course Project Demonstrations:
 *   • Topic 1: JavaFX GUI layout, SceneBuilder FXML, dynamic CSS styling.
 *   • Topic 2: Java Multithreading: ExecutorService daemon pool, JavaFX Tasks,
 *              Platform.runLater() for thread safety, non-blocking UI.
 *   • Topic 3: SQLite Relational Database: Asynchronous INSERT of searches.
 *   • Topic 4: JSON Processing: Constructing API payloads and parsing results.
 * ──────────────────────────────────────────────────────────────────────────────
 */
public class HomeController {

    // ── Header Controls ───────────────────────────────────────────────────────
    @FXML private Label     userNameLabel;
    @FXML private Label     apiKeyStatusLabel;
    @FXML private ComboBox<String> modelSelector;

    // ── Input Controls ────────────────────────────────────────────────────────
    @FXML private TabPane   inputTabPane;
    @FXML private Tab       imageTab, textTab, urlTab;
    @FXML private StackPane dropZone;
    @FXML private VBox      dropPromptBox;
    @FXML private Label     dropLabel;
    @FXML private ImageView previewImage;
    @FXML private Button    clearImageBtn, checkBtn, checkBtnImage, checkBtnUrl;
    @FXML private TextArea  textInputArea;
    @FXML private TextField urlInputField;

    // ── Image Inspection System ───────────────────────────────────────────────
    @FXML private VBox      imageInfoBox;
    @FXML private Label     imageFileNameLabel, imageFormatLabel, imageDimensionsLabel, imageSizeLabel, imageHashLabel;

    // ── Loading & Multithreading Indicators ────────────────────────────────────
    @FXML private VBox      loadingBox;
    @FXML private ProgressIndicator loadingSpinner;
    @FXML private ProgressBar taskProgressBar;
    @FXML private Label     loadingLabel;

    // ── Results Controls ──────────────────────────────────────────────────────
    @FXML private VBox      emptyState, resultPanel;
    @FXML private Label     preloadedBadge, modelUsedBadge;
    @FXML private Label     sourceUrlBadge, sourceUrlLabel;
    @FXML private Label     claimLabel, verdictLabel, confidencePctLabel;
    @FXML private ProgressBar confidenceBar;
    @FXML private Label     explanationLabel;
    @FXML private VBox      sourcesBox;
    @FXML private VBox      summaryBox;
    @FXML private Label     summaryLabel, summaryStatusLabel;

    // ── Forensics & Correction Panels ─────────────────────────────────────────
    @FXML private VBox      imageComparisonPanel;
    @FXML private Label     comparisonMatchLabel;
    @FXML private ImageView submittedImageView, originalImageView;
    @FXML private Label     submittedImageMetaLabel, originalImageMetaLabel;
    @FXML private VBox      correctionCard;
    @FXML private Label     correctionLabel;

    private File selectedImageFile;
    private FactCheckResult currentResult;

    // ── Initialization ────────────────────────────────────────────────────────

    @FXML public void initialize() {
        // 1. Session Setup
        userNameLabel.setText(SessionManager.isGuest() ? "Guest Mode" : SessionManager.getCurrentUser().getName());
        updateApiKeyStatus();

        // 2. Model Selector Setup
        modelSelector.setItems(FXCollections.observableArrayList(
            "Auto / Smart Engine",
            "Google Gemini 1.5 Flash (Free API)",
            "ChatGPT (GPT-4o)",
            "Claude 3.5 Sonnet"
        ));
        modelSelector.getSelectionModel().select(0);

        // 3. Initial Visibility States
        resultPanel.setVisible(false);  resultPanel.setManaged(false);
        emptyState.setVisible(true);    emptyState.setManaged(true);
        loadingBox.setVisible(false);   loadingBox.setManaged(false);
        clearImageBtn.setVisible(false);
        imageInfoBox.setVisible(false); imageInfoBox.setManaged(false);

        // 4. Drag & Drop Setup
        setupDragAndDrop();

        // 5. Topic 2: Initialize Preloaded Knowledge Database on Background Thread
        Task<Void> preloadTask = new Task<>() {
            @Override protected Void call() {
                String preloadedPath = System.getProperty("user.dir") + File.separator + "preloaded";
                PreloadedDatabase.getInstance().initialize(preloadedPath);
                return null;
            }
        };
        FactCheckerService.getExecutor().submit(preloadTask);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Image Inspection & Drag-and-Drop System
    // ═════════════════════════════════════════════════════════════════════════

    private void setupDragAndDrop() {
        dropZone.setOnDragOver(ev -> {
            if (ev.getDragboard().hasFiles()) ev.acceptTransferModes(TransferMode.COPY);
            ev.consume();
        });
        dropZone.setOnDragDropped(ev -> {
            List<File> files = ev.getDragboard().getFiles();
            if (!files.isEmpty()) setImage(files.get(0));
            ev.setDropCompleted(true);
            ev.consume();
        });
        dropZone.setOnDragEntered(ev -> dropZone.setStyle("-fx-border-color:#8b5cf6;-fx-background-color:rgba(139,92,246,0.1);"));
        dropZone.setOnDragExited(ev  -> dropZone.setStyle(""));
    }

    @FXML private void handleBrowse() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select an Image to Fact-Check");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images (*.png, *.jpg, *.jpeg, *.webp)", "*.png", "*.jpg", "*.jpeg", "*.webp", "*.gif"));
        File f = fc.showOpenDialog(Main.getPrimaryStage());
        if (f != null) setImage(f);
    }

    private void setImage(File f) {
        selectedImageFile = f;
        try (FileInputStream fis = new FileInputStream(f)) {
            previewImage.setImage(new Image(fis));
            previewImage.setVisible(true);
            dropPromptBox.setVisible(false);
        } catch (Exception ex) {
            previewImage.setVisible(false);
            dropPromptBox.setVisible(true);
        }
        clearImageBtn.setVisible(true);

        // Inspect image metadata (size, dimensions, format, SHA-256 hash)
        ImageHashUtil.ImageInfo info = ImageHashUtil.inspectImage(f);
        imageFileNameLabel.setText(info.fileName);
        imageFormatLabel.setText(info.format);
        imageDimensionsLabel.setText(info.width > 0 ? info.width + "×" + info.height + " px" : "Dimensions N/A");
        imageSizeLabel.setText(info.fileSizeFormatted);
        imageHashLabel.setText(info.hash.length() > 24 ? info.hash.substring(0, 24) + "…" : info.hash);

        imageInfoBox.setVisible(true);
        imageInfoBox.setManaged(true);
    }

    @FXML private void handleClearImage() {
        selectedImageFile = null;
        previewImage.setImage(null);
        previewImage.setVisible(false);
        dropPromptBox.setVisible(true);
        clearImageBtn.setVisible(false);
        imageInfoBox.setVisible(false);
        imageInfoBox.setManaged(false);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Quick Preset Handlers (1-Click Test for Grading & Examiners)
    // ═════════════════════════════════════════════════════════════════════════

    @FXML private void handlePresetDhaka() { loadPreloadedImage("claim_dhaka_capital.png"); }
    @FXML private void handlePreset5G()    { loadPreloadedImage("claim_5g_health.png"); }
    @FXML private void handlePresetYunus() { loadPreloadedImage("718953356_1465989142222974_2371143075906418461_n.jpg"); }
    @FXML private void handlePresetMars()  { loadPreloadedImage("claim_mars_water.png"); }
    @FXML private void handlePresetEiffel(){ loadPreloadedImage("claim_eiffel_sold.png"); }

    private void loadPreloadedImage(String fileName) {
        File f = new File(System.getProperty("user.dir") + File.separator + "preloaded" + File.separator + fileName);
        if (f.exists()) {
            inputTabPane.getSelectionModel().select(imageTab);
            setImage(f);
        } else {
            alert("Preset Not Found", "Could not locate preloaded image: " + fileName);
        }
    }

    @FXML private void handleTextPresetDhaka() {
        inputTabPane.getSelectionModel().select(textTab);
        textInputArea.setText("Dhaka has been the official capital of Bangladesh since independence in 1971.");
    }

    @FXML private void handleTextPreset5G() {
        inputTabPane.getSelectionModel().select(textTab);
        textInputArea.setText("5G cell phone towers emit dangerous ionizing radiation that causes cancer and tumors.");
    }

    @FXML private void handleTextPresetEiffel() {
        inputTabPane.getSelectionModel().select(textTab);
        textInputArea.setText("The Eiffel Tower was sold by a con man named Victor Lustig for scrap metal in 1925.");
    }

    @FXML private void handleTextPresetMoon() {
        inputTabPane.getSelectionModel().select(textTab);
        textInputArea.setText("The 1969 Apollo 11 moon landing was a Hollywood film set hoax orchestrated by Stanley Kubrick.");
    }

    @FXML private void handleTextPresetClimate() {
        inputTabPane.getSelectionModel().select(textTab);
        textInputArea.setText("Climate change is a fabricated global hoax invented by scientists to secure research grants.");
    }

    // ── URL Preset Handlers ───────────────────────────────────────────────────

    @FXML private void handleUrlPreset5G() {
        inputTabPane.getSelectionModel().select(urlTab);
        urlInputField.setText("https://www.who.int/news-room/questions-and-answers/item/radiation-5g-mobile-networks-and-health");
    }

    @FXML private void handleUrlPresetEiffel() {
        inputTabPane.getSelectionModel().select(urlTab);
        urlInputField.setText("https://www.smithsonianmag.com/history/the-man-who-sold-the-eiffel-tower-twice-17973580/");
    }

    @FXML private void handleUrlPresetClimate() {
        inputTabPane.getSelectionModel().select(urlTab);
        urlInputField.setText("https://climate.nasa.gov/evidence/");
    }

    @FXML private void handleUrlPresetWHO() {
        inputTabPane.getSelectionModel().select(urlTab);
        urlInputField.setText("https://www.who.int/news-room/q-a-detail/vaccines-and-immunization-what-is-vaccination");
    }

    @FXML private void handleUrlPresetMars() {
        inputTabPane.getSelectionModel().select(urlTab);
        urlInputField.setText("https://www.nasa.gov/press-release/nasa-confirms-evidence-that-liquid-water-flows-on-today-s-mars");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  API Key Settings Dialog (Gemini, ChatGPT, Claude & Free Mode)
    // ═════════════════════════════════════════════════════════════════════════

    @FXML private void handleApiKeySettings() {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("API Engine Settings");
        dialog.setHeaderText("Configure Live API Keys (Google Gemini, OpenAI, Anthropic)\nLeave blank to run with the built-in Knowledge Engine.");

        ButtonType saveBtnType = new ButtonType("Save Settings", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.setPrefWidth(500);

        // Google Gemini Key (Free tier from Google AI Studio)
        Label geminiLabel = new Label("Google Gemini API Key (Free from Google AI Studio):");
        geminiLabel.setStyle("-fx-font-weight:bold;");
        PasswordField geminiField = new PasswordField();
        geminiField.setText(SessionManager.getGeminiKey());
        geminiField.setPromptText("AIzaSy...");

        // Claude Key
        Label claudeLabel = new Label("Anthropic Claude API Key (Optional):");
        claudeLabel.setStyle("-fx-font-weight:bold;");
        PasswordField claudeField = new PasswordField();
        claudeField.setText(SessionManager.getClaudeKey());
        claudeField.setPromptText("sk-ant-api03-...");

        // OpenAI Key
        Label openAiLabel = new Label("OpenAI ChatGPT API Key (Optional):");
        openAiLabel.setStyle("-fx-font-weight:bold;");
        PasswordField openAiField = new PasswordField();
        openAiField.setText(SessionManager.getOpenAiKey());
        openAiField.setPromptText("sk-proj-...");

        Label note = new Label("💡 Free API Tip:\n" +
                               "• You can get a free Gemini API key from https://aistudio.google.com\n" +
                               "• If no keys are entered, Sondhan uses its built-in knowledge archive.");
        note.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:12px;");

        content.getChildren().addAll(geminiLabel, geminiField, openAiLabel, openAiField, claudeLabel, claudeField, note);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(btn -> {
            if (btn == saveBtnType) {
                SessionManager.setGeminiKey(geminiField.getText());
                SessionManager.setClaudeKey(claudeField.getText());
                SessionManager.setOpenAiKey(openAiField.getText());
                updateApiKeyStatus();
                return true;
            }
            return false;
        });

        dialog.showAndWait();
    }

    private void updateApiKeyStatus() {
        int keyCount = (SessionManager.hasGeminiKey() ? 1 : 0)
                     + (SessionManager.hasOpenAiKey() ? 1 : 0)
                     + (SessionManager.hasClaudeKey() ? 1 : 0);

        if (keyCount >= 2) {
            apiKeyStatusLabel.setText("🟢 Multi-Engine Live (" + keyCount + ")");
            apiKeyStatusLabel.setStyle("-fx-background-color:rgba(16,185,129,0.15);-fx-text-fill:#34d399;-fx-border-color:rgba(16,185,129,0.35);");
        } else if (SessionManager.hasGeminiKey()) {
            apiKeyStatusLabel.setText("🟢 Gemini 1.5 Live");
            apiKeyStatusLabel.setStyle("-fx-background-color:rgba(14,165,233,0.18);-fx-text-fill:#38bdf8;-fx-border-color:rgba(14,165,233,0.4);");
        } else if (SessionManager.hasClaudeKey()) {
            apiKeyStatusLabel.setText("🟢 Claude Live");
            apiKeyStatusLabel.setStyle("-fx-background-color:rgba(16,185,129,0.15);-fx-text-fill:#34d399;-fx-border-color:rgba(16,185,129,0.35);");
        } else if (SessionManager.hasOpenAiKey()) {
            apiKeyStatusLabel.setText("🟢 ChatGPT Live");
            apiKeyStatusLabel.setStyle("-fx-background-color:rgba(16,185,129,0.15);-fx-text-fill:#34d399;-fx-border-color:rgba(16,185,129,0.35);");
        } else {
            apiKeyStatusLabel.setText("⚡ Free Engine");
            apiKeyStatusLabel.setStyle("-fx-background-color:rgba(249,115,22,0.15);-fx-text-fill:#fb923c;-fx-border-color:rgba(249,115,22,0.35);");
        }
    }


    // ═════════════════════════════════════════════════════════════════════════
    //  Fact-Checking Execution (Topic 2: Multithreading Architecture)
    // ═════════════════════════════════════════════════════════════════════════

    @FXML private void handleCheck() {
        Tab selected = inputTabPane.getSelectionModel().getSelectedItem();
        boolean isImage = selected == imageTab;
        boolean isUrl   = selected == urlTab;

        if (isImage && selectedImageFile == null) {
            alert("No Image Selected", "Please select or drop an image first, or pick one of the sample presets.");
            return;
        }
        if (isUrl && (urlInputField == null || urlInputField.getText().trim().isEmpty())) {
            alert("No URL Entered", "Please enter a valid website or article URL to verify, or pick a sample preset.");
            return;
        }
        if (!isImage && !isUrl && textInputArea.getText().trim().isEmpty()) {
            alert("No Statement Entered", "Please type or paste a claim statement to fact-check, or pick a sample preset.");
            return;
        }

        setLoading(true);
        hideResult();

        String selectedModel = modelSelector.getSelectionModel().getSelectedItem();
        if (selectedModel == null) selectedModel = "Auto / Smart Engine";

        if (isImage) {
            executeImageTask(selectedModel, selectedImageFile);
        } else if (isUrl) {
            executeUrlTask(selectedModel, urlInputField.getText().trim());
        } else {
            executeTextTask(selectedModel, textInputArea.getText().trim());
        }
    }

    /**
     * Topic 2: URL Verification Task running on ExecutorService daemon pool.
     */
    private void executeUrlTask(String model, String urlStr) {
        Task<FactCheckResult> task = new Task<>() {
            @Override protected FactCheckResult call() throws Exception {
                updateMessage("Connecting to URL and extracting page content...");
                updateProgress(0.20, 1.0);

                updateMessage("Inspecting visual elements & computing perceptual hash...");
                updateProgress(0.50, 1.0);

                FactCheckResult res = FactCheckerService.checkUrlClaim(model, urlStr);

                updateMessage("Synthesizing citations and factual verification report...");
                updateProgress(0.95, 1.0);
                return res;
            }
        };
        wireTask(task, "url", urlStr, model);
    }

    /**
     * Topic 2: Image Verification Task running on ExecutorService daemon pool.
     */
    private void executeImageTask(String model, File imageFile) {
        Task<FactCheckResult> task = new Task<>() {
            @Override protected FactCheckResult call() throws Exception {
                updateMessage("Inspecting image cryptographic hash...");
                updateProgress(0.2, 1.0);

                updateMessage("Searching newspaper archives & databases...");
                updateProgress(0.5, 1.0);

                FactCheckResult res = FactCheckerService.checkImageClaim(model, imageFile);

                updateMessage("Synthesizing citations & cross-referencing...");
                updateProgress(0.9, 1.0);
                return res;
            }
        };
        wireTask(task, "image", imageFile.getName(), model);
    }

    /**
     * Topic 2: Text Statement Verification Task running on ExecutorService daemon pool.
     */
    private void executeTextTask(String model, String claim) {
        Task<FactCheckResult> task = new Task<>() {
            @Override protected FactCheckResult call() throws Exception {
                updateMessage("Analyzing semantic claim structure...");
                updateProgress(0.25, 1.0);

                updateMessage("Cross-examining newspapers, books & journals...");
                updateProgress(0.60, 1.0);

                FactCheckResult res = FactCheckerService.checkTextClaim(model, claim);

                updateMessage("Formatting verified source report...");
                updateProgress(0.95, 1.0);
                return res;
            }
        };
        wireTask(task, "text", claim, model);
    }

    /**
     * Topic 2: Task property binding and thread-safe UI completion callback.
     */
    private void wireTask(Task<FactCheckResult> task, String inputType, String originalInput, String modelName) {
        loadingLabel.textProperty().bind(task.messageProperty());
        taskProgressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            loadingLabel.textProperty().unbind();
            taskProgressBar.progressProperty().unbind();
            setLoading(false);
            FactCheckResult res = task.getValue();
            currentResult = res;
            displayResult(res, inputType, originalInput);
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            loadingLabel.textProperty().unbind();
            taskProgressBar.progressProperty().unbind();
            setLoading(false);
            Throwable err = task.getException();
            alert("Verification Error", err != null ? err.getMessage() : "Unknown verification failure.");
        }));

        FactCheckerService.getExecutor().submit(task);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Result Presentation & Categorized Sources System
    // ═════════════════════════════════════════════════════════════════════════

    private void displayResult(FactCheckResult r, String inputType, String originalInput) {
        claimLabel.setText(r.getClaim());

        // 1. Verdict Badge Styling
        verdictLabel.setText(switch (r.getVerdict()) {
            case "TRUE"       -> "✓ VERIFIED TRUE";
            case "FALSE"      -> "✕ FALSE CLAIM";
            case "MISLEADING" -> "⚠ MISLEADING";
            case "MODIFIED / OUT OF CONTEXT" -> "⚡ MODIFIED / OUT OF CONTEXT";
            default           -> "? UNVERIFIED";
        });
        verdictLabel.getStyleClass().removeAll("verdict-true", "verdict-false", "verdict-misleading", "verdict-unverified", "verdict-modified");
        verdictLabel.getStyleClass().add(switch (r.getVerdict()) {
            case "TRUE"       -> "verdict-true";
            case "FALSE"      -> "verdict-false";
            case "MISLEADING" -> "verdict-misleading";
            case "MODIFIED / OUT OF CONTEXT" -> "verdict-modified";
            default           -> "verdict-unverified";
        });

        // 2. Confidence Level
        confidenceBar.setProgress(r.getConfidence() / 100.0);
        confidencePctLabel.setText(r.getConfidence() + "%");

        // 3. Factual Explanation
        explanationLabel.setText(r.getExplanation());

        // 4. Badges (Preloaded & Model Used)
        preloadedBadge.setVisible(r.isPreloaded());
        preloadedBadge.setManaged(r.isPreloaded());
        modelUsedBadge.setText("Engine: " + (r.getAiModel() != null ? r.getAiModel() : "Sondhan AI"));

        // 5. Source URL Display (if checked via URL tab or has sourceUrl)
        if (r.getSourceUrl() != null && !r.getSourceUrl().isBlank()) {
            if (sourceUrlBadge != null) { sourceUrlBadge.setVisible(true); sourceUrlBadge.setManaged(true); }
            if (sourceUrlLabel != null) {
                sourceUrlLabel.setText("Verified Webpage: " + r.getSourceUrl());
                sourceUrlLabel.setVisible(true); sourceUrlLabel.setManaged(true);
            }
        } else {
            if (sourceUrlBadge != null) { sourceUrlBadge.setVisible(false); sourceUrlBadge.setManaged(false); }
            if (sourceUrlLabel != null) { sourceUrlLabel.setVisible(false); sourceUrlLabel.setManaged(false); }
        }

        // 6. Side-by-Side Image Forensics Comparison (shown if original & submitted images exist)
        boolean showComparison = r.getOriginalImageUrl() != null && !r.getOriginalImageUrl().isBlank()
                && r.getSubmittedImageUrl() != null && !r.getSubmittedImageUrl().isBlank();
        if (showComparison && imageComparisonPanel != null) {
            try {
                submittedImageView.setImage(new Image(r.getSubmittedImageUrl()));
            } catch (Exception ex) {
                submittedImageView.setImage(null);
            }
            try {
                originalImageView.setImage(new Image(r.getOriginalImageUrl()));
            } catch (Exception ex) {
                originalImageView.setImage(null);
            }

            String subMeta = "";
            if (r.getSubmittedFormat() != null) subMeta += "Format: " + r.getSubmittedFormat() + "  ";
            if (r.getSubmittedDimensions() != null) subMeta += "Dimensions: " + r.getSubmittedDimensions() + "  ";
            if (r.getSubmittedImageHash() != null) {
                String h = r.getSubmittedImageHash();
                subMeta += "SHA-256: " + (h.length() > 16 ? h.substring(0, 16) + "…" : h);
            }
            if (submittedImageMetaLabel != null) {
                submittedImageMetaLabel.setText(subMeta.isBlank() ? "Extracted Visual Asset" : subMeta);
            }

            String origMeta = "";
            if (r.getOriginalImageDate() != null) origMeta += "Archived: " + r.getOriginalImageDate() + "  ";
            if (r.getOriginalImageSource() != null) origMeta += "Source: " + r.getOriginalImageSource() + "  ";
            if (r.getOriginalImageHash() != null) {
                String h = r.getOriginalImageHash();
                origMeta += "Hash: " + (h.length() > 16 ? h.substring(0, 16) + "…" : h);
            }
            if (originalImageMetaLabel != null) {
                originalImageMetaLabel.setText(origMeta.isBlank() ? "Verified Archived Reference" : origMeta);
            }

            imageComparisonPanel.setVisible(true);
            imageComparisonPanel.setManaged(true);
        } else if (imageComparisonPanel != null) {
            imageComparisonPanel.setVisible(false);
            imageComparisonPanel.setManaged(false);
        }

        // 7. Correction Card ("What's actually true" for FALSE, MISLEADING, MODIFIED)
        boolean showCorrection = r.getCorrection() != null && !r.getCorrection().isBlank()
                && ("FALSE".equalsIgnoreCase(r.getVerdict())
                    || "MISLEADING".equalsIgnoreCase(r.getVerdict())
                    || "MODIFIED / OUT OF CONTEXT".equalsIgnoreCase(r.getVerdict()));
        if (showCorrection && correctionCard != null) {
            correctionLabel.setText(r.getCorrection());
            correctionCard.setVisible(true);
            correctionCard.setManaged(true);
        } else if (correctionCard != null) {
            correctionCard.setVisible(false);
            correctionCard.setManaged(false);
        }

        // 8. Categorized Sources Rendering (Newspaper, Book, Journal, Government)
        sourcesBox.getChildren().clear();
        if (r.getSources() != null && !r.getSources().isEmpty()) {
            for (FactCheckResult.Source s : r.getSources()) {
                HBox card = buildSourceCard(s);
                sourcesBox.getChildren().add(card);
            }
        } else {
            Label noSrc = new Label("Standard academic and press consensus references.");
            noSrc.getStyleClass().add("text-muted");
            sourcesBox.getChildren().add(noSrc);
        }

        // 6. 10-Point Summary Generation
        if (r.getSummary() != null && !r.getSummary().isEmpty()) {
            summaryLabel.setText(String.join("\n", r.getSummary()));
            summaryStatusLabel.setText("Complete");
            summaryBox.setVisible(true);
            summaryBox.setManaged(true);
        } else {
            generateSummaryTask(r);
        }

        // 7. Topic 3: Save to SQLite searches table (Background Thread)
        if (!SessionManager.isGuest()) {
            saveSearchToDatabase(r, inputType, originalInput);
        }

        showResult();
    }

    /**
     * Builds a visual source card with category badge, source metadata, and clickable native browser redirect.
     */
    private HBox buildSourceCard(FactCheckResult.Source s) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("source-card");

        // Category Badge
        Label typeBadge = new Label();
        String stype = s.type != null ? s.type.toLowerCase() : "website";
        switch (stype) {
            case "newspaper" -> {
                typeBadge.setText("📰 NEWSPAPER");
                typeBadge.getStyleClass().add("badge-source-newspaper");
            }
            case "book" -> {
                typeBadge.setText("📚 BOOK / REFERENCE");
                typeBadge.getStyleClass().add("badge-source-book");
            }
            case "journal" -> {
                typeBadge.setText("🔬 ACADEMIC JOURNAL");
                typeBadge.getStyleClass().add("badge-source-journal");
            }
            case "government" -> {
                typeBadge.setText("🏛️ OFFICIAL / GOVT");
                typeBadge.getStyleClass().add("badge-source-government");
            }
            default -> {
                typeBadge.setText("🌐 WEB SOURCE");
                typeBadge.getStyleClass().add("badge-source-newspaper");
            }
        }

        // Title and URL info container
        VBox infoBox = new VBox(3);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        Label titleLabel = new Label(s.title != null ? s.title : "Document Reference");
        titleLabel.setStyle("-fx-text-fill: #F0F6FC; -fx-font-weight: 700; -fx-font-size: 13.5px;");
        titleLabel.setWrapText(true);

        Label urlLabel = new Label(s.url != null ? s.url : "");
        urlLabel.setStyle("-fx-text-fill: #14B8A6; -fx-font-size: 11px; -fx-font-family: monospace;");
        urlLabel.setWrapText(true);

        infoBox.getChildren().addAll(titleLabel, urlLabel);

        // Buttons: Copy Link & Open in Browser
        HBox actionsBox = new HBox(8);
        actionsBox.setAlignment(Pos.CENTER_RIGHT);

        Button copyBtn = new Button("📋 Copy");
        copyBtn.getStyleClass().add("btn-ghost");
        copyBtn.setStyle("-fx-font-size: 11px; -fx-padding: 5 10; -fx-cursor: hand;");
        copyBtn.setOnAction(ev -> {
            ev.consume();
            copyLinkToClipboard(s.url);
        });

        Button openBtn = new Button("Open in Browser ↗");
        openBtn.getStyleClass().add("btn-open-source");
        openBtn.setOnAction(ev -> {
            ev.consume();
            openUrlInBrowser(s.url);
        });
        openBtn.setOnMouseClicked(javafx.event.Event::consume);
        copyBtn.setOnMouseClicked(javafx.event.Event::consume);

        // Click anywhere on card or link opens the source
        card.setOnMouseClicked(ev -> openUrlInBrowser(s.url));
        card.setStyle(card.getStyle() + "; -fx-cursor: hand;");

        card.getChildren().addAll(typeBadge, infoBox, actionsBox);
        return card;
    }

    private void openUrlInBrowser(String url) {
        if (url == null || url.trim().isEmpty() || "#".equals(url.trim())) return;
        boolean launched = Main.openBrowser(url);
        if (!launched) {
            alert("Open Source Link",
                  "Source link copied to your macOS clipboard:\n\n" + url + 
                  "\n\nPress Cmd+V in Safari or Chrome to view the original publication.");
        }
    }

    private void copyLinkToClipboard(String url) {
        if (url == null || url.trim().isEmpty() || "#".equals(url.trim())) return;
        try {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(url);
            clipboard.setContent(content);
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Link Copied");
            a.setHeaderText(null);
            a.setContentText("Source link copied to macOS clipboard:\n" + url);
            a.showAndWait();
        } catch (Exception ignored) {}
    }

    private void generateSummaryTask(FactCheckResult r) {
        summaryLabel.setText("Synthesizing 10-point executive brief...");
        summaryStatusLabel.setText("In Progress");
        summaryBox.setVisible(true);
        summaryBox.setManaged(true);

        Task<String> sumTask = new Task<>() {
            @Override protected String call() throws Exception {
                String model = modelSelector.getSelectionModel().getSelectedItem();
                return FactCheckerService.generateSummary(model, r.getClaim(), r.getVerdict(), r.getExplanation(), r.getSources());
            }
        };
        sumTask.setOnSucceeded(e -> Platform.runLater(() -> {
            summaryLabel.setText(sumTask.getValue());
            summaryStatusLabel.setText("Complete");
        }));
        sumTask.setOnFailed(e -> Platform.runLater(() -> {
            summaryLabel.setText("1. Claim: " + r.getClaim() + "\n2. Verdict: " + r.getVerdict() + "\n3. " + r.getExplanation());
            summaryStatusLabel.setText("Standard Brief");
        }));
        FactCheckerService.getExecutor().submit(sumTask);
    }

    /**
     * Topic 3: Asynchronously persists fact-check results into SQLite searches table.
     */
    private void saveSearchToDatabase(FactCheckResult r, String type, String orig) {
        int uid = SessionManager.getCurrentUser().getId();
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arr = mapper.createArrayNode();
        if (r.getSources() != null) {
            for (FactCheckResult.Source s : r.getSources()) {
                ObjectNode node = mapper.createObjectNode();
                node.put("title", s.title);
                node.put("url", s.url);
                node.put("type", s.type);
                arr.add(node);
            }
        }
        String jsonTemp;
        try { jsonTemp = mapper.writeValueAsString(arr); }
        catch (Exception ex) { jsonTemp = "[]"; }
        final String sourcesJson = jsonTemp;
        final String aiModel = r.getAiModel() != null ? r.getAiModel() : "Sondhan AI";

        Task<Void> dbTask = new Task<>() {
            @Override protected Void call() throws Exception {
                DatabaseService.getInstance().saveSearch(
                    uid, type, orig, r.getClaim(), r.getVerdict(),
                    r.getConfidence(), r.getExplanation(), sourcesJson,
                    r.isPreloaded(), aiModel, r.getSourceUrl(), r.getCorrection()
                );
                return null;
            }
        };
        FactCheckerService.getExecutor().submit(dbTask);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Action Buttons & Navigation
    // ═════════════════════════════════════════════════════════════════════════

    @FXML private void handleCopyReport() {
        if (currentResult == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("=== SONDHAN FACT-CHECK REPORT ===\n\n");
        sb.append("CLAIM: ").append(currentResult.getClaim()).append("\n");
        sb.append("VERDICT: ").append(currentResult.getVerdict()).append("\n");
        sb.append("CONFIDENCE: ").append(currentResult.getConfidence()).append("%\n");
        sb.append("ENGINE: ").append(currentResult.getAiModel()).append("\n\n");
        if (currentResult.getSourceUrl() != null && !currentResult.getSourceUrl().isBlank()) {
            sb.append("SOURCE URL: ").append(currentResult.getSourceUrl()).append("\n\n");
        }
        if (currentResult.getCorrection() != null && !currentResult.getCorrection().isBlank()) {
            sb.append("CORRECTION (WHAT IS TRUE):\n").append(currentResult.getCorrection()).append("\n\n");
        }
        sb.append("EXPLANATION:\n").append(currentResult.getExplanation()).append("\n\n");
        sb.append("VERIFIED SOURCES:\n");
        if (currentResult.getSources() != null) {
            for (FactCheckResult.Source s : currentResult.getSources()) {
                sb.append("• [").append(s.type).append("] ").append(s.title).append(" -> ").append(s.url).append("\n");
            }
        }
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(sb.toString());
        clipboard.setContent(content);

        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("Report Copied");
        info.setHeaderText(null);
        info.setContentText("Complete fact-check report copied to your macOS clipboard!");
        info.showAndWait();
    }

    @FXML private void handleExportReportFile() {
        if (currentResult == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Fact-Check Report");
        fc.setInitialFileName("FactCheck_" + System.currentTimeMillis() + ".md");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Markdown Document (*.md)", "*.md"),
            new FileChooser.ExtensionFilter("Text File (*.txt)", "*.txt")
        );
        File target = fc.showSaveDialog(Main.getPrimaryStage());
        if (target != null) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("# SONDHAN FACT-CHECK REPORT\n\n");
                sb.append("- **Claim:** ").append(currentResult.getClaim()).append("\n");
                sb.append("- **Verdict:** ").append(currentResult.getVerdict()).append("\n");
                sb.append("- **Confidence:** ").append(currentResult.getConfidence()).append("%\n");
                sb.append("- **Verification Engine:** ").append(currentResult.getAiModel()).append("\n\n");
                if (currentResult.getSourceUrl() != null && !currentResult.getSourceUrl().isBlank()) {
                    sb.append("- **Source URL:** ").append(currentResult.getSourceUrl()).append("\n\n");
                }
                if (currentResult.getCorrection() != null && !currentResult.getCorrection().isBlank()) {
                    sb.append("## 💡 What's Actually True\n").append(currentResult.getCorrection()).append("\n\n");
                }
                sb.append("## 📝 Factual Rationale\n").append(currentResult.getExplanation()).append("\n\n");
                sb.append("## 📚 Verified Sources & Citations\n");
                if (currentResult.getSources() != null) {
                    for (FactCheckResult.Source s : currentResult.getSources()) {
                        sb.append("- [").append(s.type.toUpperCase()).append("] ").append(s.title).append(" (").append(s.url).append(")\n");
                    }
                }
                if (currentResult.getSummary() != null && !currentResult.getSummary().isEmpty()) {
                    sb.append("\n## 📋 10-Point Executive Summary\n");
                    for (String bullet : currentResult.getSummary()) {
                        sb.append(bullet).append("\n");
                    }
                }
                java.nio.file.Files.writeString(target.toPath(), sb.toString());

                Alert info = new Alert(Alert.AlertType.INFORMATION);
                info.setTitle("Report Saved");
                info.setHeaderText(null);
                info.setContentText("Fact-check report exported to:\n" + target.getAbsolutePath());
                info.showAndWait();
            } catch (Exception ex) {
                alert("Export Failed", "Error saving report file: " + ex.getMessage());
            }
        }
    }

    private boolean isLightAccent = false;

    @FXML private void handleToggleTheme() {
        isLightAccent = !isLightAccent;
        javafx.scene.Scene scene = Main.getPrimaryStage().getScene();
        if (scene != null && scene.getRoot() != null) {
            if (isLightAccent) {
                if (!scene.getRoot().getStyleClass().contains("light-mode")) {
                    scene.getRoot().getStyleClass().add("light-mode");
                }
            } else {
                scene.getRoot().getStyleClass().remove("light-mode");
            }
        }
    }

    @FXML private void handleResetView() {
        hideResult();
    }

    @FXML private void handleHistory() { go("history.fxml"); }
    @FXML private void handleLogout()  { SessionManager.setCurrentUser(null); go("login.fxml"); }

    private void setLoading(boolean on) {
        if (checkBtn != null) checkBtn.setDisable(on);
        if (checkBtnImage != null) checkBtnImage.setDisable(on);
        if (checkBtnUrl != null) checkBtnUrl.setDisable(on);
        loadingBox.setVisible(on);
        loadingBox.setManaged(on);
    }

    private void showResult() {
        emptyState.setVisible(false); emptyState.setManaged(false);
        resultPanel.setVisible(true); resultPanel.setManaged(true);
    }

    private void hideResult() {
        resultPanel.setVisible(false); resultPanel.setManaged(false);
        emptyState.setVisible(true);   emptyState.setManaged(true);
    }

    private void alert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private void go(String fxml) {
        try {
            Main.navigateTo(fxml);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}