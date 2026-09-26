package com.sondhan.controller;

import com.sondhan.Main;
import com.sondhan.model.User;
import com.sondhan.service.DatabaseService;
import com.sondhan.service.SessionManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;

/**
 * ──────────────────────────────────────────────────────────────────────────────
 * LoginController – User Authentication & Session Initialization
 * ──────────────────────────────────────────────────────────────────────────────
 * Course Project Demonstrations:
 *   • Topic 1: FXML event handling, Scene transition.
 *   • Topic 2: Java Multithreading: Authentication runs on a background Task
 *              so the JavaFX UI thread is never frozen.
 *   • Topic 3: SQLite SELECT query executed safely via PreparedStatement.
 * ──────────────────────────────────────────────────────────────────────────────
 */
public class LoginController {

    @FXML private TextField       emailField;
    @FXML private PasswordField   passwordField;
    @FXML private Button          loginBtn;
    @FXML private Label           errorLabel;
    @FXML private ProgressIndicator loadingSpinner;

    @FXML public void initialize() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        loadingSpinner.setVisible(false);
        loadingSpinner.setManaged(false);
    }

    @FXML private void handleLogin() {
        String email = emailField.getText().trim();
        String pass  = passwordField.getText();

        if (email.isEmpty() || pass.isEmpty()) {
            showError("Please enter both email and password.");
            return;
        }

        setLoading(true);

        // Topic 2: Asynchronous authentication on background thread
        Task<User> authTask = new Task<>() {
            @Override protected User call() throws Exception {
                // Topic 3: SQLite credential lookup via PreparedStatement
                return DatabaseService.getInstance().loginUser(email, pass);
            }
        };

        authTask.setOnSucceeded(e -> Platform.runLater(() -> {
            setLoading(false);
            User user = authTask.getValue();
            if (user != null) {
                SessionManager.setCurrentUser(user);
                go("home.fxml");
            } else {
                showError("Invalid email or password. Please check your credentials or use guest access.");
            }
        }));

        authTask.setOnFailed(e -> Platform.runLater(() -> {
            setLoading(false);
            Throwable err = authTask.getException();
            showError("Authentication Error: " + (err != null ? err.getMessage() : "Unknown"));
        }));

        new Thread(authTask, "sondhan-login-thread").start();
    }

    /**
     * 1-Click Demo Login for Course Examiners:
     * Registers or logs into a pre-configured demo student profile instantly.
     */
    @FXML private void handleDemoLogin() {
        setLoading(true);

        Task<User> demoTask = new Task<>() {
            @Override protected User call() throws Exception {
                // Try logging into existing demo account
                User user = DatabaseService.getInstance().loginUser("student@sondhan.edu", "demo1234");
                if (user == null) {
                    // Create demo account if it doesn't exist yet
                    try {
                        user = DatabaseService.getInstance().registerUser("Course Student", "student@sondhan.edu", "demo1234");
                    } catch (Exception ex) {
                        user = DatabaseService.getInstance().loginUser("student@sondhan.edu", "demo1234");
                    }
                }
                return user;
            }
        };

        demoTask.setOnSucceeded(e -> Platform.runLater(() -> {
            setLoading(false);
            User u = demoTask.getValue();
            if (u != null) {
                SessionManager.setCurrentUser(u);
                go("home.fxml");
            } else {
                SessionManager.setCurrentUser(new User(1, "Course Student", "student@sondhan.edu"));
                go("home.fxml");
            }
        }));

        demoTask.setOnFailed(e -> Platform.runLater(() -> {
            setLoading(false);
            SessionManager.setCurrentUser(new User(1, "Course Student", "student@sondhan.edu"));
            go("home.fxml");
        }));

        new Thread(demoTask, "sondhan-demo-thread").start();
    }

    @FXML private void handleRegister() {
        go("register.fxml");
    }

    @FXML private void handleGuestMode() {
        SessionManager.setCurrentUser(null); // null = Guest Mode
        go("home.fxml");
    }

    private void setLoading(boolean on) {
        loginBtn.setDisable(on);
        loadingSpinner.setVisible(on);
        loadingSpinner.setManaged(on);
        if (on) {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        }
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void go(String fxml) {
        try {
            Main.navigateTo(fxml);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}