# 🎓 Sondhan v2.0 – Academic Defense & Presentation Guide
> **Preparation Guide for Course Project Viva / Lab Evaluation**  
> Use this guide to answer professor questions and demonstrate each requirement for **Full Marks (100%)**.

---

## 📌 Quick Summary of Core Course Topics

| Course Topic | File Reference | Key Technical Justification |
| :--- | :--- | :--- |
| **Topic 1: GUI** | `home.fxml`, `history.fxml`, `styles.css` | • JavaFX MVC architecture separating presentation (FXML) from controller logic.<br>• Mixed Dark/Light theme with CSS variables, high contrast, and responsive layout.<br>• Dynamic visual components (colored verdict badges, confidence meter, categorized source cards). |
| **Topic 2: Multithreading** | `FactCheckerService.java`, `HomeController.java` | • Thread pool: `Executors.newFixedThreadPool(4)` with daemon threads so JVM exits cleanly.<br>• `javafx.concurrent.Task<T>` manages background work.<br>• `Platform.runLater()` dispatches UI updates onto the JavaFX Application Thread, preventing `IllegalStateException: Not on FX application thread`. |
| **Topic 3: SQLite Database** | `DatabaseService.java`, `HistoryController.java` | • Relational database (`sondhan.db`) with `users` and `searches` tables linked by Foreign Key.<br>• `PreparedStatement` with parameterized placeholders (`?`) eliminates SQL Injection.<br>• SHA-256 cryptographic password hashing.<br>• Write-Ahead Logging (WAL) enabled for safe concurrent reads. |
| **Topic 4: JSON Processing** | `FactCheckerService.java`, `DatabaseService.java` | • `org.json` library (`JSONObject`, `JSONArray`).<br>• Dynamic JSON schema payload construction for AI prompts.<br>• Regex-based stripping of markdown fences (````json ... ````) before deserialization.<br>• Storing cited sources as JSON arrays in SQLite and parsing them back into interactive links. |

---

## ❓ Common Examiner Questions & Perfect Answers

### Q1: "Why and where did you use Multithreading in this project?"
> **Answer:**  
> "In JavaFX, any long-running task (such as computing SHA-256 image hashes, calling external HTTP APIs, or executing SQLite database operations) will freeze the GUI if run on the main JavaFX Application Thread.  
> To guarantee a smooth 60fps UI, we implemented **`ExecutorService` with 4 daemon worker threads** in `FactCheckerService.java`. In `HomeController.java`, we encapsulate the verification logic inside a `javafx.concurrent.Task<FactCheckResult>`. The task reports live status updates via `updateMessage()` and progress via `updateProgress()`, and UI components are updated safely using `Platform.runLater()` inside `setOnSucceeded()`."

---

### Q2: "What happens if you update JavaFX UI elements directly from a background thread?"
> **Answer:**  
> "JavaFX enforces a single-threaded UI model. If a background worker thread attempts to modify a UI node directly (e.g. `label.setText()`), JavaFX throws an `IllegalStateException: Not on FX application thread`.  
> We avoid this by routing all UI updates through `Platform.runLater(Runnable)`, which schedules the update on the JavaFX event queue."

---

### Q3: "How does your database handle security and SQL Injection?"
> **Answer:**  
> "In `DatabaseService.java`, we never concatenate user input into raw SQL queries. Instead, we exclusively use `PreparedStatement` with parameter binding (`ps.setString(1, ...)`). This ensures the SQLite driver escapes all special characters and treats input strictly as data, completely preventing SQL Injection attacks. Additionally, user passwords are never stored in plaintext; they are hashed using **SHA-256** with salt-standard digests."

---

### Q4: "How does the Image Verification system work without an API key?"
> **Answer:**  
> "We implemented a dual-engine architecture in `ImageHashUtil.java` and `PreloadedDatabase.java`:
> 1. When an image is uploaded, we calculate its cryptographic **SHA-256** hash by streaming its bytes through an 8KB buffer into `MessageDigest`.
> 2. We perform an $O(1)$ hash map lookup against our preloaded catalog of verified viral images.
> 3. If a match is found, verified findings and citations are retrieved immediately.
> 4. If the user later adds a paid Claude or OpenAI key in Settings, the image is Base64 encoded and sent directly to Claude Vision or GPT-4o Vision."

---

### Q5: "How are sources categorized and parsed?"
> **Answer:**  
> "Every fact-check result categorizes sources into three distinct types:
> - 📰 **Newspaper Articles** (e.g. *The Daily Star*, *Reuters*, *BBC*, *BDNews24*)
> - 📚 **Books & Encyclopedias** (e.g. *Encyclopedia Britannica*, *Cambridge History*)
> - 🔬 **Academic Journals & Government** (e.g. *WHO*, *NASA*, *Nature*)
>
> In `FactCheckerService.java`, the AI returns a JSON array of source objects. In `DatabaseService.java`, this array is stored as a JSON string in SQLite. When viewing History, `HistoryController.java` deserializes the JSON string back into interactive `HBox` cards with clickable links opening in the default macOS browser."

---

## 🎬 3-Minute Live Demonstration Script for Viva

1. **Step 1 – Launch Application:**  
   Open terminal and type `./run.sh`. Mention that the app runs on Java 17+ and JavaFX 21 on macOS.
2. **Step 2 – Login:**  
   Point out the macOS window styling. Click **⚡ 1-Click Demo Login** to demonstrate instant SQLite authentication.
3. **Step 3 – Image Verification:**  
   Click the **🏢 Dhaka Capital** or **📡 5G Health** preset chip.  
   Show the professor the **Image Inspector**: file name, dimensions, size, and SHA-256 hash.  
   Click **🔎 Verify Claim with AI**. Show the progress bar updating asynchronously.
4. **Step 4 – Result & Categorized Sources:**  
   Highlight the color-coded verdict (`✓ VERIFIED TRUE` or `✕ FALSE CLAIM`), confidence %, and the **Newspaper & Book badges**. Click a link to prove it opens in the browser.
5. **Step 5 – Text Claim Verification:**  
   Switch to **💬 Text Statement** tab. Click **Climate change is a hoax** preset. Verify it.
6. **Step 6 – Export & SQLite History:**  
   Click **💾 Save Report as File...** to save a markdown report.  
   Then click **📜 History** in the header. Show the live statistics counters, search filter, and click **📤 Export...** to show JSON/CSV export.
7. **Step 7 – Theme & Settings:**  
   Click **🌓 Theme** in the header to show the light accent theme.  
   Click **⚙ Settings** to show how Claude and ChatGPT API keys can be entered when a paid key is available.
