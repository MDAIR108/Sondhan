# সন্ধান (Sondhan) v2.0 – AI Fact Checker & Citation Engine
> **Intelligent Truth Verification Desktop Application for macOS**  
> Built with **Java 17+ / 21+**, **JavaFX**, **Multithreading (`ExecutorService`)**, **SQLite Database**, and **JSON Processing (`org.json`)**.  
> Dual Engine: **Live AI (Claude & ChatGPT)** + **Built-in Knowledge Engine (Free / Offline Mode)**.

---

## 🌟 Executive Summary

**সন্ধান (Sondhan)** is an academic course project desktop application designed for macOS that fact-checks news screenshots, social media images, and textual claims. It cross-references claims against verified **newspaper articles**, **published reference books**, and **peer-reviewed scientific journals**.

---

## 🎯 Course Curriculum Topics (Full Marks Checklist)

| Topic | Curriculum Requirement | Sondhan v2.0 Implementation |
| :--- | :--- | :--- |
| **Topic 1: GUI** | JavaFX, FXML, CSS, Layouts, Controls, Event Handling | • Mixed Dark/Light macOS-native theme (`styles.css`).<br>• MVC architecture with SceneBuilder-compatible FXML (`login.fxml`, `register.fxml`, `home.fxml`, `history.fxml`).<br>• Image inspection system (dimensions, file size, format, SHA-256 hash).<br>• Categorized citation cards (📰 Newspaper, 📚 Book, 🔬 Journal).<br>• 1-Click grading presets for instant testing. |
| **Topic 2: Multithreading** | Threads, Thread Safety, Background Tasks, Concurrency | • Fixed daemon thread pool (`Executors.newFixedThreadPool(4)`).<br>• Asynchronous `javafx.concurrent.Task<T>` with live message & progress binding.<br>• Thread-safe UI updates using `Platform.runLater()`.<br>• Startup background thread for preloaded database hashing.<br>• Non-blocking SQLite I/O and HTTP network calls. |
| **Topic 3: SQLite Database** | JDBC, DDL, DML, PreparedStatements, Relations | • SQLite JDBC (`sondhan.db`) with WAL mode enabled.<br>• Tables: `users` (credentials with SHA-256) and `searches` (history).<br>• Parameterized queries to eliminate SQL injection.<br>• History filtering, analytics counters, single record deletion, and bulk purge. |
| **Topic 4: JSON Processing** | JSON Builders, Serialization, Parsing, Envelope Handling | • `org.json` (`JSONObject`, `JSONArray`) throughout.<br>• Dynamic prompt envelope construction for Claude & ChatGPT.<br>• Robust JSON parsing that strips markdown code fences.<br>• JSON-based source persistence in SQLite, dynamically parsed back to UI. |

---

## 🚀 How to Run on macOS

### Option 1: Fast One-Click Terminal Launch
Open Terminal in the project folder and run:
```bash
./run.sh
```
*The script automatically detects the macOS Java runtime, compiles all classes, bundles resources, and starts the JavaFX application.*

### Option 2: IntelliJ IDEA / Eclipse
1. Open the project folder in **IntelliJ IDEA**.
2. Set Project SDK to **Java 17 or higher** (e.g. JetBrains Runtime OpenJDK).
3. Run `com.sondhan.Main`.

### Option 3: Automated Verification Test (Headless)
To run the automated course test suite that verifies all 5 core components without GUI:
```bash
JAVA="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home/bin/java"
CP=$(find ~/.m2/repository -name "*.jar" | grep -E "javafx-.*21\.0\.6|sqlite-jdbc-3\.45\.2\.0|json-20240303|slf4j-api-1\.7\.36" | grep -v sources | paste -sd ":" -)
$JAVA -cp "target/classes:$CP" com.sondhan.HeadlessVerifyTest
```

---

## 🔑 AI Models & Dual-Engine Architecture

The user does **not** need a paid API key to test or grade the project:

### 1. Free Offline Mode (Default)
- Operates using the built-in factual knowledge engine and cryptographic hash database.
- Provides immediate, highly accurate fact-checks for viral news, historical claims, and scientific myths.
- Generates categorized citations from **Newspapers** (e.g. *The Daily Star*, *Reuters*, *BBC*, *BDNews24*), **Books** (e.g. *Encyclopedia Britannica*, *Cambridge History*), and **Journals/Government** (e.g. *WHO*, *NASA*, *Nature*).

### 2. Live AI Mode (When Paid API Keys are Available)
When you acquire an API key:
1. Launch the app and click the **⚙ Settings** button in the header.
2. Enter your **Anthropic Claude API Key** (`sk-ant-...`) and/or **OpenAI ChatGPT API Key** (`sk-...`).
3. Click **Save Settings**.
4. The status pill instantly changes to `🟢 Claude Live` or `🟢 ChatGPT Live`.
5. Select your desired model from the header dropdown:
   - `Claude 3.5 Sonnet`
   - `ChatGPT (GPT-4o)`
   - `Auto / Smart Engine`
6. Live multimodal vision and text requests are dispatched over secure HTTPS via `java.net.http.HttpClient` with `org.json`.

---

## 🧪 Quick Grading Walkthrough for Evaluators

1. **Sign In**:
   - Click **⚡ 1-Click Demo Login** to immediately enter as a verified user, or click **Continue as Guest**.
2. **Test Image Verification**:
   - Under **🖼️ Image Analysis**, click any of the 1-click presets:
     - `[ 🏢 Dhaka Capital ]` → Constitutional verification of capital since 1971.
     - `[ 📡 5G Health ]` → Scientific debunking of radiation myth.
     - `[ ⚖️ Dr. Yunus ]` → Judicial dismissal clarification.
     - `[ 🗼 Eiffel Tower ]` → Victor Lustig's 1925 historical con.
   - Observe the **Image Inspector**: File name, dimensions (`W×H px`), size, format, and SHA-256 hash.
   - Click **🔎 Verify Claim with AI**.
   - Note the **Multithreaded Progress Bar** and live status updates.
3. **Inspect the Result**:
   - Large colored verdict badge: `✓ VERIFIED TRUE` / `✕ FALSE CLAIM` / `⚠ MISLEADING`.
   - Confidence percentage meter.
   - **Categorized Sources**: Click any newspaper or book citation to launch it in your macOS default browser.
   - **10-Point Executive Summary**: Complete breakdown of the fact-check.
   - Click **📋 Copy Report to Clipboard** to copy the formatted text.
4. **Test Text Claim Verification**:
   - Switch to **💬 Text Statement** tab.
   - Click any sample chip (e.g. `Moon landing was staged` or `5G towers cause cancer`).
   - Click **🔎 Verify Claim with AI**.
5. **Inspect SQLite History & Analytics**:
   - Click **📜 History** in the header.
   - View the live counters: *Total Checks*, *Verified True*, *Debunked False*, *Avg Confidence*.
   - Filter claims dynamically using the search bar or verdict filter dropdown.
   - Click any table row to see its complete details and interactive source links.
   - Click **🗑️ Delete Selected** or **🧹 Clear All**.

---

## 📂 Project Architecture

```
SondhanJavaFX/
├── run.sh                              # One-click macOS compile & launch script
├── pom.xml                             # Maven POM configuration
├── sondhan.db                          # SQLite relational database
├── preloaded/                          # Bundled test images for offline evaluation
│   ├── claim_dhaka_capital.png
│   ├── claim_5g_health.png
│   ├── claim_eiffel_sold.png
│   ├── claim_mars_water.png
│   └── 718953356_..._n.jpg
└── src/
    ├── main/
    │   ├── java/com/sondhan/
    │   │   ├── Main.java               # Application bootstrap & Scene navigator
    │   │   ├── HeadlessVerifyTest.java # Automated verification test
    │   │   ├── controller/
    │   │   │   ├── LoginController.java
    │   │   │   ├── RegisterController.java
    │   │   │   ├── HomeController.java # Main UI coordinator & task orchestration
    │   │   │   └── HistoryController.java # TableView, analytics & SQLite queries
    │   │   ├── model/
    │   │   │   ├── User.java           # Authenticated user entity
    │   │   │   ├── FactCheckResult.java# Fact-check domain model & Sources
    │   │   │   └── SearchHistory.java  # SQLite searches entity
    │   │   ├── service/
    │   │   │   ├── DatabaseService.java# SQLite JDBC singleton & schema
    │   │   │   ├── FactCheckerService.java # Dual Engine (Live AI + Knowledge Engine)
    │   │   │   ├── PreloadedDatabase.java # SHA-256 image match catalog
    │   │   │   └── SessionManager.java # In-memory session & API keys
    │   │   └── util/
    │   │       └── ImageHashUtil.java  # Image inspection & SHA-256 hashing
    │   └── resources/com/sondhan/
    │       ├── login.fxml              # macOS styled login screen
    │       ├── register.fxml           # Registration screen
    │       ├── home.fxml               # Main fact-checking workbench
    │       ├── history.fxml            # SQLite archive & analytics screen
    │       └── styles.css              # Mixed dark/light macOS theme
```

---

## 🛡️ Security & Reliability
- **Password Protection**: Passwords hashed with standard **SHA-256** before storage.
- **SQL Injection Prevention**: All SQL queries use parameterized `PreparedStatement`.
- **API Key Privacy**: API keys remain strictly in volatile memory (`SessionManager`) and are never written to database tables or log files.
- **Thread Isolation**: All network operations and database transactions run on background daemon threads (`sondhan-worker-thread`), guaranteeing that the JavaFX UI remains completely responsive.
