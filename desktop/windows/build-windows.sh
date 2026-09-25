#!/bin/bash
# Builds the Windows x64 version of QSO-LOG on macOS:
#   dist/QSO-LOG-<version>-windows-x64.zip  →  QSO-LOG\QSO-LOG.exe, app\qsolog.jar, runtime\ (bundled Java)
#
# jpackage cannot build Windows installers on a Mac, so the bundle is assembled by hand:
#   1. the app jar with Windows graphics libraries (Gradle, -Ptarget=windows);
#   2. a trimmed Java runtime made by jlink from the Windows JDK's jmods (host and target JDK versions must match);
#   3. QSO-LOG.exe from Launch4j, which starts the jar with that runtime.
#
# Needs (downloaded once, see README in this folder):
#   MAC_JDK   Temurin 17 for macOS            (default ~/.gradle/jdks/jdk-17.0.20.1+1/Contents/Home)
#   WIN_JDK   Temurin 17 for Windows x64, same version (default ~/.gradle/jdks/win/jdk-17.0.20.1+1)
#   LAUNCH4J  Launch4j 3.50 for macOS          (default ~/.gradle/jdks/launch4j)
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
VERSION=$(sed -n 's/^val appVersion = "\(.*\)"/\1/p' "$ROOT/desktop/build.gradle.kts")
MAC_JDK="${MAC_JDK:-$HOME/.gradle/jdks/jdk-17.0.20.1+1/Contents/Home}"
WIN_JDK="${WIN_JDK:-$HOME/.gradle/jdks/win/jdk-17.0.20.1+1}"
LAUNCH4J="${LAUNCH4J:-$HOME/.gradle/jdks/launch4j}"

for d in "$MAC_JDK/bin/jlink" "$WIN_JDK/jmods" "$LAUNCH4J/launch4j.jar"; do
    [ -e "$d" ] || { echo "Не найдено: $d" >&2; exit 1; }
done

OUT="$ROOT/desktop/build/windows/QSO-LOG"
rm -rf "$ROOT/desktop/build/windows"
mkdir -p "$OUT/app" "$ROOT/dist"

echo "1/4 Сборка jar для Windows"
(cd "$ROOT" && JAVA_HOME="$MAC_JDK" ./gradlew -q :desktop:packageReleaseUberJarForCurrentOS -Ptarget=windows)
# The Compose task names the jar after the host OS; its contents are for Windows.
cp "$ROOT/desktop/build/compose/jars/QSO-LOG-macos-arm64-$VERSION-release.jar" "$OUT/app/qsolog.jar"
# Signature files of a signed dependency become invalid once jars are merged, and Java refuses to start the jar.
zip -q -d "$OUT/app/qsolog.jar" 'META-INF/*.SF' 'META-INF/*.RSA' 'META-INF/*.DSA' 'META-INF/*.EC' || true
# grep without -q: with pipefail, -q exits early and the SIGPIPE to unzip would fail the check.
unzip -l "$OUT/app/qsolog.jar" | grep "skiko-windows-x64.dll" > /dev/null || { echo "В jar нет графической библиотеки Windows" >&2; exit 1; }

echo "2/4 Java для Windows (jlink)"
"$MAC_JDK/bin/jlink" --module-path "$WIN_JDK/jmods" \
    --add-modules java.base,java.desktop,java.xml,java.logging,java.prefs,java.datatransfer,java.instrument,java.sql,jdk.security.auth,jdk.unsupported,java.naming,java.management,jdk.crypto.ec,jdk.crypto.mscapi,jdk.localedata,jdk.charsets,jdk.accessibility,jdk.zipfs \
    --strip-debug --no-header-files --no-man-pages --compress=2 \
    --output "$OUT/runtime"

echo "3/4 QSO-LOG.exe (Launch4j)"
# The config's relative paths work from desktop/build as well as from here; the copy carries the version.
sed "s/@VERSION@/$VERSION/g" "$HERE/launch4j.xml" > "$ROOT/desktop/build/launch4j.xml"
"$MAC_JDK/bin/java" -jar "$LAUNCH4J/launch4j.jar" "$ROOT/desktop/build/launch4j.xml" | grep -v "^launch4j: \(Compiling\|Linking\)" || true
[ -f "$OUT/QSO-LOG.exe" ] || { echo "Launch4j не создал exe" >&2; exit 1; }
# Latin file name (Cyrillic names in a zip made on a Mac can look garbled in Explorer); BOM + CRLF for Notepad.
{ printf '\xef\xbb\xbf'; sed 's/$/\r/' "$HERE/README.txt"; } > "$OUT/README.txt"

echo "4/4 Архив"
ZIP="$ROOT/dist/QSO-LOG-$VERSION-windows-x64.zip"
rm -f "$ZIP"
(cd "$ROOT/desktop/build/windows" && zip -q -r -X "$ZIP" "QSO-LOG")
ls -la "$ZIP"
