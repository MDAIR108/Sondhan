package com.sondhan;

import com.sondhan.service.DatabaseService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Entry point. Initialises SQLite, loads the Login screen,
 * and wires the shared CSS theme.
 */
public class Main extends Application {

    private static Main instance;
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        instance = this;
        primaryStage = stage;
        try {
            DatabaseService.getInstance().initialize();
        } catch (Exception e) {
            System.err.println("[DB Error] " + e.getMessage());
        }
        // Runtime key loading (env vars) before any UI is shown.
        com.sondhan.service.SessionManager.initFromEnv();
        navigateTo("login.fxml", 980, 700);
        stage.setTitle("Sondhan — Fact Verification Platform");
        stage.setMinWidth(900);
        stage.setMinHeight(650);
        stage.show();
    }

    public static Main getInstance() { return instance; }

    /**
     * Resilient multi-tiered browser launcher:
     * 1. Copies URL to macOS clipboard immediately as a guaranteed fallback.
     * 2. Calls JavaFX native HostServices.showDocument().
     * 3. Calls Java Desktop.browse().
     * 4. Calls macOS ProcessBuilder("open", url).
     * 5. Attempts direct execution of installed browsers (Chrome, Safari, Firefox, etc.).
     */
    public static boolean openBrowser(String url) {
        if (url == null || url.trim().isEmpty() || "#".equals(url.trim())) return false;
        String clean = url.trim();
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://" + clean;
        }
        final String targetUrl = clean;

        // Guaranteed safety net: Copy URL to macOS system clipboard
        try {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(targetUrl);
            clipboard.setContent(content);
        } catch (Throwable t) {
            System.err.println("[Clipboard] Failed to copy URL: " + t.getMessage());
        }

        // Tier 1: macOS native `open` command (most reliable on macOS when unsandboxed)
        try {
            Process p = new ProcessBuilder("open", targetUrl).start();
            boolean finished = p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            if (finished && p.exitValue() == 0) {
                System.out.println("[Browser] Launched via 'open': " + targetUrl);
                return true;
            }
            System.err.println("[Browser] 'open' returned exit code: " + (finished ? p.exitValue() : "timeout"));
        } catch (Throwable t) {
            System.err.println("[Browser] 'open' failed: " + t.getMessage());
        }

        // Tier 2: AppleScript open location
        try {
            Process p = new ProcessBuilder("osascript", "-e", "open location \"" + targetUrl + "\"").start();
            boolean finished = p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            if (finished && p.exitValue() == 0) {
                System.out.println("[Browser] Launched via osascript: " + targetUrl);
                return true;
            }
        } catch (Throwable ignored) {}

        // Tier 3: Java AWT Desktop browse
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(targetUrl));
                System.out.println("[Browser] Launched via AWT Desktop: " + targetUrl);
                return true;
            }
        } catch (Throwable t) {
            System.err.println("[Browser] Desktop.browse failed: " + t.getMessage());
        }

        // Tier 4: JavaFX HostServices
        if (instance != null) {
            try {
                instance.getHostServices().showDocument(targetUrl);
                System.out.println("[Browser] Attempted HostServices: " + targetUrl);
            } catch (Throwable t) {
                System.err.println("[Browser] HostServices failed: " + t.getMessage());
            }
        }

        // Tier 5: Direct browser binaries (Chrome, Safari, Firefox, Brave)
        String[] binaries = {
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "/Applications/Safari.app/Contents/MacOS/Safari",
            "/Applications/Brave Browser.app/Contents/MacOS/Brave Browser",
            "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
            "/Applications/Firefox.app/Contents/MacOS/firefox",
            "/Applications/Arc.app/Contents/MacOS/Arc"
        };
        for (String bin : binaries) {
            if (new java.io.File(bin).exists()) {
                try {
                    new ProcessBuilder(bin, targetUrl).start();
                    System.out.println("[Browser] Launched directly via " + bin);
                    return true;
                } catch (Throwable ignored) {}
            }
        }

        return false;
    }

    /** Load any FXML into the primary stage, preserving current size. */
    public static void navigateTo(String fxml) throws Exception {
        double w = primaryStage.getScene() != null ? primaryStage.getScene().getWidth()  : 980;
        double h = primaryStage.getScene() != null ? primaryStage.getScene().getHeight() : 700;
        navigateTo(fxml, w, h);
    }

    public static void navigateTo(String fxml, double w, double h) throws Exception {
        java.net.URL url = Main.class.getResource("/com/sondhan/" + fxml);
        if (url == null) throw new RuntimeException("FXML not found: /com/sondhan/" + fxml);
        FXMLLoader loader = new FXMLLoader(url);
        Parent root = loader.load();
        Scene scene = new Scene(root, w, h);
        java.net.URL css = Main.class.getResource("/com/sondhan/styles.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        primaryStage.setScene(scene);
    }

    public static Stage getPrimaryStage() { return primaryStage; }

    @Override
    public void stop() {
        DatabaseService.getInstance().close();
    }

    public static void main(String[] args) { launch(args); }
}