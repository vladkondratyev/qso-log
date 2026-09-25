#!/bin/bash
# Builds the Ubuntu/Debian package of QSO-LOG on macOS:
#   dist/QSO-LOG-<version>-linux-amd64.deb  →  /opt/qso-log, menu entry "QSO-LOG", bundled Java
#
# jpackage makes .deb files only on Linux, so the package is assembled in a Docker container (linux/amd64):
#   1. the app jar with Linux graphics libraries (Gradle on the Mac, -Ptarget=linux);
#   2. jpackage --type deb from Temurin 17 inside eclipse-temurin:17-jdk (Ubuntu), which also trims the Java runtime.
#
# Needs: Docker Desktop running; on Apple Silicon it runs the amd64 image through emulation (a few minutes).
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
VERSION=$(sed -n 's/^val appVersion = "\(.*\)"/\1/p' "$ROOT/desktop/build.gradle.kts")
MAC_JDK="${MAC_JDK:-$HOME/.gradle/jdks/jdk-17.0.20.1+1/Contents/Home}"
OUT="$ROOT/desktop/build/linux"

docker info > /dev/null 2>&1 || { echo "Docker не запущен" >&2; exit 1; }
rm -rf "$OUT"
mkdir -p "$OUT/input" "$OUT/deb" "$ROOT/dist"

echo "1/3 Сборка jar для Linux"
(cd "$ROOT" && JAVA_HOME="$MAC_JDK" ./gradlew -q :desktop:packageReleaseUberJarForCurrentOS -Ptarget=linux)
# The Compose task names the jar after the host OS; its contents are for Linux.
cp "$ROOT/desktop/build/compose/jars/QSO-LOG-macos-arm64-$VERSION-release.jar" "$OUT/input/qsolog.jar"
# Signature files of a signed dependency become invalid once jars are merged, and Java refuses to start the jar.
zip -q -d "$OUT/input/qsolog.jar" 'META-INF/*.SF' 'META-INF/*.RSA' 'META-INF/*.DSA' 'META-INF/*.EC' || true
unzip -l "$OUT/input/qsolog.jar" | grep "libskiko-linux-x64.so" > /dev/null || { echo "В jar нет графической библиотеки Linux" >&2; exit 1; }
cp "$ROOT/desktop/icons/icon.png" "$OUT/icon.png"

echo "2/3 Пакет .deb (jpackage в Docker)"
# Under x86 emulation GNU tar cannot stat files (statx), so dpkg-deb, the last step of jpackage, fails there.
# jpackage still leaves the finished package tree in --temp; dpkg-deb then runs in a native container,
# it does not care which architecture the package is for.
docker run --rm --platform linux/amd64 -v "$OUT:/work" -w /work eclipse-temurin:17-jdk bash -c "
    set -e
    # jpackage requires fakeroot; the container runs as root anyway.
    printf '#!/bin/sh\nexec \"\$@\"\n' > /usr/local/bin/fakeroot && chmod +x /usr/local/bin/fakeroot
    apt-get update -qq && apt-get install -y -qq binutils > /dev/null
    jpackage --type deb --temp /work/jp --dest /work/deb --input input --main-jar qsolog.jar --main-class ru.r3xed.qsolog.MainKt \\
        --name QSO-LOG --linux-package-name qso-log --app-version $VERSION \\
        --description 'Аппаратный журнал QSO с поиском по QRZ.ru' --vendor R3XED \\
        --icon icon.png --linux-shortcut --linux-menu-group 'HamRadio;Network' --linux-app-category hamradio \\
        --linux-deb-maintainer '13299424+vladkondratyev@users.noreply.github.com' \\
        --add-modules java.base,java.desktop,java.xml,java.logging,java.prefs,java.datatransfer,java.instrument,java.sql,jdk.security.auth,jdk.unsupported,java.naming,java.management,jdk.crypto.ec,jdk.localedata,jdk.charsets,jdk.accessibility,jdk.zipfs \\
        --jlink-options '--strip-debug --no-header-files --no-man-pages --compress=2' \\
        --java-options '-Dfile.encoding=UTF-8 -Xmx768m' || true  # expected to fail at dpkg-deb, see above
    test -f /work/jp/images/DEBIAN/control
"
# jpackage finds library dependencies with ldd, which does not work under emulation either; list them here.
# libasound2t64 is the Ubuntu 24.04 name, libasound2 the older one.
DEPENDS="libc6, xdg-utils, libasound2t64 | libasound2, libx11-6, libxext6, libxrender1, libxtst6, libxi6, libfreetype6, libfontconfig1, libgl1"
docker run --rm -v "$OUT:/work" -w /work debian:stable-slim bash -c "
    set -e
    sed -i 's/^Depends: .*/Depends: $DEPENDS/' jp/images/DEBIAN/control
    sed -i 's/^Categories=.*/Categories=HamRadio;Network;/' jp/images/opt/qso-log/lib/qso-log-QSO-LOG.desktop
    # xdg-desktop-menu fails on systems without menu folders (minimal installs); a failing postinst/prerm would leave
    # the package half-installed and impossible to remove, so fall back to the plain applications folder.
    D=/opt/qso-log/lib/qso-log-QSO-LOG.desktop
    sed -i \"s|^xdg-desktop-menu install \$D\$|xdg-desktop-menu install \$D 2>/dev/null \|\| install -m 644 \$D /usr/share/applications/|\" jp/images/DEBIAN/postinst
    sed -i \"s|^xdg-desktop-menu uninstall \$D\$|xdg-desktop-menu uninstall \$D 2>/dev/null \|\| true; rm -f /usr/share/applications/qso-log-QSO-LOG.desktop|\" jp/images/DEBIAN/prerm
    grep -q 'install -m 644' jp/images/DEBIAN/postinst && grep -q 'rm -f /usr/share' jp/images/DEBIAN/prerm
    dpkg-deb --root-owner-group -Zxz -b jp/images deb/qso-log.deb
"

echo "3/3 Готово"
DEB="$ROOT/dist/QSO-LOG-$VERSION-linux-amd64.deb"
cp "$OUT/deb/qso-log.deb" "$DEB"
ls -la "$DEB"
