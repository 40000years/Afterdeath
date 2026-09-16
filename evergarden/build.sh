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
CLASSPATH="$(cat "$WORKSPACE_ROOT/output/item-fixes-20260915/dependency-classpath.txt"):$WORKSPACE_ROOT/advance-magic/target/classes"
SOURCES="$(find "$MODULE_ROOT/src/main/java" -name "*.java")"
# Paper 26.2 is compiled for Java 25 (class-file version 69). Prefer the
# Homebrew JDK used by this workspace; the macOS java shim may select JDK 23.
JAVAC_BIN="/opt/homebrew/opt/openjdk/bin/javac"
if [ ! -x "$JAVAC_BIN" ]; then JAVAC_BIN="$(command -v javac)"; fi
"$JAVAC_BIN" --release 25 -proc:none -encoding UTF-8 -cp "$CLASSPATH" -d "$CLASSES_DIR" $SOURCES

echo "==> Copying Resources..."
if [ -d "$MODULE_ROOT/src/main/resources" ]; then
    cp -r "$MODULE_ROOT/src/main/resources/"* "$CLASSES_DIR/"
fi

echo "==> Embedding Resource Packs inside JAR..."
PACK_DIR="$CLASSES_DIR/resource-packs"
GEYSER_DIR="$CLASSES_DIR/geyser"
mkdir -p "$PACK_DIR" "$GEYSER_DIR"

for asset in evergarden-java.zip evergarden-bedrock.mcpack geyser-mappings.json pack-hashes.json; do
    if [ -f "$DIST_DIR/$asset" ]; then
        cp -f "$DIST_DIR/$asset" "$PACK_DIR/"
    fi
done

if [ -f "$DIST_DIR/evergarden-bedrock.mcpack" ]; then
    cp -f "$DIST_DIR/evergarden-bedrock.mcpack" "$GEYSER_DIR/"
fi
if [ -f "$DIST_DIR/geyser-mappings.json" ]; then
    cp -f "$DIST_DIR/geyser-mappings.json" "$GEYSER_DIR/"
fi

echo "==> Packaging JAR..."
BUILT_JAR="$OUTPUT_DIR/evergarden.jar"
jar --create --file "$BUILT_JAR" -C "$CLASSES_DIR" .

cp -f "$BUILT_JAR" "$DIST_DIR/evergarden-3.0.0.jar"
cp -f "$BUILT_JAR" "$WORKSPACE_ROOT/evergarden.jar"

echo "==> Successfully Built Evergarden 3.0 (with 30 custom crops, packs, and mappings)!"
echo "    Output: $DIST_DIR/evergarden-3.0.0.jar"
