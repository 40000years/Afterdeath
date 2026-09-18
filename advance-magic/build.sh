#!/usr/bin/env bash
set -euo pipefail

MODULE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$MODULE_ROOT/.." && pwd)"
OUTPUT_DIR="$MODULE_ROOT/target/build-$(date +%s)"
CLASSES_DIR="$OUTPUT_DIR/classes"
DIST_DIR="$MODULE_ROOT/dist"

mkdir -p "$DIST_DIR" "$CLASSES_DIR"

echo "==> Building Resource Packs (Java & Bedrock + Geyser mappings)..."
python3 "$MODULE_ROOT/tools/build_packs.py"

echo "==> Compiling Java Sources..."
CLASSPATH="$(cat "$WORKSPACE_ROOT/output/item-fixes-20260915/dependency-classpath.txt")"
SOURCES="$(find "$MODULE_ROOT/src/main/java" -name "*.java")"
JAVAC_BIN="/opt/homebrew/opt/openjdk/bin/javac"
if [ ! -x "$JAVAC_BIN" ]; then JAVAC_BIN="$(command -v javac)"; fi
"$JAVAC_BIN" --release 25 -proc:none -encoding UTF-8 -cp "$CLASSPATH" -d "$CLASSES_DIR" $SOURCES

echo "==> Copying Resources..."
if [ -d "$MODULE_ROOT/src/main/resources" ]; then
    cp -r "$MODULE_ROOT/src/main/resources/"* "$CLASSES_DIR/"
fi

echo "==> Embedding Resource Packs inside JAR..."
PACK_DIR="$CLASSES_DIR/resource-packs"
mkdir -p "$PACK_DIR"

for asset in advance-magic-java.zip advance-magic-bedrock.mcpack geyser-mappings.json pack-hashes.json wand-preview.html advance-magic-guide-th.png; do
    if [ -f "$DIST_DIR/$asset" ]; then
        cp -f "$DIST_DIR/$asset" "$PACK_DIR/"
    fi
done

echo "==> Packaging JAR..."
BUILT_JAR="$OUTPUT_DIR/advance-magic.jar"
jar --create --file "$BUILT_JAR" -C "$CLASSES_DIR" .

cp -f "$BUILT_JAR" "$DIST_DIR/advance-magic-1.0.0.jar"
cp -f "$BUILT_JAR" "$WORKSPACE_ROOT/advance-magic.jar"
mkdir -p "$MODULE_ROOT/target/classes"
cp -r "$CLASSES_DIR/"* "$MODULE_ROOT/target/classes/"

echo "==> Successfully Built advance-magic!"
echo "    Output: $DIST_DIR/advance-magic-1.0.0.jar"
