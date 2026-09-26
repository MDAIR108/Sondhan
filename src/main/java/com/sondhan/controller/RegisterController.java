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
 * RegisterController – User Account Creation
 * ──────────────────────────────────────────────────────────────────────────────
 * Course Project Demonstrations:
 *   • Topic 1: Form validation and error state management.
 *   • Topic 2: Multithreading: Registration task runs asynchronously on background thread.
 *   • Topic 3: SQLite INSERT into `users` table with SHA-256 hashed password.
 * ──────────────────────────────────────────────────────────────────────────────
 */
public class RegisterController {

    @FXML private TextField       nameField, emailField;
    @FXML private PasswordField   passwordField, confirmPasswordField;
    @FXML private Button          registerBtn;
    @FXML private Label           errorLabel;
    @FXML private ProgressIndicator loadingSpinner;

    @FXML public void initialize() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        loadingSpinner.setVisible(false);
        loadingSpinner.setManaged(false);
    }

    @FXML private void handleRegister() {
        String name  = nameField.getText().trim();
        String email = emailField.getText().trim();
        String pass  = passwordField.getText();
        String conf  = confirmPasswordField.getText();

        if (name.isEmpty() || email.isEmpty() || pass.isEmpty() || conf.isEmpty()) {
            showError("Please complete all required fields.");
            return;
        }

        if (!email.contains("@") || !email.contains(".")) {
            showError("Please enter a valid email address.");
            return;
        }

        if (!pass.equals(conf)) {
            showError("Passwords do not match. Please verify.");
            return;
        }

        if (pass.length() < 6) {
            showError("Password must be at least 6 characters in length.");
            return;
        }

        setLoading(true);

        // Topic 2: Asynchronous DB write on background thread
        Task<User> regTask = new Task<>() {
            @Override protected User call() throws Exception {
                // Topic 3: SQLite INSERT query with generated key retrieval
                return DatabaseService.getInstance().registerUser(name, email, pass);
            }
        };

        regTask.setOnSucceeded(e -> Platform.runLater(() -> {
            setLoading(false);
            User u = regTask.getValue();
            if (u != null) {
                SessionManager.setCurrentUser(u);
                go("home.fxml");
            } else {
                showError("Registration could not be completed. Email might already exist.");
            }
        }));

        regTask.setOnFailed(e -> Platform.runLater(() -> {
            setLoading(false);
            Throwable err = regTask.getException();
            String msg = err != null ? err.getMessage() : "Unknown";
            if (msg.contains("UNIQUE") || msg.contains("constraint")) {
                showError("This email is already registered. Please sign in instead.");
            } else {
                showError("Database Error: " + msg);
            }
        }));

        new Thread(regTask, "sondhan-reg-thread").start();
    }

    @FXML private void handleBackToLogin() {
        go("login.fxml");
    }

    private void setLoading(boolean on) {
        registerBtn.setDisable(on);
        loadingSpinner.setVisible(on);
        loadingSpinner.setManaged(on);
        if (on) {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        }
    }

    private void showError(String m) {
        errorLabel.setText(m);
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