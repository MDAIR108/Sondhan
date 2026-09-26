#!/bin/bash
# ════════════════════════════════════════════════════════════════════════
#  Sondhan – Fact Verification Platform
#  Launch Script  ·  Java 17  ·  JavaFX 21  ·  Jackson 2.17
# ════════════════════════════════════════════════════════════════════════

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

# Detect Java (IntelliJ bundled JBR first)
if [ -d "/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home" ]; then
    export JAVA_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"
fi

JAVAC="${JAVA_HOME:-/usr}/bin/javac"
JAVA="${JAVA_HOME:-/usr}/bin/java"

# Jackson JARs (downloaded to /tmp on first run)
JACKSON_CORE="/tmp/jackson-core.jar"
JACKSON_DATABIND="/tmp/jackson-databind.jar"
JACKSON_ANN="/tmp/jackson-annotations.jar"

# Download Jackson if missing
if [ ! -f "$JACKSON_DATABIND" ]; then
    echo "[Setup] Downloading Jackson JSON library..."
    curl -sL "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.17.1/jackson-databind-2.17.1.jar" -o "$JACKSON_DATABIND"
    curl -sL "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.17.1/jackson-core-2.17.1.jar" -o "$JACKSON_CORE"
    curl -sL "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.17.1/jackson-annotations-2.17.1.jar" -o "$JACKSON_ANN"
fi

JACKSON_CP="$JACKSON_DATABIND:$JACKSON_CORE:$JACKSON_ANN"

# Build classpath
CP=$(find ~/.m2/repository -name "*.jar" 2>/dev/null | grep -E "javafx-.*21\.0\.6|sqlite-jdbc-3\.45\.2\.0|slf4j-api-1\.7\.36|jsoup-1\.17\.2" | grep -v sources | paste -sd ":" -)
CP="$CP:$JACKSON_CP"

JFX_MODULES=$(find ~/.m2/repository -name "javafx-*.jar" 2>/dev/null | grep "21\.0\.6" | grep -v sources | paste -sd ":" -)

echo "========================================================"
echo "  Sondhan — Fact Verification Platform"
echo "  Jackson JSON · SQLite · JavaFX · Multithreading"
echo "========================================================"

echo "[1/3] Compiling..."
mkdir -p target/classes
"$JAVAC" -cp "$CP" -d target/classes $(find src/main/java -name "*.java")
if [ $? -ne 0 ]; then
    echo "[ERROR] Compilation failed."
    exit 1
fi

echo "[2/3] Bundling resources..."
mkdir -p target/classes/com/sondhan
cp -r src/main/resources/com/sondhan/* target/classes/com/sondhan/

echo "[3/3] Launching..."
"$JAVA" --enable-native-access=ALL-UNNAMED \
    --module-path "$JFX_MODULES" \
    --add-modules javafx.controls,javafx.fxml \
    -cp "target/classes:$CP" \
    com.sondhan.Launcher "$@"
